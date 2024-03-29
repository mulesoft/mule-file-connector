/*
 * Copyright 2023 Salesforce, Inc. All rights reserved.
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.extension.file.integration;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;
import static org.mule.extension.file.AllureConstants.FileFeature.FILE_EXTENSION;

import org.mule.extension.file.common.api.FileWriteMode;
import org.mule.runtime.core.api.event.CoreEvent;
import org.mule.runtime.core.api.message.OutputHandler;
import org.mule.test.runner.RunnerDelegateTo;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;

import io.qameta.allure.Feature;
import org.junit.Test;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@Feature(FILE_EXTENSION)
@RunnerDelegateTo(Parameterized.class)
public class FileWriteTypeTestCase extends FileConnectorTestCase {

  @Parameters(name = "{0}")
  public static Iterable<Object[]> data() {
    return Arrays.asList(new Object[][] {
        {"String", HELLO_WORLD, HELLO_WORLD},
        {"native byte", "A".getBytes()[0], "A"},
        {"Object byte", new Byte("A".getBytes()[0]), "A"},
        {"byte[]", HELLO_WORLD.getBytes(), HELLO_WORLD},
        {"OutputHandler", new TestOutputHandler(), HELLO_WORLD},
        {"InputStream", new ByteArrayInputStream(HELLO_WORLD.getBytes()), HELLO_WORLD},});
  }

  private final Object content;
  private final String expected;
  private String path;

  public FileWriteTypeTestCase(String name, Object content, String expected) {
    this.content = content;
    this.expected = expected;
  }

  @Override
  protected String getConfigFile() {
    return "file-write-config.xml";
  }


  @Override
  protected void doSetUp() throws Exception {
    super.doSetUp();
    path = temporaryFolder.newFolder().getPath() + "/test.txt";
  }

  @Test
  public void writeAndAssert() throws Exception {
    write(content);
    assertThat(readPathAsString(path), equalTo(expected));
  }

  private void write(Object content) throws Exception {
    doWrite(path, content, FileWriteMode.APPEND, false);
  }

  private static class TestOutputHandler implements OutputHandler {

    @Override
    public void write(CoreEvent event, OutputStream out) throws IOException {
      org.apache.commons.io.IOUtils.write(HELLO_WORLD, out);
    }
  }

  private static class HelloWorld {

    @Override
    public String toString() {
      return HELLO_WORLD;
    }
  }
}
