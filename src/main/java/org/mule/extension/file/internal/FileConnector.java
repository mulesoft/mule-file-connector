/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.extension.file.internal;

import org.mule.extension.file.api.LocalFileAttributes;
import org.mule.extension.file.common.api.BaseFileSystemOperations;
import org.mule.extension.file.common.api.FileConnectorConfig;
import org.mule.extension.file.common.api.exceptions.FileError;
import org.mule.extension.file.internal.source.DirectoryListener;
import org.mule.runtime.extension.api.annotation.Export;
import org.mule.runtime.extension.api.annotation.Extension;
import org.mule.runtime.extension.api.annotation.Operations;
import org.mule.runtime.extension.api.annotation.Sources;
import org.mule.runtime.extension.api.annotation.connectivity.ConnectionProviders;
import org.mule.runtime.extension.api.annotation.error.ErrorTypes;

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
@ConnectionProviders(LocalFileConnectionProvider.class)
@ErrorTypes(FileError.class)
@Sources(DirectoryListener.class)
@Export(classes = LocalFileAttributes.class)
public class FileConnector extends FileConnectorConfig {

}
