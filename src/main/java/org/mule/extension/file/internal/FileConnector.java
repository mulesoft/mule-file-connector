/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.extension.file.internal;

import org.mule.extension.file.api.DeletedFileAttributes;
import org.mule.extension.file.api.EventedFileAttributes;
import org.mule.extension.file.api.FileEventType;
import org.mule.extension.file.api.ListenerFileAttributes;
import org.mule.extension.file.api.LocalFileAttributes;
import org.mule.extension.file.api.exception.FileConnectionException;
import org.mule.extension.file.common.api.BaseFileSystemOperations;
import org.mule.extension.file.common.api.FileConnectorConfig;
import org.mule.extension.file.common.api.exceptions.FileError;
import org.mule.runtime.extension.api.annotation.Export;
import org.mule.runtime.extension.api.annotation.Extension;
import org.mule.runtime.extension.api.annotation.Operations;
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
@Extension(name = "File", description = "Connector to manipulate files on a locally mounted file system")
@Operations({FileOperations.class})
@ConnectionProviders(LocalFileConnectionProvider.class)
@ErrorTypes(FileError.class)
@Export(classes = {LocalFileAttributes.class, FileEventType.class, ListenerFileAttributes.class, EventedFileAttributes.class,
    DeletedFileAttributes.class, FileConnectionException.class})
public class FileConnector extends FileConnectorConfig {

}
