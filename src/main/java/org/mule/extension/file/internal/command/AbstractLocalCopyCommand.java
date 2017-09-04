/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.extension.file.internal.command;

import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.isBlank;
import org.mule.extension.file.common.api.FileConnectorConfig;
import org.mule.extension.file.common.api.FileSystem;
import org.mule.extension.file.common.api.exceptions.IllegalPathException;
import org.mule.extension.file.internal.LocalFileSystem;

import java.nio.file.CopyOption;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Base class for commands that generates copies of a local file, either by copying or moving them.
 * <p>
 * This class contains the logic to determine the actual target path in a way which provides bash semantics, as described in the
 * {@link FileSystem#copy(FileConnectorConfig, String, String, boolean, boolean, String)} and
 * {@link FileSystem#move(FileConnectorConfig, String, String, boolean, boolean)} methods.
 * <p>
 * This command also handles the concern of the target path already existing and whether or not overwrite it.
 *
 * @since 1.0
 */
abstract class AbstractLocalCopyCommand extends LocalFileCommand {

  /**
   * {@inheritDoc}
   */
  AbstractLocalCopyCommand(LocalFileSystem fileSystem) {
    super(fileSystem);
  }

  /**
   * Performs the base logic and delegates into {@link #doExecute(Path, Path, boolean, CopyOption[])} to perform the actual
   * copying logic
   *
   * @param sourcePath the path to be copied
   * @param target the path to the target destination
   * @param overwrite whether to overwrite existing target paths
   * @param createParentDirectory whether to create the target's parent directory if it doesn't exists
   * @param renameTo the new file name, {@code null} if the file doesn't need to be renamed
   */
  protected final void execute(String sourcePath, String target, boolean overwrite, boolean createParentDirectory,
                               String renameTo) {
    Path source = resolveExistingPath(sourcePath);
    Path targetPath = resolvePath(target);
    String targetFileName = isBlank(renameTo) ? source.getFileName().toString() : renameTo;

    CopyOption copyOption = null;
    targetPath = buildTargetPath(overwrite, createParentDirectory, source, targetPath, targetFileName);

    if (Files.exists(targetPath)) {
      if (overwrite) {
        copyOption = StandardCopyOption.REPLACE_EXISTING;
      } else {
        throw alreadyExistsException(targetPath);
      }
    }

    try {
      doExecute(source, targetPath, overwrite, copyOption != null ? new CopyOption[] {copyOption} : new CopyOption[] {});
    } catch (FileAlreadyExistsException e) {
      throw new org.mule.extension.file.common.api.exceptions.FileAlreadyExistsException(format("Can't %s '%s' to '%s' because the destination path "
          + "already exists. Consider setting the 'overwrite' parameter to 'true'", getAction(), source.toAbsolutePath(),
                                                                                                targetPath.toAbsolutePath()));
    } catch (Exception e) {
      throw exception(format("An error occurred while executing '%s' operation on file '%s' to '%s': %s", getAction(), source,
                             targetPath,
                             e.getMessage()),
                      e);
    }
  }

  /**
   * Validates and constructs the target path (creating parent directories if its configured, failing if the file already exists and overwrite is not set).
   * This construction depends on whether the original target and the source are directories, files or a mix between those options.
   */
  private Path buildTargetPath(boolean overwrite, boolean createParentDirectory, Path source, Path targetPath,
                               String targetFileName) {
    if (Files.exists(targetPath)) {
      if (Files.isDirectory(targetPath)) {
        if (Files.isDirectory(source) && source.getFileName().equals(targetPath.getFileName()) && !overwrite) {
          throw alreadyExistsException(targetPath);
        } else {
          targetPath = targetPath.resolve(targetFileName);
        }
      } else if (!overwrite) {
        throw alreadyExistsException(targetPath);
      }
    } else {
      if (createParentDirectory) {
        targetPath.toFile().mkdirs();
        targetPath = targetPath.resolve(targetFileName);
      } else {
        throw new IllegalPathException(format("Can't %s '%s' to '%s' because the destination path " + "doesn't exists",
                                              getAction(),
                                              source.toAbsolutePath(), targetPath.toAbsolutePath()));
      }
    }
    return targetPath;
  }

  /**
   * Implement this method with the corresponding copying logic
   *
   * @param source the path to be copied
   * @param targetPath the path to the target destination
   * @param overwrite whether to overwrite existing target paths
   * @param options an array of {@link CopyOption} which configure the copying operation
   */
  protected abstract void doExecute(Path source, Path targetPath, boolean overwrite, CopyOption[] options) throws Exception;

  /**
   * @return The name of the action that the implementation is actually doing. Useful for logging and exception messages
   */
  protected abstract String getAction();
}
