/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.extension.file.internal;

import org.mule.extension.file.api.LocalFileAttributes;
import org.mule.extension.file.api.lock.BasicLockStrategy;
import org.mule.extension.file.api.lock.LockStrategy;
import org.mule.extension.file.common.api.BaseFileSystemOperations;
import org.mule.extension.file.common.api.FileConnectorConfig;
import org.mule.extension.file.common.api.exceptions.FileError;
import org.mule.extension.file.internal.source.DirectoryListener;
import org.mule.runtime.extension.api.annotation.Export;
import org.mule.runtime.extension.api.annotation.Extension;
import org.mule.runtime.extension.api.annotation.Operations;
import org.mule.runtime.extension.api.annotation.Sources;
import org.mule.runtime.extension.api.annotation.SubTypeMapping;
import org.mule.runtime.extension.api.annotation.connectivity.ConnectionProviders;
import org.mule.runtime.extension.api.annotation.error.ErrorTypes;
import org.mule.runtime.extension.api.annotation.param.Optional;
import org.mule.runtime.extension.api.annotation.param.Parameter;
import org.mule.runtime.extension.api.annotation.param.display.Placement;
import org.mule.runtime.extension.api.annotation.param.display.Summary;

import java.util.concurrent.TimeUnit;

import static org.mule.runtime.extension.api.annotation.param.display.Placement.ADVANCED_TAB;

/**
 * File connector used to manipulate file systems mounted on the host operation system.
 * <p>
 * This class serves as both extension definition and configuration. Operations are based on the standard
 * {@link BaseFileSystemOperations}
 *
 * @since 1.0
 */
@Extension(name = "File")
@Operations({FileOperations.class})
@SubTypeMapping(baseType = LockStrategy.class, subTypes = {BasicLockStrategy.class})
@ConnectionProviders(LocalFileConnectionProvider.class)
@ErrorTypes(FileError.class)
@Sources(DirectoryListener.class)
@Export(classes = LocalFileAttributes.class)
public class FileConnector extends FileConnectorConfig {

  /**
   * Wait time between size checks to determine if a file is ready to be read. This allows a file write to complete before
   * processing. If no value is provided, the check will not be performed. When enabled, Mule performs two size checks waiting the
   * specified time between calls. If both checks return the same value, the file is ready to be read.This attribute works in
   * tandem with {@link #timeBetweenSizeCheckUnit}.
   */
  @Parameter
  @Placement(tab = ADVANCED_TAB)
  @Summary("Wait time between size checks to determine if a file is ready to be read.")
  @Optional
  private Long timeBetweenSizeCheck;

  /**
   * A {@link TimeUnit} which qualifies the {@link #timeBetweenSizeCheck} attribute.
   * <p>
   * Defaults to {@code MILLISECONDS}
   */
  @Parameter
  @Placement(tab = ADVANCED_TAB)
  @Optional(defaultValue = "MILLISECONDS")
  @Summary("Time unit to be used in the wait time between size checks")
  private TimeUnit timeBetweenSizeCheckUnit;

}
