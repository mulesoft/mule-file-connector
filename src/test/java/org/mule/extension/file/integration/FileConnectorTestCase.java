/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.extension.file.integration;

import static java.lang.Thread.sleep;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static org.mule.extension.file.AllureConstants.FileFeature.FILE_EXTENSION;
import static org.mule.functional.api.exception.ExpectedError.none;
import static org.mule.runtime.core.api.util.IOUtils.closeQuietly;

import org.junit.rules.ExpectedException;
import org.mule.extension.file.common.api.FileWriteMode;
import org.mule.functional.api.exception.ExpectedError;
import org.mule.functional.junit4.MuleArtifactFunctionalTestCase;
import org.mule.runtime.api.event.Event;
import org.mule.runtime.api.message.Message;
import org.mule.runtime.api.streaming.bytes.CursorStreamProvider;
import org.mule.runtime.core.api.util.IOUtils;
import org.mule.tck.junit4.rule.SystemProperty;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import io.qameta.allure.Feature;
import org.apache.commons.io.FileUtils;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import org.mule.tck.probe.JUnitLambdaProbe;
import org.mule.tck.probe.PollingProber;

@Feature(FILE_EXTENSION)
public abstract class FileConnectorTestCase extends MuleArtifactFunctionalTestCase {

  protected static final String NAMESPACE = "FILE";
  protected static final String HELLO_WORLD = "Hello World!";
  protected static final String HELLO_FILE_NAME = "hello.json";
  protected static final String HELLO_PATH = "files" + File.separator + HELLO_FILE_NAME;
  protected static final String TEST_FILE_PATTERN = "test-file-%d.html";
  protected static final String SUB_DIRECTORY_NAME = "subDirectory";
  protected static final String CONTENT = "foo";

  @ClassRule
  public static TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Rule
  public ExpectedException expectedException = ExpectedException.none();

  @Rule
  public ExpectedError expectedError = none();

  @Rule
  public SystemProperty workingDir = new SystemProperty("workingDir", temporaryFolder.getRoot().getAbsolutePath());

  @Override
  protected void doSetUpBeforeMuleContextCreation() throws Exception {
    if (!temporaryFolder.getRoot().exists()) {
      temporaryFolder.getRoot().mkdir();
    }
  }

  @Override
  protected void doTearDownAfterMuleContextDispose() throws Exception {
    new PollingProber(60000, 1000).check(new JUnitLambdaProbe(() -> {
      temporaryFolder.delete();
      return temporaryFolder.getRoot().exists() ? false : true;
    }));
  }

  protected void assertExists(boolean exists, File... files) {
    for (File file : files) {
      assertThat(file.exists(), is(exists));
    }
  }

  protected Event readHelloWorld() throws Exception {
    return getPath(HELLO_PATH);
  }

  protected Message readPath(String path) throws Exception {
    return getPath(path).getMessage();
  }

  protected Event getPath(String path) throws Exception {
    return getPath(path, true);
  }

  protected Event getPath(String path, boolean streaming) throws Exception {
    return flowRunner("read")
        .withVariable("path", path)
        .withVariable("streaming", streaming)
        .run();
  }

  protected String readPathAsString(String path) throws Exception {
    return toString(readPath(path).getPayload().getValue());
  }

  protected String toString(Object value) {
    if (value == null) {
      return null;
    }

    if (value instanceof String) {
      return (String) value;
    }

    InputStream inputStream;
    if (value instanceof CursorStreamProvider) {
      inputStream = ((CursorStreamProvider) value).openCursor();
    } else if (value instanceof InputStream) {
      inputStream = (InputStream) value;
    } else {
      throw new IllegalArgumentException("Result was not of expected type");
    }

    try {
      return IOUtils.toString(inputStream);
    } finally {
      closeQuietly(inputStream);
    }
  }

  protected void doWrite(String path, Object content, FileWriteMode mode, boolean createParent) throws Exception {
    doWrite("write", path, content, mode, createParent);
  }

  protected void doWrite(String flow, String path, Object content, FileWriteMode mode, boolean createParent) throws Exception {
    doWrite(flow, path, content, mode, createParent, null);
  }

  protected void doWrite(String flow, String path, Object content, FileWriteMode mode, boolean createParent, String encoding)
      throws Exception {
    flowRunner(flow).withVariable("path", path).withVariable("createParent", createParent).withVariable("mode", mode)
        .withVariable("encoding", encoding).withPayload(content).run();
  }

  protected File createHelloWorldFile() throws IOException {
    File folder = temporaryFolder.newFolder("files");
    File hello = new File(folder, HELLO_FILE_NAME);
    FileUtils.write(hello, HELLO_WORLD);

    return hello;
  }

  protected void createTestFiles() throws Exception {
    createTestFiles(temporaryFolder.getRoot(), 0, 5);
    createSubDirectory();
  }

  protected void createSubDirectory() throws Exception {
    createTestFiles(temporaryFolder.newFolder(SUB_DIRECTORY_NAME), 5, 7);
  }

  protected void createTestFiles(File parentFolder, int startIndex, int endIndex) throws Exception {
    for (int i = startIndex; i < endIndex; i++) {
      String name = String.format(TEST_FILE_PATTERN, i);
      File file = new File(parentFolder, name);
      file.createNewFile();
      FileUtils.write(file, CONTENT);
    }
  }

  protected void writeByteByByteAsync(String path, String content, long delayBetweenCharacters) throws Exception {
    path = path.startsWith("/") ? path : temporaryFolder.getRoot().getPath() + File.separator + path;
    OutputStream outputStream = FileUtils.openOutputStream(new File(path), true);
    new Thread(() -> {
      try {
        for (int i = 0; i < content.length(); i++) {
          outputStream.write(content.charAt(i));
          sleep(delayBetweenCharacters);
        }
      } catch (Exception e) {
        fail("Error trying to write in file");
      } finally {
        try {
          outputStream.close();
        } catch (IOException e) {
          // Do nothing
        }
      }

    }).start();
  }

}
