/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.extension.file.internal.command;

import static java.lang.String.format;
import static java.nio.file.Files.isDirectory;
import static java.nio.file.Files.isReadable;
import static java.nio.file.StandardOpenOption.READ;
import static java.nio.file.StandardOpenOption.WRITE;
import static org.mule.runtime.core.api.util.IOUtils.closeQuietly;
import org.mule.extension.file.api.LocalFileAttributes;
import org.mule.extension.file.common.api.FileConnectorConfig;
import org.mule.extension.file.common.api.command.ReadCommand;
import org.mule.extension.file.common.api.exceptions.FileAccessDeniedException;
import org.mule.extension.file.common.api.lock.NullPathLock;
import org.mule.extension.file.common.api.lock.PathLock;
import org.mule.extension.file.internal.FileInputStream;
import org.mule.extension.file.internal.LocalFileSystem;
import org.mule.runtime.api.util.LazyValue;
import org.mule.runtime.extension.api.runtime.operation.Result;

import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.nio.file.AccessDeniedException;
import java.nio.file.Path;

/**
 * A {@link LocalFileCommand} which implements the {@link ReadCommand} contract
 *
 * @since 1.0
 */
public final class LocalReadCommand extends LocalFileCommand implements ReadCommand<LocalFileAttributes> {

  /**
   * {@inheritDoc}
   */
  public LocalReadCommand(LocalFileSystem fileSystem) {
    super(fileSystem);
  }

  /**
   * {@inheritDoc}
   */
  @Deprecated
  @Override
  public Result<InputStream, LocalFileAttributes> read(FileConnectorConfig config, String filePath, boolean lock) {
    return read(config, filePath, lock, null);
  }


  /**
   * {@inheritDoc}
   */
  @Override
  public Result<InputStream, LocalFileAttributes> read(FileConnectorConfig config, String filePath, boolean lock,
                                                       Long timeBetweenSizeCheck) {
    Path path = resolveExistingPath(filePath);

    LocalFileAttributes fileAttributes = new LocalFileAttributes(path);
    return read(config, fileAttributes, lock, timeBetweenSizeCheck);
  }

  /**
   * {@inheritDoc}
   */
  public Result<InputStream, LocalFileAttributes> read(FileConnectorConfig config, LocalFileAttributes attributes, boolean lock,
                                                       Long timeBetweenSizeCheck) {
    Path path = resolvePath(attributes.getPath());

    if (isDirectory(path)) {
      throw cannotReadDirectoryException(path);
    }

    if (!isReadable(path)) {
      throw new FileAccessDeniedException(format("Could not read the file '%s' because access was denied by the operating system",
                                                 path));
    }

    LazyValue<FileChannel> lazyChannel = null;
    PathLock pathLock = null;
    InputStream payload = null;

    try {
      if (lock) {
        lazyChannel = new LazyValue<>(FileChannel.open(path, READ, WRITE));
        pathLock = fileSystem.lock(path, lazyChannel.get());
      } else {
        lazyChannel = new LazyValue<>(() -> {
          try {
            return FileChannel.open(path, READ);
          } catch (IOException e) {
            throw exception(format("Unexpected error reading file '%s': %s", path, e.getMessage()), e);
          }
        });
        pathLock = new NullPathLock(path);
      }

      payload = new FileInputStream(lazyChannel, pathLock, path, timeBetweenSizeCheck, attributes);

      return Result.<InputStream, LocalFileAttributes>builder()
          .output(payload)
          .mediaType(fileSystem.getFileMessageMediaType(attributes))
          .attributes(attributes)
          .build();

    } catch (AccessDeniedException e) {
      onException(payload, lazyChannel, pathLock);
      throw new FileAccessDeniedException(format("Access to path '%s' denied by the operating system", path), e);
    } catch (Exception e) {
      onException(payload, lazyChannel, pathLock);
      throw exception(format("Unexpected error reading file '%s': %s", path, e.getMessage()), e);
    }
  }

  private void onException(InputStream payload, LazyValue<FileChannel> lazyChannel, PathLock lock) {
    closeQuietly(payload);
    if (lazyChannel != null) {
      lazyChannel.ifComputed(channel -> closeQuietly(channel));
    }
    if (lock != null) {
      lock.release();
    }
  }
}
