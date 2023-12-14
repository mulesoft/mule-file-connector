/*
 * Copyright 2023 Salesforce, Inc. All rights reserved.
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.extension.file.integration;

import static org.mule.extension.file.AllureConstants.FileFeature.FILE_EXTENSION;
import static org.mule.extension.file.common.api.exceptions.FileError.FILE_ALREADY_EXISTS;
import org.mule.extension.file.common.api.exceptions.FileAlreadyExistsException;

import java.io.File;
import java.nio.file.Paths;

import org.junit.Test;
import io.qameta.allure.Feature;

@Feature(FILE_EXTENSION)
public class FileCreateDirectoryTestCase extends FileConnectorTestCase {

  private static final String DIRECTORY = "validDirectory";

  @Override
  protected String[] getConfigFiles() {
    return new String[] {"file-create-directory-config.xml", "file-read-config.xml"};
  }

  @Test
  public void createDirectory() throws Exception {
    doCreateDirectory(DIRECTORY);
    assertExists(true, new File(temporaryFolder.getRoot(), DIRECTORY));
  }

  @Test
  public void createExistingDirectory() throws Exception {
    expectedError.expectError(NAMESPACE, FILE_ALREADY_EXISTS, FileAlreadyExistsException.class, "already exists");
    final String directory = "washerefirst";
    temporaryFolder.newFolder(directory);

    doCreateDirectory(directory);
  }

  @Test
  public void createDirectoryWithComplexPath() throws Exception {
    File folder = temporaryFolder.newFolder();
    doCreateDirectory(Paths.get(folder.getAbsolutePath()).resolve(DIRECTORY).toAbsolutePath().toString());

    assertExists(true, new File(folder, DIRECTORY));
  }

  private void doCreateDirectory(String directory) throws Exception {
    flowRunner("createDirectory").withVariable("directory", directory).run();
  }

}
