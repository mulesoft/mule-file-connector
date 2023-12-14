/*
 * Copyright 2023 Salesforce, Inc. All rights reserved.
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.extension.file.internal.lock;

import static java.lang.String.format;

import org.mule.extension.file.common.api.lock.PathLock;
import org.mule.runtime.api.util.LazyValue;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Composite {@link PathLock} implementation and {@link FileChannel} that closes the {@link FileChannel} when the
 * {@link PathLock} is released.
 *
 * @since 2.0.0
 */
public class PathLockChannelWrapper implements PathLock {

  private static final Logger LOGGER = LoggerFactory.getLogger(PathLockChannelWrapper.class);

  private PathLock pathLock;
  private LazyValue<FileChannel> lazyFileChannel;

  public PathLockChannelWrapper(PathLock pathLock, FileChannel fileChannel) {
    this.pathLock = pathLock;
    this.lazyFileChannel = new LazyValue<>(fileChannel);
  }

  public PathLockChannelWrapper(PathLock pathLock, LazyValue<FileChannel> fileChannel) {
    this.pathLock = pathLock;
    this.lazyFileChannel = fileChannel;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean tryLock() {
    return pathLock.tryLock();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isLocked() {
    return pathLock.isLocked();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void release() {
    pathLock.release();
    lazyFileChannel.ifComputed(fileChannel -> {
      try {
        fileChannel.close();
      } catch (IOException e) {
        if (LOGGER.isDebugEnabled()) {
          LOGGER.debug(format("Found exception attempting to close the channel for the lock on path '%s'", pathLock.getPath()),
                       e);
        }
      }
    });
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Path getPath() {
    return pathLock.getPath();
  }

}
