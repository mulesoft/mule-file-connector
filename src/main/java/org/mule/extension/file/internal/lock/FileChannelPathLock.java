/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.extension.file.internal.lock;

import static java.lang.String.format;
import org.mule.extension.file.common.api.exceptions.FileAccessDeniedException;
import org.mule.extension.file.common.api.exceptions.FileLockedException;
import org.mule.extension.file.common.api.lock.PathLock;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.AccessDeniedException;
import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link PathLock} backed by a {@link FileLock} obtained through a {@link FileChannel}
 *
 * @since 1.0
 */
public final class FileChannelPathLock implements PathLock {

  private static final Logger LOGGER = LoggerFactory.getLogger(FileChannelPathLock.class);

  private final Path path;
  private final FileChannel channel;
  private FileLock lock;

  /**
   * Creates a new instance
   *
   * @param path    a {@link Path} pointing to the resource to be locked
   * @param channel a {@link FileChannel}
   */
  public FileChannelPathLock(Path path, FileChannel channel) {
    this.path = path.toAbsolutePath();
    this.channel = channel;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean tryLock() {
    if (isLocked()) {
      throw new FileLockedException("Lock is already acquired");
    }

    try {
      lock = channel.tryLock();
      return isLocked();
    } catch (AccessDeniedException e) {
      release();
      throw new FileAccessDeniedException(
                                          format("Could not obtain lock on path ''%s'' because access was denied by the operating system",
                                                 path),
                                          e);
    } catch (Exception e) {
      release();
      if (LOGGER.isInfoEnabled()) {
        LOGGER.info(format("Could not obtain lock on path ''%s'' due to the following exception", path), e);
      }

      return false;
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isLocked() {
    return lock != null && lock.isValid();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void release() {
    if (lock != null) {
      try {
        lock.release();
      } catch (IOException e) {
        if (LOGGER.isDebugEnabled()) {
          LOGGER.debug(format("Found exception attempting to release lock on path '%s'", path), e);
        }
      } finally {
        lock = null;
      }
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Path getPath() {
    return path;
  }
}
