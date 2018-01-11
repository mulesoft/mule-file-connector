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
import static org.mule.extension.file.api.WatermarkMode.CREATED_TIMESTAMP;
import static org.mule.extension.file.api.WatermarkMode.DISABLED;
import static org.mule.extension.file.api.WatermarkMode.MODIFIED_TIMESTAMP;
import static org.mule.extension.file.common.api.FileDisplayConstants.MATCHER;
import static org.mule.runtime.core.api.util.IOUtils.closeQuietly;
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
import org.mule.runtime.extension.api.runtime.source.PollContext;
import org.mule.runtime.extension.api.runtime.source.PollingSource;
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
import java.util.function.Predicate;

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
public class DirectoryListener extends PollingSource<InputStream, FileAttributes> {

  private static final Logger LOGGER = LoggerFactory.getLogger(DirectoryListener.class);
  private static final String ATTRIBUTES_CONTEXT_VAR = "attributes";
  private static final String POST_PROCESSING_GROUP_NAME = "Post processing action";

  @Config
  private FileConnector config;

  @Connection
  private ConnectionProvider<LocalFileSystem> fileSystemProvider;

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

  @Override
  protected void doStart() throws MuleException {
    fileSystem = fileSystemProvider.connect();

    matcher = predicateBuilder != null ? predicateBuilder.build() : new NullFilePayloadPredicate<>();
    directoryPath = resolveRootPath();
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
  public void onTerminate() {}

  @Override
  public void poll(PollContext<InputStream, FileAttributes> pollContext) {
    PollWalker walker = new PollWalker(pollContext);
    try {
      walkFileTree(directoryPath, EnumSet.of(FOLLOW_LINKS), MAX_VALUE, walker);
    } catch (Exception e) {
      LOGGER.error(format("Found exception trying to poll directory '%s'. Will try again on the next poll. ",
                          directoryPath.toString(), e.getMessage()), e);
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
      closeQuietly(payload);
      closeQuietly(channel);

      throw new MuleRuntimeException(e);
    }
  }

  @Override
  public void releaseRejectedResource(Result<InputStream, FileAttributes> result) {
    closeQuietly(result.getOutput());
  }

  @Override
  protected void doStop() {
    if (fileSystem != null) {
      fileSystemProvider.disconnect(fileSystem);
    }
  }

  private Path resolveRootPath() {
    return new OnNewFileCommand(fileSystem).resolveRootPath(directory);
  }

  // not to be confused with Paul Walker
  private class PollWalker extends SimpleFileVisitor<Path> {

    private final PollContext<InputStream, FileAttributes> pollContext;

    public PollWalker(PollContext<InputStream, FileAttributes> pollContext) {
      this.pollContext = pollContext;
    }

    @Override
    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
      return recursive ? CONTINUE : SKIP_SUBTREE;
    }

    @Override
    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
      if (pollContext.isSourceStopping()) {
        return TERMINATE;
      }

      LocalFileAttributes attributes = new LocalFileAttributes(file, attrs);

      if (!matcher.test(attributes)) {
        if (LOGGER.isDebugEnabled()) {
          LOGGER.debug("Skipping file '{}' because the matcher rejected it", attributes.getPath());
        }
        return CONTINUE;
      }

      pollContext.accept(item -> {
        item.setResult(createResult(file, attributes))
            .setId(file.toString());

        if (watermarkMode != DISABLED) {
          item.setWatermark(getWatermarkTimestamp(attributes));
        }

        item.getSourceCallbackContext().addVariable(ATTRIBUTES_CONTEXT_VAR, attributes);
      });

      return pollContext.isSourceStopping() ? TERMINATE : CONTINUE;
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
}
