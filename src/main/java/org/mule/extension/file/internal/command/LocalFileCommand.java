/*
 * Copyright 2023 Salesforce, Inc. All rights reserved.
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.extension.file.internal.command;

import static java.lang.String.format;
import org.mule.extension.file.common.api.FileSystem;
import org.mule.extension.file.common.api.command.FileCommand;
import org.mule.extension.file.internal.LocalFileSystem;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Base class for implementations of {@link FileCommand} which operate on a local file system
 *
 * @since 1.0
 */
abstract class LocalFileCommand extends FileCommand<LocalFileSystem> {

  /**
   * {@inheritDoc}
   */
  LocalFileCommand(LocalFileSystem fileSystem) {
    super(fileSystem);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  protected Path getBasePath(FileSystem fileSystem) {
    return Paths.get(fileSystem.getBasePath());
  }

  /**
   * {@inheritDoc}
   */
  @Override
  protected boolean exists(Path path) {
    return Files.exists(path);
  }

  /**
   * Transforms the {@code directoryPath} to a {@link} {@link File} and invokes {@link File#mkdirs()} on it
   *
   * @param directoryPath a {@link Path} pointing to the directory you want to create
   */
  @Override
  protected void doMkDirs(Path directoryPath) {
    File target = directoryPath.toFile();
    try {
      if (!target.mkdirs()) {
        throw exception(format("Directory '%s' could not be created", target));
      }
    } catch (Exception e) {
      throw exception(format("Exception was found creating directory '%s'", target), e);
    }
  }
}
