/*
 * Copyright 2023 Salesforce, Inc. All rights reserved.
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.extension.file.internal.command;

import org.mule.extension.file.internal.LocalFileSystem;
import org.mule.extension.file.internal.source.DirectoryListener;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * A {@link LocalFileCommand} which implements support functionality for {@link DirectoryListener}
 *
 * @since 1.1
 */
public class OnNewFileCommand extends LocalFileCommand {

  /**
   * {@inheritDoc}
   */
  public OnNewFileCommand(LocalFileSystem fileSystem) {
    super(fileSystem);
  }

  /**
   * Resolves the root path on which the listener needs to be created
   *
   * @param directory the path that the user configured on the listener
   * @return the resolved {@link Path} to listen on
   */
  public Path resolveRootPath(String directory) {
    Path directoryPath = directory == null ? Paths.get(fileSystem.getBasePath())
        : Paths.get(fileSystem.getBasePath()).resolve(directory).toAbsolutePath();

    return resolveExistingPath(directoryPath.toAbsolutePath().toString());
  }
}
