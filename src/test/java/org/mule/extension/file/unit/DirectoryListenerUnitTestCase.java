/*
 * Copyright 2023 Salesforce, Inc. All rights reserved.
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.extension.file.unit;

import static java.util.Optional.empty;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mule.runtime.extension.api.runtime.source.PollContext.PollItemStatus.SOURCE_STOPPING;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import org.apache.commons.lang3.reflect.FieldUtils;
import org.mule.extension.file.api.LocalFileAttributes;
import org.mule.extension.file.internal.FileConnector;
import org.mule.extension.file.internal.LocalFileSystem;
import org.mule.extension.file.internal.source.DirectoryListener;
import org.mule.runtime.api.connection.ConnectionProvider;
import org.mule.runtime.extension.api.runtime.operation.Result;
import org.mule.runtime.extension.api.runtime.source.PollContext;
import org.mule.runtime.extension.api.runtime.source.SourceCallback;
import org.mule.runtime.extension.api.runtime.source.SourceCallbackContext;
import org.slf4j.Logger;

import java.io.InputStream;
import java.io.Serializable;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import org.junit.Before;
import org.junit.Test;
import org.mockito.stubbing.Answer;

public class DirectoryListenerUnitTestCase {

  private static final int AMOUNT_OF_MOCK_RESULTS = 10;
  private static final String FILE_PATH = "/file/path";

  private FileConnector config = mock(FileConnector.class);
  private ConnectionProvider<LocalFileSystem> fileSystemProvider = mock(ConnectionProvider.class);
  private PollContext pollContext = mock(PollContext.class);
  private DirectoryListener directoryListener;
  private LocalFileSystem localFileSystemMock;

  @Before
  public void setup() throws Exception {
    directoryListener = new DirectoryListener(config, fileSystemProvider);
    when(config.getTimeBetweenSizeCheckInMillis(any(), any())).thenReturn(empty());
    localFileSystemMock = getLocalFileSystemMock();
    when(fileSystemProvider.connect()).thenReturn(localFileSystemMock);
    when(pollContext.accept(any())).then((Answer<PollContext.PollItemStatus>) invocationOnMock -> {
      Consumer<PollContext.PollItem> pollItemConsumer = (Consumer<PollContext.PollItem>) invocationOnMock.getArguments()[0];
      PollContext.PollItem pollItem = new RejectPollItem();
      pollItemConsumer.accept(pollItem);
      directoryListener.onRejectedItem(((RejectPollItem) pollItem).getResult(), mock(SourceCallbackContext.class));
      return SOURCE_STOPPING;
    });
    directoryListener.onStart(mock(SourceCallback.class));
  }

  private List<Result<InputStream, LocalFileAttributes>> getFileStreams(List<LocalFileAttributes> localFileAttributesList)
      throws IllegalAccessException {
    List<Result<InputStream, LocalFileAttributes>> fileStreams = new LinkedList<>();
    for (LocalFileAttributes localFileAttributes : localFileAttributesList) {
      fileStreams.add(createMockResult(localFileAttributes));
    }
    return fileStreams;
  }

  private List<LocalFileAttributes> getLocalFileAttributesList(BasicFileAttributes basicFileAttributes)
      throws IllegalAccessException {
    List<LocalFileAttributes> localFileAttributesList = new LinkedList<>();
    for (int i = 0; i < AMOUNT_OF_MOCK_RESULTS; i++) {
      String fileName = "test_file_" + i + ".txt";
      LocalFileAttributes localFileAttributes = getLocalFileAttributes(fileName, basicFileAttributes);
      localFileAttributesList.add(localFileAttributes);
    }
    return localFileAttributesList;
  }

  private LocalFileSystem getLocalFileSystemMock() throws IllegalAccessException {
    LocalFileSystem localFileSystem = mock(LocalFileSystem.class);
    when(localFileSystem.getBasePath()).thenReturn(".");
    return localFileSystem;
  }

  private Result<InputStream, LocalFileAttributes> createMockResult(LocalFileAttributes attributes)
      throws IllegalAccessException {
    return Result.<InputStream, LocalFileAttributes>builder().output(createMockedInputStream())
        .attributes(attributes).build();
  }

  private InputStream createMockedInputStream() {
    return mock(InputStream.class);
  }

  private BasicFileAttributes getBasicFileAttributesMock() {
    BasicFileAttributes basicFileAttributes = mock(BasicFileAttributes.class);
    FileTime now = FileTime.from(Instant.now());
    when(basicFileAttributes.lastModifiedTime()).thenReturn(now);
    when(basicFileAttributes.creationTime()).thenReturn(now);
    when(basicFileAttributes.lastAccessTime()).thenReturn(now);
    return basicFileAttributes;
  }

  private LocalFileAttributes getLocalFileAttributes(final String fileName, BasicFileAttributes basicFileAttributes)
      throws IllegalAccessException {
    LocalFileAttributes attributes = new LocalFileAttributes(Paths.get(FILE_PATH + "/" + fileName), basicFileAttributes);
    FieldUtils.writeField(attributes, "fileName", fileName, true);
    FieldUtils.writeField(attributes, "directory", false, true);
    return attributes;
  }

  @Test
  public void resultsAreClosedWhenSourceIsStopping() throws Exception {
    BasicFileAttributes basicFileAttributes = getBasicFileAttributesMock();
    when(basicFileAttributes.size()).thenReturn(100L);
    List<Result<InputStream, LocalFileAttributes>> fileStreams = getFileStreams(getLocalFileAttributesList(basicFileAttributes));
    when(localFileSystemMock.list(any(), any(), anyBoolean(), any(), any(), any())).thenReturn(fileStreams);

    directoryListener.poll(pollContext);
    assertAllStreamsAreClosed(fileStreams);
  }

  @Test
  public void filesAreProcessOneTime() throws Exception {
    BasicFileAttributes basicFileAttributes = getBasicFileAttributesMock();
    when(basicFileAttributes.size()).thenReturn(100L);
    List<Result<InputStream, LocalFileAttributes>> fileStreams = getFileStreams(getLocalFileAttributesList(basicFileAttributes));
    when(localFileSystemMock.list(any(), any(), anyBoolean(), any(), any(), any())).thenReturn(fileStreams);

    when(config.getTimeBetweenSizeCheckInMillis(any(), any())).thenReturn(Optional.of(25L));

    directoryListener.poll(pollContext);
    assertAllStreamsAreClosed(fileStreams);
  }

  @Test
  public void allFilesAreAttemptedToBeProcessedAtleastThreeTimes() throws Exception {
    Logger mockLogger = mock(Logger.class);
    Field loggerField = DirectoryListener.class.getDeclaredField("LOGGER");
    loggerField.setAccessible(true);

    // Remove the 'final' modifier
    Field modifiersField = Field.class.getDeclaredField("modifiers");
    modifiersField.setAccessible(true);
    modifiersField.setInt(loggerField, loggerField.getModifiers() & ~Modifier.FINAL);

    // Set the static field to the mock
    loggerField.set(null, mockLogger);

    BasicFileAttributes basicFileAttributes = getBasicFileAttributesMock();
    when(basicFileAttributes.size()).thenReturn(100L);
    List<Result<InputStream, LocalFileAttributes>> fileStreams =
        getFileStreams(getLocalFileAttributesList(basicFileAttributes));
    BasicFileAttributes basicFileAttributesWithDifferentSize = getBasicFileAttributesMock();
    when(basicFileAttributesWithDifferentSize.size()).thenReturn(200L);
    List<Result<InputStream, LocalFileAttributes>> fileStreamsWithDifferentSizes =
        getFileStreams(getLocalFileAttributesList(basicFileAttributesWithDifferentSize));
    when(localFileSystemMock.list(any(), any(), anyBoolean(), any(), any(), any())).thenReturn(fileStreams)
        .thenReturn(fileStreamsWithDifferentSizes);

    when(config.getTimeBetweenSizeCheckInMillis(any(), any())).thenReturn(Optional.of(25L));

    directoryListener.poll(pollContext);

    verify(mockLogger, times(30)).warn(anyString(), any(String.class));
  }

  private void assertAllStreamsAreClosed(List<Result<InputStream, LocalFileAttributes>> streams) throws Exception {
    for (Result<InputStream, LocalFileAttributes> result : streams) {
      assertStreamIsClosed(result);
    }
  }

  private void assertStreamIsClosed(Result<InputStream, LocalFileAttributes> result) throws Exception {
    verify(result.getOutput(), times(1)).close();
  }

  private static class RejectPollItem implements PollContext.PollItem {

    private Result result;

    public Result getResult() {
      return result;
    }

    @Override
    public SourceCallbackContext getSourceCallbackContext() {
      return mock(SourceCallbackContext.class);
    }

    @Override
    public PollContext.PollItem setResult(Result result) {
      this.result = result;
      return this;
    }

    @Override
    public PollContext.PollItem setWatermark(Serializable serializable) {
      return this;
    }

    @Override
    public PollContext.PollItem setId(String s) {
      return this;
    }
  }

}
