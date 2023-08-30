/*
 * Copyright 2023 Salesforce, Inc. All rights reserved.
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.extension.file.internal;

import static java.nio.file.StandardOpenOption.READ;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import org.mule.extension.file.api.LocalFileAttributes;
import org.mule.extension.file.common.api.lock.NullPathLock;
import org.mule.extension.file.common.api.lock.PathLock;
import org.mule.tck.junit4.AbstractMuleTestCase;

import java.io.File;
import java.nio.channels.FileChannel;
import java.nio.file.Path;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class FileInputStreamTestCase extends AbstractMuleTestCase {

  @Rule
  public TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Test
  public void channelIsClosedOnPathLockRelease() throws Exception {
    File file = temporaryFolder.newFile();

    Path path = file.toPath();
    LocalFileAttributes fileAttributes = new LocalFileAttributes(file.toPath());
    PathLock pathLock = new NullPathLock(path);
    FileChannel channel = FileChannel.open(path, READ);

    FileChannel spyFileChannel = spy(channel);

    FileInputStream fileInputStream = new FileInputStream(spyFileChannel, pathLock, path, 10L, fileAttributes);
    fileInputStream.close();

    verify(spyFileChannel).close();
  }

}
