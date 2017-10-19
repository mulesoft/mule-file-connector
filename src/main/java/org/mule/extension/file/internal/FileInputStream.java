/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.extension.file.internal;

import static org.mule.runtime.core.api.util.IOUtils.closeQuietly;
import org.mule.extension.file.common.api.lock.PathLock;
import org.mule.extension.file.common.api.stream.AbstractFileInputStream;
import org.mule.extension.file.common.api.stream.LazyStreamSupplier;
import org.mule.runtime.api.exception.MuleRuntimeException;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.Reader;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;

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
  public FileInputStream(FileChannel channel, PathLock lock) {
    super(new LazyStreamSupplier(() -> {
      try {
        return new BufferedInputStream(Channels.newInputStream(channel));
      } catch (Exception e) {
        throw new MuleRuntimeException(e);
      }
    }), lock);

    this.channel = channel;
  }

  @Override
  protected void doClose() throws IOException {
    closeQuietly(channel);
  }
}
