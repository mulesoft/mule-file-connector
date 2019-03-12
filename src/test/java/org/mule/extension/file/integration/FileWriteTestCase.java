/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.extension.file.integration;

import static java.lang.String.format;
import static java.nio.charset.Charset.availableCharsets;
import static org.apache.commons.io.FileUtils.readFileToByteArray;
import static org.apache.commons.io.FileUtils.writeStringToFile;
import static org.apache.commons.lang3.SystemUtils.IS_OS_WINDOWS;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeFalse;
import static org.mule.extension.file.AllureConstants.FileFeature.FILE_EXTENSION;
import static org.mule.extension.file.common.api.FileWriteMode.APPEND;
import static org.mule.extension.file.common.api.FileWriteMode.CREATE_NEW;
import static org.mule.extension.file.common.api.FileWriteMode.OVERWRITE;
import static org.mule.extension.file.common.api.exceptions.FileError.FILE_ALREADY_EXISTS;
import static org.mule.extension.file.common.api.exceptions.FileError.ILLEGAL_PATH;

import org.junit.Ignore;
import org.mule.extension.file.common.api.FileWriteMode;
import org.mule.extension.file.common.api.exceptions.FileAccessDeniedException;
import org.mule.extension.file.common.api.exceptions.FileAlreadyExistsException;
import org.mule.extension.file.common.api.exceptions.FileError;
import org.mule.extension.file.common.api.exceptions.IllegalPathException;
import org.mule.runtime.core.api.event.CoreEvent;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import io.qameta.allure.Feature;
import org.junit.Test;

@Feature(FILE_EXTENSION)
public class FileWriteTestCase extends FileConnectorTestCase {

  private static final String TEST_FILENAME = "test.txt";

  @Override
  protected String[] getConfigFiles() {
    return new String[] {"file-write-config.xml", "file-read-config.xml"};
  }

  @Test
  public void appendOnNotExistingFile() throws Exception {
    doWriteOnNotExistingFile(APPEND);
  }

  @Test
  public void overwriteOnNotExistingFile() throws Exception {
    doWriteOnNotExistingFile(OVERWRITE);
  }

  @Test
  public void createNewOnNotExistingFile() throws Exception {
    doWriteOnNotExistingFile(CREATE_NEW);
  }

  @Test
  public void appendOnExistingFile() throws Exception {
    String content = doWriteOnExistingFile(APPEND);
    assertThat(content, is(HELLO_WORLD + HELLO_WORLD));
  }

  @Test
  public void overwriteOnExistingFile() throws Exception {
    String content = doWriteOnExistingFile(OVERWRITE);
    assertThat(content, is(HELLO_WORLD));
  }

  @Test
  public void createNewOnExistingFile() throws Exception {
    expectedError.expectError(NAMESPACE, FILE_ALREADY_EXISTS, FileAlreadyExistsException.class,
                              "Use a different write mode or point to a path which doesn't exist");
    doWriteOnExistingFile(CREATE_NEW);
  }

  @Test
  public void appendOnNotExistingParentWithoutCreateFolder() throws Exception {
    expectedError.expectError(NAMESPACE, ILLEGAL_PATH, IllegalPathException.class,
                              "because path to it doesn't exist");
    doWriteOnNotExistingParentWithoutCreateFolder(APPEND);
  }

  @Test
  public void overwriteOnNotExistingParentWithoutCreateFolder() throws Exception {
    expectedError.expectError(NAMESPACE, ILLEGAL_PATH, IllegalPathException.class,
                              "because path to it doesn't exist");
    doWriteOnNotExistingParentWithoutCreateFolder(OVERWRITE);
  }

  @Test
  public void createNewOnNotExistingParentWithoutCreateFolder() throws Exception {
    expectedError.expectError(NAMESPACE, ILLEGAL_PATH, IllegalPathException.class,
                              "because path to it doesn't exist");
    doWriteOnNotExistingParentWithoutCreateFolder(CREATE_NEW);
  }

  @Test
  public void appendNotExistingFileWithCreatedParent() throws Exception {
    doWriteNotExistingFileWithCreatedParent(APPEND);
  }

  @Test
  public void overwriteNotExistingFileWithCreatedParent() throws Exception {
    doWriteNotExistingFileWithCreatedParent(OVERWRITE);
  }

  @Test
  public void createNewNotExistingFileWithCreatedParent() throws Exception {
    doWriteNotExistingFileWithCreatedParent(CREATE_NEW);
  }

  @Test
  public void writeOnReadFile() throws Exception {
    File file = temporaryFolder.newFile();
    writeStringToFile(file, "overwrite me!");

    CoreEvent event = flowRunner("readAndWrite").withVariable("path", file.getAbsolutePath()).run();

    assertThat(event.getMessage().getPayload().getValue(), equalTo(HELLO_WORLD));
  }

  @Test
  @Ignore("MULE-15851 - Different error is expected when trying to write to a directory path in Windows")
  public void writeOnDirectoryPath() throws Exception {
    expectedError.expectError("FILE", FileError.ILLEGAL_PATH, IllegalPathException.class, "because it is a Directory");
    flowRunner("writeStaticContent").withVariable("mode", "OVERWRITE").withVariable("path", temporaryFolder.newFolder().getPath())
        .run();
  }

  @Test
  public void writeOnDirectlyWithOutPermissions() throws Exception {
    assumeFalse(IS_OS_WINDOWS);
    expectedError.expectError("FILE", FileError.ACCESS_DENIED, FileAccessDeniedException.class,
                              "because access was denied by the operating system");
    File folder = temporaryFolder.newFolder();
    folder.setReadOnly();
    flowRunner("writeStaticContent").withVariable("mode", "OVERWRITE")
        .withVariable("path", new File(folder, "file.txt").getPath())
        .run();
  }

  @Test
  public void writeStaticContent() throws Exception {
    String path = format("%s/%s", temporaryFolder.newFolder().getPath(), TEST_FILENAME);
    doWrite("writeStaticContent", path, "", CREATE_NEW, false);

    String content = readPathAsString(path);
    assertThat(content, is(HELLO_WORLD));
  }

  @Test
  public void writeWithLock() throws Exception {
    String path = format("%s/%s", temporaryFolder.newFolder().getPath(), TEST_FILENAME);
    doWrite("writeWithLock", path, HELLO_WORLD, CREATE_NEW, false);

    String content = toString(readPath(path).getPayload().getValue());
    assertThat(content, is(HELLO_WORLD));
  }

  @Test
  public void writeWithLockOnLockedFile() throws Exception {
    final String path = "file";
    doWrite("writeStaticContent", path, "", CREATE_NEW, false);
    Exception exception = flowRunner("writeAlreadyLocked").withVariable("path", path).withVariable("createParent", false)
        .withVariable("mode", APPEND)
        .withVariable("encoding", null).withPayload(HELLO_WORLD).runExpectingException();

    Method methodGetErrors = exception.getCause().getClass().getMethod("getErrors");
    Object error = ((List<Object>) methodGetErrors.invoke(exception.getCause())).get(0);
    Method methodGetErrorType = error.getClass().getMethod("getErrorType");
    methodGetErrorType.setAccessible(true);
    Object fileError = methodGetErrorType.invoke(error);
    assertThat(fileError.toString(), is("FILE:FILE_LOCK"));
  }

  @Test
  public void writeWithLockTimeout() throws Exception {
    final String path = "file";
    flowRunner("writeWithLockTimeout")
        .withVariable("path", path)
        .withVariable("createParent", false)
        .withVariable("mode", APPEND)
        .withPayload(HELLO_WORLD)
        .withVariable("lockTimeout", 100)
        .run();

    String content = toString(readPath(path).getPayload().getValue());
    assertThat(content, is(HELLO_WORLD));
  }

  @Test
  public void writeWithLockTimeoutAndUnit() throws Exception {
    final String path = "file";
    flowRunner("writeWithLockTimeoutAndUnit")
        .withVariable("path", path)
        .withVariable("createParent", false)
        .withVariable("mode", APPEND)
        .withPayload(HELLO_WORLD)
        .withVariable("lockTimeout", 500)
        .withVariable("lockTimeoutUnit", "NANOSECONDS")
        .run();

    String content = toString(readPath(path).getPayload().getValue());
    assertThat(content, is(HELLO_WORLD));
  }

  @Test
  public void writeWithLockTimeoutOnLockedFile() throws Exception {
    final String path = "file";
    flowRunner("writeOnAlreadyLockedWithTimeout")
        .withVariable("path", path)
        .withVariable("createParent", false)
        .withVariable("mode", APPEND)
        .withPayload(HELLO_WORLD)
        .withVariable("lockTimeout", 50)
        .withVariable("lockTimeoutUnit", "SECONDS")
        .run();

    String content = toString(readPath(path).getPayload().getValue());
    assertThat(content, is(HELLO_WORLD + HELLO_WORLD));
  }

  @Test
  public void writeWithCustomEncoding() throws Exception {
    final String defaultEncoding = muleContext.getConfiguration().getDefaultEncoding();
    assertThat(defaultEncoding, is(notNullValue()));

    final String customEncoding =
        availableCharsets().keySet().stream().filter(encoding -> !encoding.equals(defaultEncoding)).findFirst().orElse(null);

    assertThat(customEncoding, is(notNullValue()));
    final String filename = "encoding.txt";

    doWrite("write", filename, HELLO_WORLD, CREATE_NEW, false, customEncoding);
    byte[] content = readFileToByteArray(new File(temporaryFolder.getRoot(), filename));

    assertThat(Arrays.equals(content, HELLO_WORLD.getBytes(customEncoding)), is(true));
  }

  private void doWriteNotExistingFileWithCreatedParent(FileWriteMode mode) throws Exception {
    File folder = temporaryFolder.newFolder();
    final String path = format("%s/a/b/%s", folder.getAbsolutePath(), TEST_FILENAME);

    doWrite(path, HELLO_WORLD, mode, true);

    String content = readPathAsString(path);
    assertThat(content, is(HELLO_WORLD));
  }


  private void doWriteOnNotExistingFile(FileWriteMode mode) throws Exception {
    String path = format("%s/%s", temporaryFolder.newFolder().getPath(), TEST_FILENAME);
    doWrite(path, HELLO_WORLD, mode, false);

    String content = readPathAsString(path);
    assertThat(content, is(HELLO_WORLD));
  }

  private void doWriteOnNotExistingParentWithoutCreateFolder(FileWriteMode mode) throws Exception {
    File folder = temporaryFolder.newFolder();
    final String path = format("%s/a/b/%s", folder.getAbsolutePath(), TEST_FILENAME);

    doWrite(path, HELLO_WORLD, mode, false);
  }

  private String doWriteOnExistingFile(FileWriteMode mode) throws Exception {
    File file = temporaryFolder.newFile();
    writeStringToFile(file, HELLO_WORLD);

    doWrite(file.getAbsolutePath(), HELLO_WORLD, mode, false);
    return readPathAsString(file.getAbsolutePath());
  }

  public static InputStream getContentStream() {
    return (new InputStream() {

      String text = "Hello World!";
      char[] textArray = text.toCharArray();
      int index = -1;

      @Override
      public int read() throws IOException {
        try {
          Thread.sleep(15);
        } catch (InterruptedException e) {
          fail();
        }
        if (index < text.length() - 1) {
          index++;
          return (int) textArray[index];
        }
        return -1;
      }
    });
  }

  public static void sleepThread() {
    try {
      Thread.sleep(2500);
    } catch (InterruptedException e) {
      fail();
    }
  }
}
