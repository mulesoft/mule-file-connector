/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.extension.file.internal;

import static java.lang.Thread.sleep;
import static org.mule.runtime.api.i18n.I18nMessageFactory.createStaticMessage;
import static org.mule.runtime.core.api.util.IOUtils.closeQuietly;
import static org.slf4j.LoggerFactory.getLogger;

import org.mule.extension.file.api.LocalFileAttributes;
import org.mule.extension.file.common.api.exceptions.DeletedFileWhileReadException;
import org.mule.extension.file.common.api.exceptions.FileBeingModifiedException;
import org.mule.extension.file.common.api.lock.PathLock;
import org.mule.extension.file.common.api.stream.AbstractFileInputStream;
import org.mule.extension.file.common.api.stream.LazyStreamSupplier;
import org.mule.runtime.api.exception.MuleRuntimeException;
import org.slf4j.Logger;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.function.Supplier;

/**
 * {@link AbstractFileInputStream} implementation used to obtain a file's content based on a {@link Reader}.
 * <p>
 * This stream will automatically close itself once fully consumed but will not fail if {@link #close()} is invoked after that.
 * <p>
 * This class also contains a {@link PathLock} which will be released when the stream is closed. However, this class will never
 * invoke the {@link PathLock#tryLock()} method on it, it's the responsibility of whomever is creating this instance to determine
 * if that lock is to be acquired.
 *
 * @since 1.0
 */
public final class FileInputStream extends AbstractFileInputStream {

  private final FileChannel channel;

  /**
   * Creates a new instance
   *
   * @param channel
   * @param lock a {@link PathLock}
   */
  public FileInputStream(FileChannel channel, PathLock lock, Path path, Long timeBetweenSizeCheck) {
    super(new LazyStreamSupplier(new FileStreamSupplier(timeBetweenSizeCheck, path, channel)), lock);
    this.channel = channel;
  }

  @Override
  protected void doClose() throws IOException {
    closeQuietly(channel);
  }

  protected static final class FileStreamSupplier implements Supplier<InputStream> {

    private static final Logger LOGGER = getLogger(FileStreamSupplier.class);
    private static final String FILE_NO_LONGER_EXISTS_MESSAGE =
        "Error reading file from path %s. It no longer exists at the time of reading.";
    private static final String STARTING_WAIT_MESSAGE = "Starting wait to check if the file size of the file %s is stable.";
    private static final int MAX_SIZE_CHECK_RETRIES = 2;

    private Path path;
    private Long timeBetweenSizeCheck;
    private FileChannel channel;

    FileStreamSupplier(Long timeBetweenSizeCheck, Path path, FileChannel channel) {
      this.timeBetweenSizeCheck = timeBetweenSizeCheck;
      this.path = path;
      this.channel = channel;
    }

    @Override
    public InputStream get() {
      // This call is done to check that the file still exists
      getUpdatedAttributes(path);

      if (timeBetweenSizeCheck != null && timeBetweenSizeCheck > 0) {
        failIfFileSizeIsUnstable(path);
      }
      return new BufferedInputStream(Channels.newInputStream(channel));
    }

    private LocalFileAttributes failIfFileSizeIsUnstable(Path path) {
      LocalFileAttributes oldAttributes;
      LocalFileAttributes updatedAttributes = getUpdatedAttributes(path);
      int retries = 0;
      do {
        oldAttributes = updatedAttributes;
        try {
          if (LOGGER.isDebugEnabled()) {
            LOGGER.debug(String.format(STARTING_WAIT_MESSAGE, path.toString()));
          }
          sleep(timeBetweenSizeCheck);
        } catch (InterruptedException e) {
          throw new MuleRuntimeException(createStaticMessage("Execution was interrupted while waiting to recheck file sizes"),
                                         e);
        }
        updatedAttributes = getUpdatedAttributes(path);
      } while (updatedAttributes != null && updatedAttributes.getSize() != oldAttributes.getSize()
          && retries++ < MAX_SIZE_CHECK_RETRIES);
      if (retries > MAX_SIZE_CHECK_RETRIES) {
        throw new FileBeingModifiedException(createStaticMessage("File on path " + path.toString() + " is still being written."));
      }
      return updatedAttributes;
    }

    private LocalFileAttributes getUpdatedAttributes(Path path) {
      LocalFileAttributes updatedFtpFileAttributes;
      try {
        updatedFtpFileAttributes = new LocalFileAttributes(path);
      } catch (MuleRuntimeException e) {
        if (e.getCause() instanceof NoSuchFileException) {
          LOGGER.error(String.format(FILE_NO_LONGER_EXISTS_MESSAGE, path.toString()));
          throw new DeletedFileWhileReadException(createStaticMessage("File on path " + path.toString()
              + " was read but does not exist anymore."), e);
        }
        throw e;
      }
      return updatedFtpFileAttributes;
    }

  }

}
