/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.extension.file.internal.command;

import static java.lang.String.format;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.nio.file.StandardOpenOption.WRITE;
import static org.apache.commons.io.IOUtils.copy;

import org.mule.extension.file.common.api.FileWriteMode;
import org.mule.extension.file.common.api.command.WriteCommand;
import org.mule.extension.file.common.api.exceptions.FileAccessDeniedException;
import org.mule.extension.file.common.api.exceptions.IllegalPathException;
import org.mule.extension.file.common.api.lock.NullPathLock;
import org.mule.extension.file.common.api.lock.PathLock;
import org.mule.extension.file.internal.LocalFileSystem;
import org.mule.runtime.extension.api.exception.ModuleException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

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
  @Override
  public void write(String filePath, InputStream content, FileWriteMode mode,
                    boolean lock, boolean createParentDirectory, String encoding) {
    Path path = resolvePath(filePath);
    assureParentFolderExists(path, createParentDirectory);

    final OpenOption[] openOptions = getOpenOptions(mode);
    PathLock pathLock = lock ? fileSystem.lock(path, openOptions) : new NullPathLock();

    try (OutputStream out = getOutputStream(path, openOptions, mode)) {
      copy(content, out);
    } catch (AccessDeniedException e) {
      throw new FileAccessDeniedException(format("Could not write to file '%s' because access was denied by the operating system",
                                                 path),
                                          e);
    } catch (ModuleException e) {
      throw e;
    } catch (Exception e) {
      throw exception(format("Exception was found writing to file '%s'", path), e);
    } finally {
      pathLock.release();
    }
  }

  private OutputStream getOutputStream(Path path, OpenOption[] openOptions, FileWriteMode mode) throws IOException {
    try {
      return Files.newOutputStream(path, openOptions);
    } catch (FileAlreadyExistsException e) {
      throw new org.mule.extension.file.common.api.exceptions.FileAlreadyExistsException(format(
                                                                                                "Cannot write to path '%s' because it already exists and write mode '%s' was selected. "
                                                                                                    + "Use a different write mode or point to a path which doesn't exist",
                                                                                                path, mode),
                                                                                         e);
    } catch (FileSystemException e) {
      //The only way to be sure that the exception was raised due to an illegal path.
      if (IS_A_DIRECTORY_MESSAGE.equals(e.getReason())) {
        throw new IllegalPathException(format("Cannot write to path '%s' because it is a Directory.", path), e);
      }

      throw e;
    }
  }

  private OpenOption[] getOpenOptions(FileWriteMode mode) {

    switch (mode) {
      case APPEND:
        return new OpenOption[] {CREATE, WRITE, StandardOpenOption.APPEND};
      case CREATE_NEW:
        return new OpenOption[] {WRITE, StandardOpenOption.CREATE_NEW};
      case OVERWRITE:
        return new OpenOption[] {CREATE, WRITE, TRUNCATE_EXISTING};
    }

    throw new IllegalArgumentException("Unsupported write mode " + mode);
  }
}
