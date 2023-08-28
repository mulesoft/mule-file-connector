/*
 * Copyright 2023 Salesforce, Inc. All rights reserved.
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.extension.file.internal;

import org.mule.extension.file.api.LocalFileAttributes;
import org.mule.extension.file.common.api.AbstractFileSystem;
import org.mule.extension.file.common.api.FileAttributes;
import org.mule.extension.file.common.api.FileSystem;
import org.mule.extension.file.common.api.command.CopyCommand;
import org.mule.extension.file.common.api.command.CreateDirectoryCommand;
import org.mule.extension.file.common.api.command.DeleteCommand;
import org.mule.extension.file.common.api.command.ListCommand;
import org.mule.extension.file.common.api.command.MoveCommand;
import org.mule.extension.file.common.api.command.ReadCommand;
import org.mule.extension.file.common.api.command.RenameCommand;
import org.mule.extension.file.common.api.command.WriteCommand;
import org.mule.extension.file.common.api.lock.PathLock;
import org.mule.extension.file.internal.command.LocalCopyCommand;
import org.mule.extension.file.internal.command.LocalCreateDirectoryCommand;
import org.mule.extension.file.internal.command.LocalDeleteCommand;
import org.mule.extension.file.internal.command.LocalListCommand;
import org.mule.extension.file.internal.command.LocalMoveCommand;
import org.mule.extension.file.internal.command.LocalReadCommand;
import org.mule.extension.file.internal.command.LocalRenameCommand;
import org.mule.extension.file.internal.command.LocalWriteCommand;
import org.mule.extension.file.internal.lock.FileChannelPathLock;

import java.nio.channels.FileChannel;
import java.nio.file.Path;

/**
 * Implementation of {@link FileSystem} for file systems mounted on the host operating system.
 * <p>
 * Whenever the {@link FileSystem} contract refers to locking, this implementation will resolve through a {@link FileChannelPathLock},
 * which produces file system level locks which rely on the host operating system.
 * <p>
 * Also, for any method returning {@link FileAttributes} instances, a {@link LocalFileAttributes} will be used.
 *
 * @since 1.0
 */
public class LocalFileSystem extends AbstractFileSystem<LocalFileAttributes> {

  private final CopyCommand copyCommand;
  private final CreateDirectoryCommand createDirectoryCommand;
  private final DeleteCommand deleteCommand;
  private final ListCommand<LocalFileAttributes> listCommand;
  private final MoveCommand moveCommand;
  private final ReadCommand<LocalFileAttributes> readCommand;
  private final RenameCommand renameCommand;
  private final WriteCommand writeCommand;

  /**
   * Creates a new instance
   */
  public LocalFileSystem(String basePath) {
    super(basePath);

    copyCommand = new LocalCopyCommand(this);
    createDirectoryCommand = new LocalCreateDirectoryCommand(this);
    deleteCommand = new LocalDeleteCommand(this);
    moveCommand = new LocalMoveCommand(this);
    readCommand = new LocalReadCommand(this);
    listCommand = new LocalListCommand(this, (LocalReadCommand) readCommand);
    renameCommand = new LocalRenameCommand(this);
    writeCommand = new LocalWriteCommand(this);
  }

  @Override
  protected CopyCommand getCopyCommand() {
    return copyCommand;
  }

  @Override
  public CreateDirectoryCommand getCreateDirectoryCommand() {
    return createDirectoryCommand;
  }

  @Override
  protected DeleteCommand getDeleteCommand() {
    return deleteCommand;
  }

  @Override
  protected ListCommand getListCommand() {
    return listCommand;
  }

  @Override
  protected MoveCommand getMoveCommand() {
    return moveCommand;
  }

  @Override
  protected ReadCommand getReadCommand() {
    return readCommand;
  }

  @Override
  protected RenameCommand getRenameCommand() {
    return renameCommand;
  }

  @Override
  protected WriteCommand getWriteCommand() {
    return writeCommand;
  }

  @Override
  protected PathLock createLock(Path path) {
    throw new UnsupportedOperationException("Use lock(Path, FileChannel) instead");
  }

  public PathLock lock(Path path, FileChannel channel) {
    final FileChannelPathLock lock = new FileChannelPathLock(path, channel);
    acquireLock(lock);

    return lock;
  }

  /**
   * No-op implementation.
   */
  @Override
  public void changeToBaseDir() {}
}
