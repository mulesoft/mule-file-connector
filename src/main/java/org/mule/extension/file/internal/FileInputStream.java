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
import org.mule.extension.file.common.api.AbstractFileInputStreamSupplier;
import org.mule.extension.file.common.api.FileAttributes;
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
  public FileInputStream(FileChannel channel, PathLock lock, Path path, Long timeBetweenSizeCheck, FileAttributes attributes) {
    super(new LazyStreamSupplier(new LocalFileInputStreamSupplier(timeBetweenSizeCheck, path, channel, attributes)), lock);
    this.channel = channel;
  }

  @Override
  protected void doClose() throws IOException {
    closeQuietly(channel);
  }

  protected static final class LocalFileInputStreamSupplier extends AbstractFileInputStreamSupplier {

    private static final Logger LOGGER = getLogger(LocalFileInputStreamSupplier.class);

    private final Path path;
    private final FileChannel channel;

    LocalFileInputStreamSupplier(Long timeBetweenSizeCheck, Path path, FileChannel channel, FileAttributes attributes) {
      super(attributes, timeBetweenSizeCheck);
      this.path = path;
      this.channel = channel;
    }

    @Override
    protected FileAttributes getUpdatedAttributes() {
      LocalFileAttributes updatedFtpFileAttributes;
      try {
        updatedFtpFileAttributes = new LocalFileAttributes(path);
      } catch (MuleRuntimeException e) {
        if (e.getCause() instanceof NoSuchFileException) {
          LOGGER.error(String.format(FILE_NO_LONGER_EXISTS_MESSAGE, path.toString()));
          onFileDeleted(e);
        }
        throw e;
      }
      return updatedFtpFileAttributes;
    }

    @Override
    protected InputStream getContentInputStream() {
      // Get updated attributes to check whether the file still exists
      getUpdatedAttributes();
      return new BufferedInputStream(Channels.newInputStream(channel));
    }
  }
}
