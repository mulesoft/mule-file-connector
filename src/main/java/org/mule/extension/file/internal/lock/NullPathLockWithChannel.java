/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.extension.file.internal.lock;

import static java.lang.String.format;

import org.mule.extension.file.common.api.lock.PathLock;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation of the {@link PathLock} that close its channel when releasing the lock;
 *
 * @since 1.1.4
 */
public class NullPathLockWithChannel implements PathLock {

  private static final Logger LOGGER = LoggerFactory.getLogger(NullPathLockWithChannel.class);

  private final Path path;
  private FileChannel fileChannel;

  public NullPathLockWithChannel(Path path, FileChannel fileChannel) {
    this.path = path;
    this.fileChannel = fileChannel;
  }

  /**
   * Does nothing and always returns {@code true}
   *
   * @return {@code true}
   */
  @Override
  public boolean tryLock() {
    return true;
  }

  /**
   * @return {@code false}
   */
  @Override
  public boolean isLocked() {
    return false;
  }

  /**
   * Does nothing regardless of how many invocations the {@link #tryLock()} method has received
   */
  @Override
  public void release() {
    try {
      fileChannel.close();
    } catch (IOException e) {
      if (LOGGER.isDebugEnabled()) {
        LOGGER.debug(format("Found exception attempting to close the channel for the lock on path '%s'", path), e);
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
