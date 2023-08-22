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
import static org.mockito.Matchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mule.runtime.extension.api.runtime.source.PollContext.PollItemStatus.SOURCE_STOPPING;

import org.apache.commons.lang3.reflect.FieldUtils;
import org.mockito.Mockito;
import org.mule.extension.file.api.LocalFileAttributes;
import org.mule.extension.file.internal.FileConnector;
import org.mule.extension.file.internal.LocalFileSystem;
import org.mule.extension.file.internal.source.DirectoryListener;
import org.mule.runtime.api.connection.ConnectionProvider;
import org.mule.runtime.extension.api.runtime.operation.Result;
import org.mule.runtime.extension.api.runtime.source.PollContext;
import org.mule.runtime.extension.api.runtime.source.SourceCallback;
import org.mule.runtime.extension.api.runtime.source.SourceCallbackContext;

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
  private LocalFileSystem localFileSystem = mock(LocalFileSystem.class);
  private List<Result<InputStream, LocalFileAttributes>> listResult;
  private PollContext pollContext = mock(PollContext.class);
  private DirectoryListener directoryListener;

  @Before
  public void setup() throws Exception {
    directoryListener = new DirectoryListener(config, fileSystemProvider);
    when(config.getTimeBetweenSizeCheckInMillis(anyLong(), any())).thenReturn(empty());
    when(fileSystemProvider.connect()).thenReturn(localFileSystem);
    setupListResult();
    when(localFileSystem.list(any(), any(), anyBoolean(), any(), any(), any())).thenReturn(listResult);
    when(localFileSystem.getBasePath()).thenReturn(".");
    when(pollContext.accept(any())).then((Answer<PollContext.PollItemStatus>) invocationOnMock -> {
      Consumer<PollContext.PollItem> pollItemConsumer = (Consumer<PollContext.PollItem>) invocationOnMock.getArguments()[0];
      PollContext.PollItem pollItem = new RejectPollItem();
      pollItemConsumer.accept(pollItem);
      directoryListener.onRejectedItem(((RejectPollItem) pollItem).getResult(), mock(SourceCallbackContext.class));
      return SOURCE_STOPPING;
    });
    directoryListener.onStart(mock(SourceCallback.class));
  }

  private void setupListResult() throws IllegalAccessException {
    listResult = new LinkedList<>();
    for (int i = 0; i < AMOUNT_OF_MOCK_RESULTS; i++) {
      listResult.add(createMockResult("test_file_" + i + ".txt"));
    }
  }

  private Result<InputStream, LocalFileAttributes> createMockResult(final String fileName) throws IllegalAccessException {
    return Result.<InputStream, LocalFileAttributes>builder().output(createMockedInputStream())
        .attributes(createMockedAttributes(fileName)).build();
  }

  private InputStream createMockedInputStream() {
    return mock(InputStream.class);
  }

  private LocalFileAttributes createMockedAttributes(final String fileName) throws IllegalAccessException {
    BasicFileAttributes basicFileAttributes = Mockito.mock(BasicFileAttributes.class);
    FileTime now = FileTime.from(Instant.now());
    when(basicFileAttributes.lastModifiedTime()).thenReturn(now);
    when(basicFileAttributes.creationTime()).thenReturn(now);
    when(basicFileAttributes.lastAccessTime()).thenReturn(now);

    LocalFileAttributes attributes = new LocalFileAttributes(Paths.get(FILE_PATH + "/" + fileName), basicFileAttributes);

    FieldUtils.writeField(attributes, "fileName", fileName, true);
    FieldUtils.writeField(attributes, "directory", false, true);

    return attributes;
  }

  @Test
  public void resultsAreClosedWhenSourceIsStopping() throws Exception {
    directoryListener.poll(pollContext);
    assertAllStreamsAreClosed();
  }

  @Test
  public void filesAreProcessOneTime() throws Exception {
    when(config.getTimeBetweenSizeCheckInMillis(anyLong(), any())).thenReturn(Optional.of(25L));
    directoryListener.poll(pollContext);
    assertAllStreamsAreClosed();
  }

  private void assertAllStreamsAreClosed() throws Exception {
    for (Result<InputStream, LocalFileAttributes> result : listResult) {
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
