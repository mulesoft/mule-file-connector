/*
 * Copyright 2023 Salesforce, Inc. All rights reserved.
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.extension.file.internal.command;

import static java.lang.String.format;
import static java.nio.file.StandardOpenOption.APPEND;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.CREATE_NEW;
import static java.nio.file.StandardOpenOption.READ;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.nio.file.StandardOpenOption.WRITE;
import static org.apache.commons.io.IOUtils.closeQuietly;
import static org.apache.commons.io.IOUtils.copy;
import org.mule.extension.file.common.api.FileWriteMode;
import org.mule.extension.file.common.api.command.WriteCommand;
import org.mule.extension.file.common.api.exceptions.FileAccessDeniedException;
import org.mule.extension.file.common.api.exceptions.IllegalPathException;
import org.mule.extension.file.common.api.lock.NullPathLock;
import org.mule.extension.file.common.api.lock.PathLock;
import org.mule.extension.file.internal.LocalFileSystem;
import org.mule.runtime.extension.api.exception.ModuleException;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystemException;
import java.nio.file.InvalidPathException;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * A {@link LocalFileCommand} which implements the {@link WriteCommand} contract
 *
 * @since 1.0
 */
public final class LocalWriteCommand extends LocalFileCommand implements WriteCommand {

  /**
   * {@inheritDoc}
   */
  public LocalWriteCommand(LocalFileSystem fileSystem) {
    super(fileSystem);
  }

  /**
   * {@inheritDoc}
   */
  @Deprecated
  @Override
  public void write(String filePath, InputStream content, FileWriteMode mode,
                    boolean lock, boolean createParentDirectory, String encoding) {
    write(filePath, content, mode, lock, createParentDirectory);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void write(String filePath, InputStream content, FileWriteMode mode,
                    boolean lock, boolean createParentDirectory) {
    validateFileSystemPath(filePath);

    Path path = resolvePath(filePath);
    assureParentFolderExists(path, createParentDirectory);

    FileChannel channel = null;
    PathLock pathLock = null;
    try {
      channel = FileChannel.open(path, getOpenOptions(mode));

      pathLock = lock ? fileSystem.lock(path, channel) : new NullPathLock(path);

      try (OutputStream out = Channels.newOutputStream(channel)) {
        copy(content, out);
      }
    } catch (ModuleException e) {
      throw e;
    } catch (FileAlreadyExistsException e) {
      throw new org.mule.extension.file.common.api.exceptions.FileAlreadyExistsException(format(
                                                                                                "Cannot write to path '%s' because it already exists and write mode '%s' was selected. "
                                                                                                    + "Use a different write mode or point to a path which doesn't exist",
                                                                                                path, mode),
                                                                                         e);
    } catch (AccessDeniedException e) {
      throw new FileAccessDeniedException(format("Could not write to file '%s' because access was denied by the operating system",
                                                 path),
                                          e);
    } catch (FileSystemException e) {
      // The only way to be sure that the exception was raised due to an illegal path.
      if (IS_A_DIRECTORY_MESSAGE.equals(e.getReason())) {
        throw new IllegalPathException(format("Cannot write to path '%s' because it is a Directory.", path), e);
      }

      throw exception(format("Exception was found writing to file '%s'", path), e);
    } catch (Exception e) {
      throw exception(format("Exception was found writing to file '%s'", path), e);
    } finally {
      if (pathLock != null) {
        pathLock.release();
      }

      closeQuietly(channel);
    }
  }

  private void validateFileSystemPath(final String path) {
    try {
      Paths.get(path);
    } catch (InvalidPathException ex) {
      throw new IllegalPathException(format("%s Invalid path", path), ex);
    }
  }

  private OpenOption[] getOpenOptions(FileWriteMode mode) {
    switch (mode) {
      case APPEND:
        return new OpenOption[] {CREATE, WRITE, APPEND};
      case CREATE_NEW:
        return new OpenOption[] {READ, WRITE, CREATE_NEW};
      case OVERWRITE:
        return new OpenOption[] {READ, CREATE, WRITE, TRUNCATE_EXISTING};
    }

    throw new IllegalArgumentException("Unsupported write mode " + mode);
  }
}
