/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.extension.file.internal.command;

import static java.lang.String.format;

import org.mule.extension.file.api.LocalFileAttributes;
import org.mule.extension.file.common.api.FileConnectorConfig;
import org.mule.extension.file.common.api.command.ListCommand;
import org.mule.extension.file.common.api.exceptions.FileAccessDeniedException;
import org.mule.extension.file.internal.LocalFileSystem;
import org.mule.runtime.api.exception.MuleRuntimeException;
import org.mule.runtime.extension.api.runtime.operation.Result;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Predicate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link LocalFileCommand} which implements the {@link ListCommand}
 *
 * @since 1.0
 */
public final class LocalListCommand extends LocalFileCommand implements ListCommand<LocalFileAttributes> {

  private static final Logger LOGGER = LoggerFactory.getLogger(LocalListCommand.class);

  private final LocalReadCommand readCommand;

  /**
   * {@inheritDoc}
   */
  public LocalListCommand(LocalFileSystem fileSystem, LocalReadCommand readCommand) {
    super(fileSystem);
    this.readCommand = readCommand;
  }

  /**
   * {@inheritDoc}
   */
  @Deprecated
  @Override
  public List<Result<InputStream, LocalFileAttributes>> list(FileConnectorConfig config,
                                                             String directoryPath,
                                                             boolean recursive,
                                                             Predicate<LocalFileAttributes> matcher) {
    return list(config, directoryPath, recursive, matcher, null);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<Result<InputStream, LocalFileAttributes>> list(FileConnectorConfig config,
                                                             String directoryPath,
                                                             boolean recursive,
                                                             Predicate<LocalFileAttributes> matcher,
                                                             Long timeBetweenSizeCheck) {
    Path path = resolveExistingPath(directoryPath);
    if (!Files.isDirectory(path)) {
      throw cannotListFileException(path);
    }

    List<Result<InputStream, LocalFileAttributes>> accumulator = new LinkedList<>();
    doList(config, path.toFile(), accumulator, recursive, matcher, timeBetweenSizeCheck);

    return accumulator;
  }

  private void doList(FileConnectorConfig config,
                      File parent,
                      List<Result<InputStream, LocalFileAttributes>> accumulator,
                      boolean recursive,
                      Predicate<LocalFileAttributes> matcher,
                      Long timeBetweenSizeCheck) {

    if (!parent.canRead()) {
      throw new FileAccessDeniedException(
                                          format("Could not list files from directory '%s' because access was denied by the operating system",
                                                 parent.getAbsolutePath()));
    }

    for (File child : parent.listFiles()) {
      Path path = child.toPath();
      try {
        LocalFileAttributes attributes = new LocalFileAttributes(path);
        if (child.isDirectory()) {
          if (matcher.test(attributes)) {
            accumulator.add(Result.<InputStream, LocalFileAttributes>builder().output(null).attributes(attributes).build());
          }

          if (recursive) {
            doList(config, child, accumulator, recursive, matcher, timeBetweenSizeCheck);
          }
        } else {
          if (matcher.test(attributes)) {
            accumulator.add(readCommand.read(config, attributes, false, timeBetweenSizeCheck));
          }
        }

      } catch (MuleRuntimeException e) {
        if (e.getCause() instanceof NoSuchFileException) {
          LOGGER
              .debug("A file with path %s was found while listing but was not found when trying to open a file channel to access the file",
                     path.toString());
        } else {
          throw e;
        }
      }
    }
  }

}
