/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.extension.file.integration;

import static org.apache.commons.io.FileUtils.writeByteArrayToFile;
import static org.apache.commons.lang3.SystemUtils.IS_OS_WINDOWS;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeFalse;
import static org.mule.extension.file.AllureConstants.FileFeature.FILE_EXTENSION;
import static org.mule.extension.file.common.api.exceptions.FileError.ACCESS_DENIED;
import static org.mule.extension.file.common.api.exceptions.FileError.ILLEGAL_PATH;
import static org.mule.runtime.api.metadata.MediaType.JSON;

import org.mule.extension.file.api.LocalFileAttributes;
import org.mule.extension.file.common.api.exceptions.FileAccessDeniedException;
import org.mule.extension.file.common.api.exceptions.IllegalPathException;
import org.mule.extension.file.common.api.stream.AbstractNonFinalizableFileInputStream;
import org.mule.runtime.api.event.Event;
import org.mule.runtime.api.message.Message;
import org.mule.runtime.api.metadata.MediaType;
import org.mule.runtime.core.api.event.CoreEvent;
import org.mule.runtime.core.api.processor.Processor;
import org.mule.runtime.core.api.util.IOUtils;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.time.LocalDateTime;
import java.time.ZoneId;

import org.junit.Ignore;
import org.junit.Test;

import io.qameta.allure.Feature;

@Feature(FILE_EXTENSION)
public class FileReadTestCase extends FileConnectorTestCase {

  private static String DELETED_FILE_NAME = "deleted.txt";
  private static String DELETED_FILE_CONTENT = "non existant content";
  private static String WATCH_FILE = "watch.txt";
  private static String payloadString;

  @Override
  protected String getConfigFile() {
    return "file-read-config.xml";
  }

  @Override
  protected void doSetUp() throws Exception {
    super.doSetUp();
    createHelloWorldFile();
  }

  @Test
  public void read() throws Exception {
    Event response = readHelloWorld();

    assertThat(response.getMessage().getPayload().getDataType().getMediaType().getPrimaryType(), is(JSON.getPrimaryType()));
    assertThat(response.getMessage().getPayload().getDataType().getMediaType().getSubType(), is(JSON.getSubType()));

    assertThat(toString(response.getMessage().getPayload().getValue()), is(HELLO_WORLD));
  }

  @Test
  public void readBinary() throws Exception {
    final byte[] binaryPayload = HELLO_WORLD.getBytes();
    final String binaryFileName = "binary.bin";
    File binaryFile = new File(temporaryFolder.getRoot(), binaryFileName);
    writeByteArrayToFile(binaryFile, binaryPayload);

    Event response = getPath(binaryFile.getAbsolutePath(), false);

    assertThat(response.getMessage().getPayload().getDataType().getMediaType().getPrimaryType(),
               is(MediaType.BINARY.getPrimaryType()));
    assertThat(response.getMessage().getPayload().getDataType().getMediaType().getSubType(), is(MediaType.BINARY.getSubType()));

    assertThat(payloadString, is(HELLO_WORLD));
  }

  @Test
  public void readWithForcedMimeType() throws Exception {
    Event event = flowRunner("readWithForcedMimeType").withVariable("path", HELLO_PATH).run();
    assertThat(event.getMessage().getPayload().getDataType().getMediaType().getPrimaryType(), equalTo("test"));
    assertThat(event.getMessage().getPayload().getDataType().getMediaType().getSubType(), equalTo("test"));
  }

  @Test
  public void readUnexisting() throws Exception {
    expectedError.expectError(NAMESPACE, ILLEGAL_PATH, IllegalPathException.class, "doesn't exist");
    readPath("files/not-there.txt");
  }

  @Test
  public void readWithLockAndWithoutEnoughPermissions() throws Exception {
    expectedError.expectErrorType(NAMESPACE, ACCESS_DENIED.name());

    File forbiddenFile = temporaryFolder.newFile("forbiddenFile");
    forbiddenFile.createNewFile();
    forbiddenFile.setWritable(false);
    readWithLock(forbiddenFile.getAbsolutePath());
  }

  @Test
  public void readWithoutEnoughPermissions() throws Exception {
    assumeFalse(IS_OS_WINDOWS);
    expectedError.expectError(NAMESPACE, ACCESS_DENIED, FileAccessDeniedException.class,
                              "access was denied by the operating system");

    final byte[] binaryPayload = HELLO_WORLD.getBytes();
    final String binaryFileName = "binary.txt";
    File binaryFile = new File(temporaryFolder.getRoot(), binaryFileName);
    writeByteArrayToFile(binaryFile, binaryPayload);
    binaryFile.setReadable(false);

    readPath(binaryFile.getPath());
  }

  @Test
  public void readDirectory() throws Exception {
    expectedError.expectError(NAMESPACE, ILLEGAL_PATH, IllegalPathException.class, "since it's a directory");
    readPath("files");
  }

  @Test
  public void getProperties() throws Exception {
    LocalFileAttributes filePayload = (LocalFileAttributes) readHelloWorld().getMessage().getAttributes().getValue();
    Path file = Paths.get(workingDir.getValue()).resolve(HELLO_PATH);
    assertExists(true, file.toFile());

    BasicFileAttributes attributes = Files.readAttributes(file, BasicFileAttributes.class);
    assertTime(filePayload.getCreationTime(), attributes.creationTime());
    assertThat(filePayload.getName(), equalTo(file.getFileName().toString()));
    assertThat(filePayload.getLastAccessTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
               lessThanOrEqualTo(attributes.lastAccessTime().toInstant().toEpochMilli()));
    assertTime(filePayload.getLastModifiedTime(), attributes.lastModifiedTime());
    assertThat(filePayload.getPath(), is(file.toAbsolutePath().toString()));
    assertThat(filePayload.getSize(), is(attributes.size()));
    assertThat(filePayload.isDirectory(), is(false));
    assertThat(filePayload.isSymbolicLink(), is(false));
    assertThat(filePayload.isRegularFile(), is(true));
  }

  @Test
  @Ignore("MULE-15859 - Different error is expected when you try to read a file that is deleted in Windows")
  public void readFileThatIsDeleted() throws Exception {
    expectedException.expectMessage("was read but does not exist anymore.");
    File file = new File(temporaryFolder.getRoot(), DELETED_FILE_NAME);
    writeByteArrayToFile(file, DELETED_FILE_CONTENT.getBytes());
    flowRunner("readFileThatIsDeleted").withVariable("path", DELETED_FILE_NAME).run().getMessage().getPayload().getValue();
  }

  @Test
  public void readWhileStillWriting() throws Exception {
    expectedException.expectMessage("is still being written");
    writeByteByByteAsync(WATCH_FILE, "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", 500);
    flowRunner("readFileWithSizeCheck").withVariable("path", WATCH_FILE).run().getMessage().getPayload().getValue();
  }

  @Test
  public void readWhileFinishWriting() throws Exception {
    writeByteByByteAsync(WATCH_FILE, "aaaaa", 500);
    String result = (String) flowRunner("readFileWithSizeCheck").withVariable("path", WATCH_FILE).run().getMessage()
        .getPayload().getValue();
    assertThat(result, is("aaaaa"));
  }

  private Message readWithLock() throws Exception {
    return readWithLock(HELLO_PATH);
  }

  private Message readWithLock(String path) throws Exception {
    Message message = flowRunner("readWithLock").keepStreamsOpen().withVariable("path", path).run().getMessage();
    assertThat(((AbstractNonFinalizableFileInputStream) message.getPayload().getValue()).isLocked(), is(true));
    return message;
  }

  private void assertTime(LocalDateTime dateTime, FileTime fileTime) {
    assertThat(dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), is(fileTime.toInstant().toEpochMilli()));
  }

  public static final class CapturePayloadProcessor implements Processor {

    @Override
    public CoreEvent process(CoreEvent event) {
      try {
        payloadString = IOUtils.toString((InputStream) event.getMessage().getPayload().getValue());
      } catch (Exception e) {
        fail();
      }

      return event;
    }
  }

}
