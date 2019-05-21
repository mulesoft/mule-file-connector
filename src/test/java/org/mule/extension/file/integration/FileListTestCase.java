/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.extension.file.integration;

import static org.apache.commons.lang3.SystemUtils.IS_OS_WINDOWS;
import static org.hamcrest.CoreMatchers.endsWith;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.Assert.assertThat;
import static org.junit.Assume.assumeFalse;
import static org.mule.extension.file.AllureConstants.FileFeature.FILE_EXTENSION;
import static org.mule.extension.file.common.api.exceptions.FileError.ACCESS_DENIED;
import static org.mule.extension.file.common.api.exceptions.FileError.ILLEGAL_PATH;

import org.mule.extension.file.common.api.FileAttributes;
import org.mule.extension.file.common.api.exceptions.FileAccessDeniedException;
import org.mule.extension.file.common.api.exceptions.IllegalPathException;
import org.mule.runtime.api.message.Message;
import org.mule.runtime.api.streaming.object.CursorIteratorProvider;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

import io.qameta.allure.Feature;
import org.junit.Test;

@Feature(FILE_EXTENSION)
public class FileListTestCase extends FileConnectorTestCase {

  private static String FILE_BEING_WRITTEN = "test-file-2.html";
  private static String LONG_CONTENT = "longlonglonglonglonglonglonglonglonglonglonglonglonglonglonglonglong";

  @Override
  protected String getConfigFile() {
    return "file-list-config.xml";
  }

  @Override
  protected void doSetUp() throws Exception {
    super.doSetUp();
    createTestFiles();
  }

  @Test
  public void listNotRecursive() throws Exception {
    List<Message> messages = doList(".", false);

    assertThat(messages, hasSize(6));
    assertThat(assertListedFiles(messages), is(true));
  }

  @Test
  public void listRecursive() throws Exception {
    List<Message> messages = doList(".", true);
    assertRecursiveTreeNode(messages);
  }

  @Test
  public void listRecursiveWithSizeCheck() throws Exception {
    List<Message> messages = doListWithSizeCheck(".", true);
    assertRecursiveTreeNode(messages);
  }

  @Test
  public void listRecursiveWithSizeCheckWithFileStillBeingWritten() throws Exception {
    expectedException.expectMessage("being written");
    writeByteByByteAsync(FILE_BEING_WRITTEN, LONG_CONTENT, 250);
    List<Message> messages = doListWithSizeCheck(".", true);
    assertRecursiveTreeNode(messages);
  }

  @Test
  public void listWithDirectoryWithoutReadPermission() throws Exception {
    assumeFalse(IS_OS_WINDOWS);
    temporaryFolder.newFolder("forbiddenDirectory").setReadable(false);
    List<Message> messages = doListWithSizeCheck(".", true);
    assertRecursiveTreeNode(messages);
  }

  @Test
  public void listWithFileWithoutReadPermission() throws Exception {
    assumeFalse(IS_OS_WINDOWS);
    temporaryFolder.newFile("forbiddenFile").setReadable(false);
    List<Message> messages = doList(".", false);

    assertThat(messages, hasSize(6));
    assertThat(assertListedFiles(messages), is(true));
  }

  @Test
  public void listDirectoryWithoutReadPermission() throws Exception {
    assumeFalse(IS_OS_WINDOWS);
    expectedError.expectError(NAMESPACE, ACCESS_DENIED, FileAccessDeniedException.class,
                              "access was denied by the operating system");

    temporaryFolder.newFolder("forbiddenDirectory").setReadable(false);
    doList("forbiddenDirectory", false);
  }

  @Test
  public void listFilesRecursivelyWithNameFilter() throws Exception {
    List<Message> messages = doList("listFilesRecursivelyWithNameFilter", ".", true);
    assertThat(messages, hasSize(7));
  }

  @Test
  public void listFilesRecursivelyWithSpecificNameFilter() throws Exception {
    List<Message> messages = doList("listFilesRecursivelyWithSpecificNameFilter", ".", true);
    assertThat(messages, hasSize(1));
  }

  private void assertRecursiveTreeNode(List<Message> messages) throws Exception {
    assertThat(messages, hasSize(8));
    assertThat(assertListedFiles(messages), is(true));

    List<Message> subDirectories =
        messages.stream()
            .filter(message -> ((FileAttributes) message.getAttributes().getValue()).isDirectory())
            .collect(Collectors.toList());

    assertThat(subDirectories, hasSize(1));
    assertThat(assertListedFiles(subDirectories), is(true));
  }

  @Test
  public void notDirectory() throws Exception {
    expectedError.expectError(NAMESPACE, ILLEGAL_PATH, IllegalPathException.class, "Only directories can be listed");
    doList(String.format(TEST_FILE_PATTERN, 0), false);
  }

  @Test
  public void notExistingPath() throws Exception {
    expectedError.expectError(NAMESPACE, ILLEGAL_PATH, IllegalPathException.class, "doesn't exist");
    doList("whatever", false);
  }

  @Test
  public void listWithEmbeddedMatcher() throws Exception {
    List<Message> messages = doList("listWithEmbeddedPredicate", ".", false);

    assertThat(messages, hasSize(2));
    assertThat(assertListedFiles(messages), is(false));
  }

  @Test
  public void listWithGlobalMatcher() throws Exception {
    List<Message> messages = doList("listWithGlobalMatcher", ".", true);

    assertThat(messages, hasSize(1));

    FileAttributes file = (FileAttributes) messages.get(0).getAttributes().getValue();
    assertThat(file.isDirectory(), is(true));
    assertThat(file.getName(), equalTo(SUB_DIRECTORY_NAME));
  }

  @Test
  public void listWithExpressionMatcher() throws Exception {
    List<Message> messages = doList("listWithExpressionMatcher", ".", true);

    assertThat(messages, hasSize(1));

    FileAttributes file = (FileAttributes) messages.get(0).getAttributes().getValue();
    assertThat(file.isDirectory(), is(true));
    assertThat(file.getName(), equalTo(SUB_DIRECTORY_NAME));
  }

  private boolean assertListedFiles(List<Message> messages) throws Exception {
    boolean directoryWasFound = false;

    for (Message message : messages) {
      FileAttributes attributes = (FileAttributes) message.getAttributes().getValue();
      if (attributes.isDirectory()) {
        assertThat("two directories found", directoryWasFound, is(false));
        directoryWasFound = true;
        assertThat(attributes.getName(), equalTo(SUB_DIRECTORY_NAME));
      } else {
        assertThat(attributes.getName(), endsWith(".html"));
        assertThat(toString(message.getPayload().getValue()), equalTo(CONTENT));
        assertThat(attributes.getSize(), is(new Long(CONTENT.length())));
      }
    }

    return directoryWasFound;
  }

  private List<Message> doList(String path, boolean recursive) throws Exception {
    return doList("list", path, recursive);
  }

  private List<Message> doListWithSizeCheck(String path, boolean recursive) throws Exception {
    return doList("listWithSizeCheck", path, recursive);
  }

  private List<Message> doList(String flowName, String path, boolean recursive) throws Exception {
    CursorIteratorProvider iteratorProvider = (CursorIteratorProvider) (flowRunner(flowName)
        .withVariable("path", path).withVariable("recursive", recursive).keepStreamsOpen()
        .run()
        .getMessage().getPayload().getValue());

    assertThat(iteratorProvider, is(notNullValue()));

    Iterator<Message> iterator = iteratorProvider.openCursor();

    List<Message> results = new LinkedList<>();

    while (iterator.hasNext()) {
      results.add(iterator.next());
    }
    return results;
  }

}
