/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.extension.file;

import static org.mule.extension.file.common.api.exceptions.FileError.FILE_ALREADY_EXISTS;
import static org.mule.extension.file.common.api.exceptions.FileError.ILLEGAL_PATH;
import static org.mule.test.allure.AllureConstants.FileFeature.FILE_EXTENSION;
import static java.lang.String.format;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import org.mule.extension.file.common.api.exceptions.FileAlreadyExistsException;
import org.mule.extension.file.common.api.exceptions.IllegalPathException;
import org.mule.runtime.api.exception.MuleRuntimeException;

import java.io.File;

import org.junit.Test;
import ru.yandex.qatools.allure.annotations.Features;

@Features(FILE_EXTENSION)
public class FileRenameTestCase extends FileConnectorTestCase {

  private static final String RENAME_TO = "renamed";
  private static final String RENAME_TO_DIRECTORY = "directoryToBeOverwritten";

  @Override
  protected String getConfigFile() {
    return "file-rename-config.xml";
  }

  @Test
  public void renameFile() throws Exception {
    File origin = createHelloWorldFile();

    doRename(origin.getAbsolutePath());
    assertRenamedFile(origin);
  }

  @Test
  public void renameReadFile() throws Exception {
    File origin = createHelloWorldFile();

    doRename("readAndRename", origin.getAbsolutePath(), RENAME_TO, false);
    assertRenamedFile(origin);
  }

  @Test
  public void renameDirectory() throws Exception {
    File origin = createHelloWorldFile().getParentFile();
    doRename(origin.getAbsolutePath());

    File expected = new File(origin.getParent(), RENAME_TO);

    assertExists(false, origin);
    assertExists(true, expected);

    assertThat(readPathAsString(format("%s/%s", expected.getAbsolutePath(), HELLO_FILE_NAME)), is(HELLO_WORLD));
  }

  @Test
  public void renameDirectoryAndOverwriteANonEmptyDirectory() throws Exception {
    File origin = createHelloWorldFile().getParentFile();
    File nonEmptyTargetDirectory = temporaryFolder.newFolder(RENAME_TO_DIRECTORY);
    File nonEmptyTargetDirectoryFile = new File(nonEmptyTargetDirectory, RENAME_TO);
    nonEmptyTargetDirectoryFile.createNewFile();
    doRename(getFlowName(), origin.getAbsolutePath(), RENAME_TO_DIRECTORY, true);

    assertExists(false, origin);
    assertThat(readPathAsString(format("%s/%s", nonEmptyTargetDirectory.getAbsolutePath(), HELLO_FILE_NAME)), is(HELLO_WORLD));
  }

  @Test
  public void failOnOverwriteANonReadableDirectory() throws Exception {
    expectedError.expectCause(instanceOf(MuleRuntimeException.class));

    File origin = createHelloWorldFile().getParentFile();
    File nonReadableTargetDirectory = temporaryFolder.newFolder(RENAME_TO_DIRECTORY);
    nonReadableTargetDirectory.setReadable(false);

    doRename("rename", origin.getAbsolutePath(), RENAME_TO_DIRECTORY, true);
  }

  @Test
  public void renameUnexisting() throws Exception {
    expectedError.expectError(NAMESPACE, ILLEGAL_PATH, IllegalPathException.class, "doesn't exists");
    doRename("not-there.txt");
  }

  @Test
  public void targetPathContainsParts() throws Exception {
    expectedError.expectError(NAMESPACE, ILLEGAL_PATH, IllegalPathException.class,
                              "parameter of rename operation should not contain any file separator character");
    File origin = temporaryFolder.newFile("source");
    doRename(getFlowName(), origin.getAbsolutePath(), "path/with/parts", true);
  }

  @Test
  public void targetAlreadyExistsWithoutOverwrite() throws Exception {
    expectedError.expectError(NAMESPACE, FILE_ALREADY_EXISTS, FileAlreadyExistsException.class, "already exists");
    File origin = temporaryFolder.newFile("source");
    temporaryFolder.newFile(RENAME_TO);
    doRename(origin.getAbsolutePath());
  }

  @Test
  public void targetAlreadyExistsWithOverwrite() throws Exception {
    File origin = createHelloWorldFile();
    File targetFile = new File(origin.getParent(), RENAME_TO);
    targetFile.createNewFile();

    doRename(origin.getAbsolutePath(), true);
    assertRenamedFile(origin);
  }

  private void assertRenamedFile(File origin) throws Exception {
    File expected = new File(origin.getParent(), RENAME_TO);

    assertExists(false, origin);
    assertExists(true, expected);
    assertThat(readPathAsString(expected.getAbsolutePath()), is(HELLO_WORLD));
  }

  private void doRename(String source) throws Exception {
    doRename("rename", source, RENAME_TO, false);
  }

  private void doRename(String source, boolean overwrite) throws Exception {
    doRename("rename", source, RENAME_TO, overwrite);
  }

  private void doRename(String flow, String source, String to, boolean overwrite) throws Exception {
    flowRunner(flow).withVariable("path", source).withVariable("to", to).withVariable("overwrite", overwrite).run();
  }

  private String getFlowName() {
    return "rename";
  }
}
