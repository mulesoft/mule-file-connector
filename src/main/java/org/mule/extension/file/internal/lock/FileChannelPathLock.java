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

import java.io.File;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.AccessDeniedException;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link PathLock} backed by a {@link FileLock} obtained through a {@link FileChannel}
 *
 * @since 1.0
 */
public final class FileChannelPathLock implements PathLock {

  private static final Logger LOGGER = LoggerFactory.getLogger(FileChannelPathLock.class);
  private static final long MAX_LOCK_RETRIES = 20L;

  private final Path path;
  private final FileChannel channel;
  private FileLock lock;

  /**
   * Creates a new instance
   *
   * @param path a {@link Path} pointing to the resource to be locked
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
      throw new FileAccessDeniedException(format("Could not obtain lock on path ''%s'' because access was denied by the operating system",
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
   * Due to the limitations of the {@link FileChannel} class, the only way to provided a waiting lock with a specific
   * timeout that works for 2 or more concurrent locking operations in the same JVM (i.e. in the same Mule App) is to
   * perform a busy wait loop for the timeout duration. To avoid numerous requests in the loop, on each iteration
   * the thread is called to sleep for 1/20th of the duration of the timeout.
   *
   * {@inheritDoc}
   */
  @Override
  public boolean tryLock(Long timeout) {
    long nanoTimeout = timeout < 0 ? 0 : TimeUnit.MILLISECONDS.toNanos(timeout);
    long startTime = System.nanoTime();
    do {
      try {
        lock = channel.tryLock();
        if (lock != null) {
          return isLocked();
        } else {
          sleepThread(timeout / MAX_LOCK_RETRIES);
          continue;
        }
      } catch (OverlappingFileLockException e) {
        sleepThread(timeout / MAX_LOCK_RETRIES);
        continue;
      } catch (AccessDeniedException e) {
        release();
        throw new FileAccessDeniedException(format("Could not obtain lock on path ''%s'' because access was denied by the operating system",
                                                   path),
                                            e);
      } catch (Exception e) {
        release();
        if (LOGGER.isInfoEnabled()) {
          LOGGER.info(format("Could not obtain lock on path ''%s'' due to the following exception", path), e);
        }
        return false;
      }
    } while (System.nanoTime() - startTime < nanoTimeout && lock == null);
    if (timeout != 0) {
      throw new FileLockedException(String.format("Could not lock file ''%s'' for the operation because it remained locked" +
          " by another process for the '%d' milliseconds timeout.", path, timeout));
    }
    throw new FileLockedException(String
        .format("Could not lock file ''%s'' for the operation because it was already locked by another process.", path));
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

  private void sleepThread(long duration) {
    if (duration <= 0) {
      return;
    }
    try {
      Thread.sleep(duration);
    } catch (InterruptedException e) {
      throw new FileLockedException(format("Thread was interrupted while attempting to obtain lock on path '%s'", path));
    }
  }
}
