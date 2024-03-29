/*
 * Copyright 2023 Salesforce, Inc. All rights reserved.
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.extension.file.internal;

import static java.lang.String.format;
import static java.nio.file.Files.isDirectory;
import static java.nio.file.Files.notExists;
import static org.mule.extension.file.common.api.exceptions.FileError.FILE_DOESNT_EXIST;
import static org.mule.extension.file.common.api.exceptions.FileError.FILE_IS_NOT_DIRECTORY;
import static org.mule.extension.file.common.api.exceptions.FileError.ILLEGAL_PATH;
import static org.mule.runtime.api.connection.ConnectionValidationResult.success;
import static org.mule.runtime.api.meta.model.display.PathModel.Location.EXTERNAL;
import static org.slf4j.LoggerFactory.getLogger;
import org.mule.extension.file.api.exception.FileConnectionException;
import org.mule.extension.file.common.api.FileSystem;
import org.mule.extension.file.common.api.FileSystemProvider;
import org.mule.runtime.api.connection.CachedConnectionProvider;
import org.mule.runtime.api.connection.ConnectionException;
import org.mule.runtime.api.connection.ConnectionProvider;
import org.mule.runtime.api.connection.ConnectionValidationResult;
import org.mule.runtime.extension.api.annotation.param.Optional;
import org.mule.runtime.extension.api.annotation.param.Parameter;
import org.mule.runtime.extension.api.annotation.param.display.DisplayName;
import org.mule.runtime.extension.api.annotation.param.display.Summary;

import java.nio.file.Path;
import java.nio.file.Paths;

import org.slf4j.Logger;

/**
 * A {@link ConnectionProvider} which provides instances of {@link FileSystem} from instances of {@link FileConnector}
 *
 * @since 1.0
 */
@DisplayName("Local FileSystem Connection")
public final class LocalFileConnectionProvider extends FileSystemProvider<LocalFileSystem>
    implements CachedConnectionProvider<LocalFileSystem> {

  private static final Logger LOGGER = getLogger(LocalFileConnectionProvider.class);

  /**
   * The directory to be considered as the root of every relative path used with this connector. If not provided, it will default
   * to the value of the {@code user.home} system property. If that system property is not set, then the connector will fail to
   * initialise.
   */
  @Parameter
  @Optional
  @DisplayName("Working Directory")
  @Summary("Directory to be considered as the root of every relative path used with this connector. Defaults to the user's home")
  @org.mule.runtime.extension.api.annotation.param.display.Path(location = EXTERNAL)
  private String workingDir;

  /**
   * Creates and returns a new instance of {@link LocalFileSystem}
   *
   * @return a {@link LocalFileSystem}
   */
  @Override
  public LocalFileSystem connect() throws ConnectionException {
    validateWorkingDir();
    return new LocalFileSystem(workingDir);
  }

  /**
   * Does nothing since {@link LocalFileSystem} instances do not require disconnecting
   *
   * @param localFileSystem a {@link LocalFileSystem} instance
   */
  @Override
  public void disconnect(LocalFileSystem localFileSystem) {
    // no-op
  }

  @Override
  public ConnectionValidationResult validate(LocalFileSystem fileSystem) {
    return success();
  }

  private void validateWorkingDir() throws ConnectionException {
    if (workingDir == null) {
      workingDir = System.getProperty("user.home");
      if (workingDir == null) {
        throw new FileConnectionException("Could not obtain user's home directory. Please provide a explicit value for the workingDir parameter",
                                          ILLEGAL_PATH);
      }

      LOGGER.warn("File connector '{}' does not specify the workingDir property. Defaulting to '{}'", getConfigName(),
                  workingDir);
    }

    Path workingDirPath = Paths.get(workingDir);
    if (notExists(workingDirPath)) {
      throw new FileConnectionException(format("Provided workingDir '%s' does not exists", workingDirPath.toAbsolutePath()),
                                        FILE_DOESNT_EXIST);
    }

    if (!isDirectory(workingDirPath)) {
      throw new FileConnectionException(format("Provided workingDir '%s' is not a directory", workingDirPath.toAbsolutePath()),
                                        FILE_IS_NOT_DIRECTORY);
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String getWorkingDir() {
    return workingDir;
  }
}
