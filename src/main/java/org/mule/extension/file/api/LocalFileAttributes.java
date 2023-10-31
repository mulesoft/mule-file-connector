/*
 * Copyright 2023 Salesforce, Inc. All rights reserved.
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.extension.file.api;

import static java.lang.String.format;
import static org.mule.runtime.api.i18n.I18nMessageFactory.createStaticMessage;
import org.mule.extension.file.common.api.AbstractFileAttributes;
import org.mule.extension.file.common.api.FileAttributes;
import org.mule.extension.file.common.api.exceptions.FileAccessDeniedException;
import org.mule.runtime.api.exception.MuleRuntimeException;
import org.mule.runtime.extension.api.annotation.param.Parameter;

import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Implementation of {@link FileAttributes} for files obtained from a local file system.
 *
 * @since 1.0
 */
public class LocalFileAttributes extends AbstractFileAttributes {

  @Parameter
  private LocalDateTime lastModifiedTime;

  @Parameter
  private LocalDateTime lastAccessTime;

  @Parameter
  private LocalDateTime creationTime;

  @Parameter
  private long size;

  @Parameter
  private boolean regularFile;

  @Parameter
  private boolean directory;

  @Parameter
  private boolean symbolicLink;

  private static final Path DEFAULT_PATH = Paths.get("/default/path");

  /**
   * {@inheritDoc}
   */
  public LocalFileAttributes(Path path) {
    super(path);
    initAttributes(getAttributes(path));
  }

  public LocalFileAttributes(Path path, BasicFileAttributes attributes) {
    super(path);
    initAttributes(attributes);
  }

  public LocalFileAttributes() {
    super(DEFAULT_PATH);
    this.lastModifiedTime = LocalDateTime.now();
    this.lastAccessTime = LocalDateTime.now();
    this.creationTime = LocalDateTime.now();
    this.size = 0;
  }


  protected void initAttributes(BasicFileAttributes attributes) {
    this.lastModifiedTime = asDateTime(attributes.lastModifiedTime());
    this.lastAccessTime = asDateTime(attributes.lastAccessTime());
    this.creationTime = asDateTime(attributes.creationTime());
    this.size = attributes.size();
    this.regularFile = attributes.isRegularFile();
    this.directory = attributes.isDirectory();
    this.symbolicLink = Files.isSymbolicLink(Paths.get(getPath()));
  }

  /**
   * @return The last time the file was modified
   */
  public LocalDateTime getLastModifiedTime() {
    return lastModifiedTime;
  }

  public void setLastModifiedTime(LocalDateTime lastModifiedTime) {
    this.lastModifiedTime = lastModifiedTime;
  }


  /**
   * @return The last time the file was accessed
   */
  public LocalDateTime getLastAccessTime() {
    return lastAccessTime;
  }

  public void setLastAccessTime(LocalDateTime lastAccessTime) {
    this.lastAccessTime = lastAccessTime;
  }

  /**
   * @return the time at which the file was created
   */
  public LocalDateTime getCreationTime() {
    return creationTime;
  }

  public void setCreationTime(LocalDateTime creationTime) {
    this.creationTime = creationTime;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public long getSize() {
    return size;
  }

  public void setSize(long size) {
    this.size = size;
  }


  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isRegularFile() {
    return regularFile;
  }

  public void setRegularFile(boolean isRegularFile) {
    this.regularFile = isRegularFile;
  }


  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isDirectory() {
    return directory;
  }

  public void setDirectory(boolean directory) {
    this.directory = directory;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isSymbolicLink() {
    return symbolicLink;
  }

  public void setSymbolicLink(boolean symbolicLink) {
    this.symbolicLink = symbolicLink;
  }

  private BasicFileAttributes getAttributes(Path path) {
    try {
      return Files.readAttributes(path, BasicFileAttributes.class);
    } catch (AccessDeniedException e) {
      throw new FileAccessDeniedException(format("Access to path '%s' denied by the operating system", path), e);
    } catch (Exception e) {
      throw new MuleRuntimeException(createStaticMessage("Could not read attributes for file " + path), e);
    }
  }

  private LocalDateTime asDateTime(FileTime fileTime) {
    return LocalDateTime.ofInstant(fileTime.toInstant(), ZoneId.systemDefault());
  }
}
