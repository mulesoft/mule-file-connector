/*
 * Copyright 2023 Salesforce, Inc. All rights reserved.
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.extension.file.internal.command;

import static java.lang.String.format;
import org.mule.extension.file.common.api.command.CreateDirectoryCommand;
import org.mule.extension.file.common.api.exceptions.FileAlreadyExistsException;
import org.mule.extension.file.internal.LocalFileSystem;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * A {@link LocalFileCommand} which implements the {@link CreateDirectoryCommand} contract
 *
 * @since 1.0
 */
public final class LocalCreateDirectoryCommand extends LocalFileCommand implements CreateDirectoryCommand {

  /**
   * {@inheritDoc}
   */
  public LocalCreateDirectoryCommand(LocalFileSystem fileSystem) {
    super(fileSystem);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void createDirectory(String directoryPath) {
    Path target = resolvePath(directoryPath);

    if (Files.exists(target)) {
      throw new FileAlreadyExistsException(format("Directory '%s' already exists", target.toAbsolutePath()));
    }

    mkdirs(target);
  }
}
