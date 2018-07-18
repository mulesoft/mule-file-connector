/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.extension.file;


import static java.nio.file.StandardOpenOption.READ;
import static java.nio.file.StandardOpenOption.WRITE;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;
import org.mule.extension.file.api.LocalFileAttributes;
import org.mule.extension.file.common.api.FileAttributes;
import org.mule.extension.file.common.api.lock.PathLock;
import org.mule.extension.file.internal.FileInputStream;

import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class FileInputStreamTestCase {

  private static final long TIME_BETWEEN_SIZE_CHECK = 1000L;

  @Mock
  private PathLock pathLock;

  private FileChannel fileChannel;
  private FileAttributes fileAttributes;
  private Path path;

  @Before
  public void setUp() throws Exception {
    path = Paths.get(Thread.currentThread().getContextClassLoader().getResource("file-read-config.xml").toURI());
    fileAttributes = new LocalFileAttributes(path);
    fileChannel = FileChannel.open(path, READ, WRITE);

    when(pathLock.isLocked()).thenReturn(true);
    doAnswer(invocation -> {
      when(pathLock.isLocked()).thenReturn(false);
      return null;
    }).when(pathLock).release();
  }

  @Test
  public void readLockReleasedOnContentConsumed() throws Exception {
    FileInputStream inputStream = new FileInputStream(fileChannel, pathLock, path, TIME_BETWEEN_SIZE_CHECK, fileAttributes);

    verifyZeroInteractions(pathLock);
    assertThat(inputStream.isLocked(), is(true));
    verify(pathLock).isLocked();

    org.apache.commons.io.IOUtils.toString(inputStream, "UTF-8");

    verify(pathLock, times(1)).release();
    assertThat(inputStream.isLocked(), is(false));
  }

  @Test
  public void readLockReleasedOnEarlyClose() throws Exception {
    FileInputStream inputStream = new FileInputStream(fileChannel, pathLock, path, TIME_BETWEEN_SIZE_CHECK, fileAttributes);

    verifyZeroInteractions(pathLock);
    assertThat(inputStream.isLocked(), is(true));
    verify(pathLock).isLocked();

    inputStream.close();

    verify(pathLock, times(1)).release();
    assertThat(inputStream.isLocked(), is(false));
  }

}
