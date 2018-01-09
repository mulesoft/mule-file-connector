/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.extension.file.internal.source;

import static java.lang.Integer.MAX_VALUE;
import static java.lang.String.format;
import static java.nio.file.FileVisitOption.FOLLOW_LINKS;
import static java.nio.file.FileVisitResult.CONTINUE;
import static java.nio.file.FileVisitResult.SKIP_SUBTREE;
import static java.nio.file.FileVisitResult.TERMINATE;
import static java.nio.file.Files.walkFileTree;
import static java.util.Optional.empty;
import static java.util.Optional.of;
import static java.util.Optional.ofNullable;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.mule.extension.file.api.WatermarkMode.CREATED_TIMESTAMP;
import static org.mule.extension.file.api.WatermarkMode.DISABLED;
import static org.mule.extension.file.api.WatermarkMode.MODIFIED_TIMESTAMP;
import static org.mule.extension.file.common.api.FileDisplayConstants.MATCHER;
import static org.mule.runtime.api.i18n.I18nMessageFactory.createStaticMessage;
import static org.mule.runtime.api.store.ObjectStoreSettings.unmanagedPersistent;
import static org.mule.runtime.extension.api.annotation.param.MediaType.ANY;
import org.mule.extension.file.api.LocalFileAttributes;
import org.mule.extension.file.api.LocalFileMatcher;
import org.mule.extension.file.api.WatermarkMode;
import org.mule.extension.file.common.api.FileAttributes;
import org.mule.extension.file.common.api.lock.NullPathLock;
import org.mule.extension.file.common.api.matcher.NullFilePayloadPredicate;
import org.mule.extension.file.internal.FileConnector;
import org.mule.extension.file.internal.FileInputStream;
import org.mule.extension.file.internal.LocalFileSystem;
import org.mule.extension.file.internal.command.OnNewFileCommand;
import org.mule.runtime.api.component.location.ComponentLocation;
import org.mule.runtime.api.connection.ConnectionProvider;
import org.mule.runtime.api.exception.MuleException;
import org.mule.runtime.api.exception.MuleRuntimeException;
import org.mule.runtime.api.lock.LockFactory;
import org.mule.runtime.api.scheduler.Scheduler;
import org.mule.runtime.api.scheduler.SchedulerConfig;
import org.mule.runtime.api.scheduler.SchedulerService;
import org.mule.runtime.api.store.ObjectStore;
import org.mule.runtime.api.store.ObjectStoreException;
import org.mule.runtime.api.store.ObjectStoreManager;
import org.mule.runtime.api.store.ObjectStoreSettings;
import org.mule.runtime.core.api.util.IOUtils;
import org.mule.runtime.extension.api.annotation.Alias;
import org.mule.runtime.extension.api.annotation.execution.OnError;
import org.mule.runtime.extension.api.annotation.execution.OnSuccess;
import org.mule.runtime.extension.api.annotation.execution.OnTerminate;
import org.mule.runtime.extension.api.annotation.param.Config;
import org.mule.runtime.extension.api.annotation.param.Connection;
import org.mule.runtime.extension.api.annotation.param.MediaType;
import org.mule.runtime.extension.api.annotation.param.Optional;
import org.mule.runtime.extension.api.annotation.param.Parameter;
import org.mule.runtime.extension.api.annotation.param.ParameterGroup;
import org.mule.runtime.extension.api.annotation.param.display.DisplayName;
import org.mule.runtime.extension.api.annotation.param.display.Summary;
import org.mule.runtime.extension.api.runtime.operation.Result;
import org.mule.runtime.extension.api.runtime.source.Source;
import org.mule.runtime.extension.api.runtime.source.SourceCallback;
import org.mule.runtime.extension.api.runtime.source.SourceCallbackContext;

import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.nio.file.FileVisitResult;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.function.Predicate;

import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Polls a directory looking for files that have been created on it. One message will be generated for each file that is found.
 * <p>
 * The key part of this functionality is how to determine that a file is actually new. There're three strategies for that:
 * <ul>
 * <li>Set the <i>autoDelete</i> parameter to <i>true</i>: This will delete each processed file after it has been processed,
 * causing all files obtained in the next poll to be necessarily new</li>
 * <li>Set <i>moveToDirectory</i> parameter: This will move each processed file to a different directory after it has been
 * processed, achieving the same effect as <i>autoDelete</i> but without loosing the file</li>
 * <li></li>
 * <li>Use the <i>watermarkMode</i> parameter to only pick files that have been created/updated after the last poll was
 * executed.</li>
 * </ul>
 * <p>
 * A matcher can also be used for additional filtering of files.
 *
 * @since 1.1
 */
@MediaType(value = ANY, strict = false)
@DisplayName("On New File")
@Summary("Triggers when a new file is created in a directory")
@Alias("listener")
public class DirectoryListener extends Source<InputStream, FileAttributes> {

  private static final Logger LOGGER = LoggerFactory.getLogger(DirectoryListener.class);
  private static final String WATERMARK_OS_KEY = "watermark";
  private static final String ATTRIBUTES_CONTEXT_VAR = "attributes";
  private static final String FILE_RELEASER_VAR = "fileReleaser";
  private static final String POST_PROCESSING_GROUP_NAME = "Post processing action";

  @Config
  private FileConnector config;

  @Connection
  private ConnectionProvider<LocalFileSystem> fileSystemProvider;

  @Inject
  private SchedulerService schedulerService;

  @Inject
  private LockFactory lockFactory;

  @Inject
  private ObjectStoreManager objectStoreManager;

  /**
   * The directory on which polled files are contained
   */
  @Parameter
  @Optional
  private String directory;

  /**
   * Whether or not to also files contained in sub directories.
   */
  @Parameter
  @Optional(defaultValue = "true")
  @Summary("Whether or not to also catch files created on sub directories")
  private boolean recursive = true;

  /**
   * A matcher used to filter events on files which do not meet the matcher's criteria
   */
  @Parameter
  @Optional
  @Alias("matcher")
  @DisplayName(MATCHER)
  private LocalFileMatcher predicateBuilder;

  /**
   * How ofter to poll for files.
   */
  @Parameter
  private long pollingFrequency;

  /**
   * A time unit which qualifies the {@code pollingFrequency}
   */
  @Parameter
  @Optional(defaultValue = "SECONDS")
  private TimeUnit poolingFrequencyTimeUnit;

  /**
   * Controls whether or not to do watermarking, and if so, if the watermark should consider the file's modification or creation
   * timestamps
   */
  @Parameter
  @Optional(defaultValue = "DISABLED")
  private WatermarkMode watermarkMode = DISABLED;

  private Path directoryPath;
  private LocalFileSystem fileSystem;
  private ComponentLocation location;
  private Predicate<LocalFileAttributes> matcher;
  private Scheduler listenerExecutor;
  private ObjectStore<LocalDateTime> watermarkObjectStore;
  private ObjectStore<String> filesBeingProcessingObjectStore;

  private final AtomicBoolean stopRequested = new AtomicBoolean(false);

  @Override
  public void onStart(SourceCallback<InputStream, FileAttributes> sourceCallback) throws MuleException {
    fileSystem = fileSystemProvider.connect();

    matcher = predicateBuilder != null ? predicateBuilder.build() : new NullFilePayloadPredicate<>();

    listenerExecutor = schedulerService.customScheduler(SchedulerConfig.config()
        .withMaxConcurrentTasks(1)
        .withName(
                  format("%s.file.listener", location.getRootContainerName())));

    directoryPath = resolveRootPath();
    filesBeingProcessingObjectStore = objectStoreManager.getOrCreateObjectStore("file-listener:" + directory,
                                                                                ObjectStoreSettings.builder()
                                                                                    .persistent(false)
                                                                                    .maxEntries(1000)
                                                                                    .entryTtl(60000L)
                                                                                    .expirationInterval(20000L)
                                                                                    .build());

    if (watermarkMode != DISABLED) {
      watermarkObjectStore = objectStoreManager.getOrCreateObjectStore("file-listener-watermark:" + directory,
                                                                       unmanagedPersistent());
    }

    long freq = poolingFrequencyTimeUnit.toMillis(pollingFrequency);
    stopRequested.set(false);
    listenerExecutor.scheduleAtFixedRate(() -> poll(sourceCallback), freq, freq, MILLISECONDS);
  }

  @OnSuccess
  public void onSuccess(@ParameterGroup(name = POST_PROCESSING_GROUP_NAME) PostActionGroup postAction,
                        SourceCallbackContext ctx) {
    postAction(postAction, ctx);
  }

  @OnError
  public void onError(@ParameterGroup(name = POST_PROCESSING_GROUP_NAME) PostActionGroup postAction,
                      SourceCallbackContext ctx) {
    if (postAction.isApplyPostActionWhenFailed()) {
      postAction(postAction, ctx);
    }
  }

  @OnTerminate
  public void onTerminate(SourceCallbackContext ctx) {
    ctx.<Runnable>getVariable(FILE_RELEASER_VAR).ifPresent(Runnable::run);
  }

  private void poll(SourceCallback<InputStream, FileAttributes> sourceCallback) {
    if (isRequestedToStop()) {
      return;
    }

    try {
      java.util.Optional<LocalDateTime> watermark = getWatermark();
      PollWalker walker = new PollWalker(watermark, sourceCallback);
      walkFileTree(directoryPath, EnumSet.of(FOLLOW_LINKS), MAX_VALUE, walker);

      walker.getUpdatedWatermark().ifPresent(this::updateWatermark);

    } catch (Exception e) {
      LOGGER.error(format("Found exception trying to poll directory '%s'. Will try again on the next poll. ",
                          directoryPath.toString(), e.getMessage()),
                   e);
    }
  }

  private FileVisitResult processFile(Path path, LocalFileAttributes attributes, Lock lock,
                                      SourceCallback<InputStream, FileAttributes> callback) {
    String fullPath = path.toString();
    try {
      if (filesBeingProcessingObjectStore.contains(fullPath)) {
        if (LOGGER.isDebugEnabled()) {
          LOGGER.debug("Polled file '{}', but skipping it since it is already being processed in another thread or node",
                       fullPath);
        }
        return CONTINUE;
      } else {
        markAsProcessing(fullPath);
      }

      SourceCallbackContext ctx = callback.createContext();
      ctx.addVariable(ATTRIBUTES_CONTEXT_VAR, attributes);
      ctx.addVariable(FILE_RELEASER_VAR, (Runnable) () -> releaseFile(attributes, lock));
      Result<InputStream, FileAttributes> result = createResult(path, attributes);

      if (isRequestedToStop()) {
        releaseFile(attributes, lock);
        return TERMINATE;
      } else {
        callback.handle(result, ctx);
      }
    } catch (Throwable t) {
      LOGGER.error(format("Found file '%s' but found exception trying to dispatch it for processing. %s",
                          fullPath, t.getMessage()),
                   t);
      releaseFile(attributes, lock);
    }

    return CONTINUE;
  }

  private void releaseFile(LocalFileAttributes attributes, Lock lock) {
    try {
      unmarkAsProcessing(attributes.getPath());
    } finally {
      lock.unlock();
    }
  }

  private void markAsProcessing(String path) {
    try {
      filesBeingProcessingObjectStore.store(path, path);
    } catch (ObjectStoreException e) {
      throw new MuleRuntimeException(
                                     createStaticMessage(format("Could not track file '%s' as being processed. %s", path,
                                                                e.getMessage())),
                                     e);
    }
  }

  private void unmarkAsProcessing(String path) {
    try {
      if (filesBeingProcessingObjectStore.contains(path)) {
        filesBeingProcessingObjectStore.remove(path);
      }
    } catch (ObjectStoreException e) {
      LOGGER.error(format("Could not untrack file '%s' as being processed", path), e);
    }
  }

  private void postAction(PostActionGroup postAction, SourceCallbackContext ctx) {
    try {
      postAction.validateSelf();
    } catch (IllegalArgumentException e) {
      LOGGER.error(e.getMessage());
    }

    ctx.<LocalFileAttributes>getVariable(ATTRIBUTES_CONTEXT_VAR).ifPresent(attrs -> {
      if (postAction.isAutoDelete()) {
        fileSystem.delete(attrs.getPath());
      } else if (postAction.getMoveToDirectory() != null) {
        fileSystem.move(config, attrs.getPath(), postAction.getMoveToDirectory(), false, true,
                        postAction.getRenameTo());
      }
    });
  }

  private java.util.Optional<LocalDateTime> getWatermark() {
    if (watermarkObjectStore == null) {
      return empty();
    }

    try {
      if (watermarkObjectStore.contains(WATERMARK_OS_KEY)) {
        return of(watermarkObjectStore.retrieve(WATERMARK_OS_KEY));
      } else {
        return of(LocalDateTime.of(0, 12, 25, 0, 0, 0));
      }
    } catch (ObjectStoreException e) {
      throw new MuleRuntimeException(createStaticMessage("Failed to fetch watermark for directory " + directoryPath.toString()),
                                     e);
    }
  }

  private void updateWatermark(LocalDateTime value) {
    try {
      if (watermarkObjectStore.contains(WATERMARK_OS_KEY)) {
        watermarkObjectStore.remove(WATERMARK_OS_KEY);
      }

      watermarkObjectStore.store(WATERMARK_OS_KEY, value);
    } catch (ObjectStoreException e) {
      throw new MuleRuntimeException(createStaticMessage("Failed to update watermark value for directory "
          + directoryPath.toString()), e);
    }
  }

  private Lock getFileProcessingLock(String path) {
    return lockFactory.createLock("file:listener-" + path);
  }

  private boolean isRequestedToStop() {
    return stopRequested.get() || Thread.currentThread().isInterrupted();
  }

  private Result<InputStream, FileAttributes> createResult(Path path, FileAttributes attributes) {
    InputStream payload = null;
    FileChannel channel = null;

    try {
      channel = FileChannel.open(path);
      payload = new FileInputStream(channel, new NullPathLock(path));

      return Result.<InputStream, FileAttributes>builder()
          .output(payload)
          .mediaType(fileSystem.getFileMessageMediaType(attributes))
          .attributes(attributes).build();
    } catch (Exception e) {
      IOUtils.closeQuietly(payload);
      IOUtils.closeQuietly(channel);

      throw new MuleRuntimeException(e);
    }
  }

  @Override
  public void onStop() {
    stopRequested.set(true);
    shutdownScheduler();

    if (fileSystem != null) {
      fileSystemProvider.disconnect(fileSystem);
    }
  }

  private void shutdownScheduler() {
    if (listenerExecutor != null) {
      listenerExecutor.stop();
    }
  }

  private Path resolveRootPath() {
    return new OnNewFileCommand(fileSystem).resolveRootPath(directory);
  }

  // not to be confused with Paul Walker
  private class PollWalker extends SimpleFileVisitor<Path> {

    private final java.util.Optional<LocalDateTime> watermark;
    private final SourceCallback<InputStream, FileAttributes> sourceCallback;
    private LocalDateTime updatedWatermark = null;

    private PollWalker(java.util.Optional<LocalDateTime> watermark, SourceCallback<InputStream, FileAttributes> sourceCallback) {
      this.watermark = watermark;
      this.sourceCallback = sourceCallback;
    }

    @Override
    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
      return recursive ? CONTINUE : SKIP_SUBTREE;
    }

    @Override
    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
      if (isRequestedToStop()) {
        return TERMINATE;
      }

      LocalFileAttributes attributes = new LocalFileAttributes(file, attrs);
      Lock lock = getFileProcessingLock(attributes.getPath());
      if (!lock.tryLock()) {
        if (LOGGER.isDebugEnabled()) {
          LOGGER.debug("Skipping processing of file '{}' because another thread or node already has a mule lock on it",
                       attributes.getPath());
        }
        return CONTINUE;
      }

      if (!matcher.test(attributes)) {
        if (LOGGER.isDebugEnabled()) {
          LOGGER.debug("Skipping file '{}' because the matcher rejected it", attributes.getPath());
        }
        return CONTINUE;
      }

      if (watermark.isPresent()) {
        final LocalDateTime timestamp = getWatermarkTimestamp(attributes);
        if (watermark.get().compareTo(timestamp) >= 0) {
          if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Skipping file '{}' because it was rejected by the watermark", attributes.getPath());
          }
          return CONTINUE;
        } else {
          if (updatedWatermark == null) {
            updatedWatermark = timestamp;
          } else if (timestamp.compareTo(updatedWatermark) > 0) {
            updatedWatermark = timestamp;
          }
        }
      }

      return processFile(file, attributes, lock, sourceCallback);
    }

    public java.util.Optional<LocalDateTime> getUpdatedWatermark() {
      return ofNullable(updatedWatermark);
    }
  }

  private LocalDateTime getWatermarkTimestamp(LocalFileAttributes attributes) {
    if (watermarkMode == MODIFIED_TIMESTAMP) {
      return attributes.getLastModifiedTime();
    } else if (watermarkMode == CREATED_TIMESTAMP) {
      return attributes.getCreationTime();
    } else {
      throw new IllegalArgumentException("Watermark not supported for mode " + watermarkMode);
    }
  }
}
