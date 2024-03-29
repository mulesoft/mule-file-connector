/*
 * Copyright 2023 Salesforce, Inc. All rights reserved.
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.extension.file.internal.command;

import org.mule.extension.file.common.api.FileConnectorConfig;
import org.mule.extension.file.common.api.command.MoveCommand;
import org.mule.extension.file.internal.LocalFileSystem;
import org.mule.runtime.core.api.util.FileUtils;

import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.apache.commons.io.FileUtils.moveDirectory;

/**
 * A {@link AbstractLocalCopyCommand} which implements the {@link MoveCommand} contract
 *
 * @since 1.0
 */
public final class LocalMoveCommand extends AbstractLocalCopyCommand implements MoveCommand {

  /**
   * {@inheritDoc}
   */
  public LocalMoveCommand(LocalFileSystem fileSystem) {
    super(fileSystem);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void move(FileConnectorConfig config, String sourcePath, String targetDirectory, boolean overwrite,
                   boolean createParentDirectories, String renameTo) {
    execute(sourcePath, targetDirectory, overwrite, createParentDirectories, renameTo);
  }

  /**
   * Implements recursive moving
   *
   * @param source the path to be copied
   * @param targetPath the path to the target destination
   * @param overwrite whether to overwrite existing target paths
   * @param options an array of {@link CopyOption} which configure the copying operation
   */
  @Override
  protected void doExecute(Path source, Path targetPath, boolean overwrite, CopyOption[] options) throws Exception {
    if (Files.isDirectory(source)) {
      if (Files.exists(targetPath)) {
        if (overwrite) {
          FileUtils.deleteTree(targetPath.toFile());
        } else {
          alreadyExistsException(targetPath);
        }
      }
      moveDirectory(source.toFile(), targetPath.toFile());
    } else {
      Files.move(source, targetPath, options);
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  protected String getAction() {
    return "move";
  }
}
