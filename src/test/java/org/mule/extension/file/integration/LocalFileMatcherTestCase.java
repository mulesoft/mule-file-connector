/*
 * Copyright 2023 Salesforce, Inc. All rights reserved.
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.extension.file.integration;

import static org.mockito.Mockito.when;
import static org.mule.extension.file.common.api.matcher.MatchPolicy.INCLUDE;
import static org.mule.extension.file.common.api.matcher.MatchPolicy.REQUIRE;
import static org.mule.extension.file.AllureConstants.FileFeature.FILE_EXTENSION;
import org.mule.extension.file.api.LocalFileAttributes;
import org.mule.extension.file.api.LocalFileMatcher;
import org.mule.test.extension.file.common.FileMatcherContractTestCase;

import java.time.LocalDateTime;

import org.junit.Before;
import org.junit.Test;
import io.qameta.allure.Feature;

@Feature(FILE_EXTENSION)
public class LocalFileMatcherTestCase
    extends FileMatcherContractTestCase<LocalFileMatcher, LocalFileAttributes> {

  private static final LocalDateTime CREATION_TIME = LocalDateTime.of(1983, 4, 20, 21, 15);
  private static final LocalDateTime MODIFIED_TIME = LocalDateTime.of(2011, 2, 5, 22, 00);
  private static final LocalDateTime ACCESSED_TIME = LocalDateTime.of(2015, 4, 20, 00, 00);

  @Override
  protected LocalFileMatcher createPredicateBuilder() {
    return new LocalFileMatcher();
  }

  @Override
  protected Class<LocalFileAttributes> getFileAttributesClass() {
    return LocalFileAttributes.class;
  }

  @Before
  @Override
  public void before() {
    super.before();
    when(attributes.getCreationTime()).thenReturn(CREATION_TIME);
    when(attributes.getLastModifiedTime()).thenReturn(MODIFIED_TIME);
    when(attributes.getLastAccessTime()).thenReturn(ACCESSED_TIME);
  }

  @Test
  public void matchesAll() {
    builder.setFilenamePattern("glob:*.{java, js}").setPathPattern("glob:**.{java, js}")
        .setCreatedSince(LocalDateTime.of(1980, 1, 1, 0, 0))
        .setCreatedUntil(LocalDateTime.of(1990, 1, 1, 0, 0))
        .setUpdatedSince(LocalDateTime.of(2010, 9, 24, 0, 0))
        .setUpdatedUntil(LocalDateTime.of(2013, 11, 3, 6, 0))
        .setAccessedSince(LocalDateTime.of(2013, 11, 3, 0, 0))
        .setAccessedUntil(LocalDateTime.of(2015, 4, 20, 0, 0))
        .setRegularFiles(REQUIRE)
        .setDirectories(INCLUDE)
        .setSymLinks(INCLUDE)
        .setMinSize(1L)
        .setMaxSize(1024L);

    assertMatch();
  }

  @Test
  public void createdSince() {
    builder.setCreatedSince(LocalDateTime.of(1980, 1, 1, 0, 0));
    assertMatch();
  }

  @Test
  public void createdUntil() {
    builder.setCreatedUntil(LocalDateTime.of(1990, 1, 1, 0, 0));
    assertMatch();
  }

  @Test
  public void rejectCreatedSince() {
    builder.setCreatedSince(LocalDateTime.of(1984, 1, 1, 0, 0));
    assertReject();
  }

  @Test
  public void rejectCreatedUntil() {
    builder.setCreatedUntil(LocalDateTime.of(1982, 4, 2, 0, 0));
    assertReject();
  }

  @Test
  public void updateSince() {
    builder.setUpdatedSince(LocalDateTime.of(2010, 9, 24, 0, 0));
    assertMatch();
  }

  @Test
  public void updatedUntil() {
    builder.setUpdatedUntil(LocalDateTime.of(2013, 11, 3, 6, 0));
    assertMatch();
  }

  @Test
  public void rejectUpdatedSince() {
    builder.setUpdatedSince(LocalDateTime.of(2015, 1, 1, 0, 0));
    assertReject();
  }

  @Test
  public void rejectUpdatedUntil() {
    builder.setUpdatedUntil(LocalDateTime.of(2010, 9, 24, 0, 0));
    assertReject();
  }

  @Test
  public void accessedSince() {
    builder.setAccessedSince(LocalDateTime.of(2013, 11, 3, 0, 0));
    assertMatch();
  }

  @Test
  public void accessedUntil() {
    builder.setAccessedUntil(LocalDateTime.of(2015, 4, 20, 0, 0));
    assertMatch();
  }

  @Test
  public void rejectAccessedSince() {
    builder.setAccessedSince(LocalDateTime.of(2016, 1, 1, 0, 0));
    assertReject();
  }

  @Test
  public void rejectAccessedUntil() {
    builder.setUpdatedUntil(LocalDateTime.of(2010, 9, 24, 0, 0));
    assertReject();
  }
}
