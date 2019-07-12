/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.extension.file.api;

import static java.time.LocalDateTime.now;

import org.mule.runtime.extension.api.annotation.Alias;
import org.mule.runtime.extension.api.annotation.dsl.xml.TypeDsl;
import org.mule.runtime.extension.api.annotation.param.Parameter;
import org.mule.runtime.extension.api.annotation.param.Optional;
import org.mule.extension.file.common.api.matcher.FileMatcher;
import org.mule.runtime.extension.api.annotation.param.display.Example;
import org.mule.runtime.extension.api.annotation.param.display.Summary;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

/**
 * A specialization of {@link FileMatcher} used to do assertions on files stored on a local file system. The file's
 * properties are to be represented on an instance of {@link LocalFileAttributes}
 * <p>
 * It adds capabilities to consider creation, update and access timestamps.
 *
 * @since 1.0
 */
@Alias("matcher")
@TypeDsl(allowTopLevelDefinition = true)
public class LocalFileMatcher extends FileMatcher<LocalFileMatcher, LocalFileAttributes> {

  /**
   * Files created before this date are rejected.
   */
  @Parameter
  @Summary("Files created before this date are rejected.")
  @Optional
  private LocalDateTime createdSince;

  /**
   * Files created after this date are rejected
   */
  @Parameter
  @Summary("Files created after this date are rejected")
  @Optional
  private LocalDateTime createdUntil;

  /**
   * Files modified before this date are rejected
   */
  @Parameter
  @Summary("Files modified before this date are rejected")
  @Optional
  private LocalDateTime updatedSince;

  /**
   * Files modified after this date are rejected
   */
  @Parameter
  @Summary("Files modified after this date are rejected")
  @Optional
  private LocalDateTime updatedUntil;

  /**
   * Files which were last accessed before this date are rejected
   */
  @Parameter
  @Summary("Files which were last accessed before this date are rejected")
  @Optional
  private LocalDateTime accessedSince;

  /**
   * Files which were last accessed after this date are rejected
   */
  @Parameter
  @Summary("Files which were last accessed after this date are rejected")
  @Optional
  private LocalDateTime accessedUntil;

  /**
   * Minimum time that should have passed since a file was updated to not be rejected. This attribute works in tandem with {@link #timeUnit}.
   */
  @Parameter
  @Summary("Minimum time that should have passed since a file was updated to not be rejected. This attribute works in tandem with timeUnit.")
  @Example("10000")
  @Optional
  private Long notUpdatedInTheLast;

  /**
   * Maximum time that should have passed since a file was updated to not be rejected. This attribute works in tandem with {@link #timeUnit}.
   */
  @Parameter
  @Summary("Maximum time that should have passed since a file was updated to not be rejected. This attribute works in tandem with timeUnit.")
  @Example("10000")
  @Optional
  private Long updatedInTheLast;

  /**
   * A {@link TimeUnit} which qualifies the {@link #updatedInTheLast} and the {@link #notUpdatedInTheLast} attributes.
   * <p>
   * Defaults to {@code MILLISECONDS}
   */
  @Parameter
  @Summary("Time unit to be used to interpret the parameters 'notUpdatedInTheLast' and 'updatedInTheLast'")
  @Optional(defaultValue = "MILLISECONDS")
  private TimeUnit timeUnit;

  @Override
  protected Predicate<LocalFileAttributes> addConditions(Predicate<LocalFileAttributes> predicate) {
    if (createdSince != null) {
      predicate = predicate.and(attributes -> FILE_TIME_SINCE.apply(createdSince, attributes.getCreationTime()));
    }

    if (createdUntil != null) {
      predicate = predicate.and(attributes -> FILE_TIME_UNTIL.apply(createdUntil, attributes.getCreationTime()));
    }

    if (updatedSince != null) {
      predicate = predicate.and(attributes -> FILE_TIME_SINCE.apply(updatedSince, attributes.getLastModifiedTime()));
    }

    if (updatedUntil != null) {
      predicate = predicate.and(attributes -> FILE_TIME_UNTIL.apply(updatedUntil, attributes.getLastModifiedTime()));
    }

    if (accessedSince != null) {
      predicate = predicate.and(attributes -> FILE_TIME_SINCE.apply(accessedSince, attributes.getLastAccessTime()));
    }

    if (accessedUntil != null) {
      predicate = predicate.and(attributes -> FILE_TIME_SINCE.apply(accessedUntil, attributes.getLastAccessTime()));
    }

    // We want to make sure that the same time is used when comparing multiple files consecutively.
    LocalDateTime now = now();

    if (notUpdatedInTheLast != null) {
      predicate = predicate.and(attributes -> FILE_TIME_UNTIL.apply(minusTime(now, notUpdatedInTheLast, timeUnit),
                                                                    attributes.getLastModifiedTime()));
    }

    if (updatedInTheLast != null) {
      predicate = predicate
          .and(attributes -> FILE_TIME_SINCE.apply(minusTime(now, updatedInTheLast, timeUnit), attributes.getLastModifiedTime()));
    }
    return predicate;
  }

  private LocalDateTime minusTime(LocalDateTime localDateTime, Long time, TimeUnit timeUnit) {
    return localDateTime.minus(getTimeInMillis(time, timeUnit), ChronoUnit.MILLIS);
  }

  private long getTimeInMillis(Long time, TimeUnit timeUnit) {
    return timeUnit.toMillis(time);
  }

  public LocalFileMatcher setCreatedSince(LocalDateTime createdSince) {
    this.createdSince = createdSince;
    return this;
  }

  public LocalFileMatcher setCreatedUntil(LocalDateTime createdUntil) {
    this.createdUntil = createdUntil;
    return this;
  }

  public LocalFileMatcher setUpdatedSince(LocalDateTime updatedSince) {
    this.updatedSince = updatedSince;
    return this;
  }

  public LocalFileMatcher setUpdatedUntil(LocalDateTime updatedUntil) {
    this.updatedUntil = updatedUntil;
    return this;
  }

  public LocalFileMatcher setAccessedSince(LocalDateTime accessedSince) {
    this.accessedSince = accessedSince;
    return this;
  }

  public LocalFileMatcher setAccessedUntil(LocalDateTime accessedUntil) {
    this.accessedUntil = accessedUntil;
    return this;
  }

  public void setTimeUnit(TimeUnit timeUnit) {
    this.timeUnit = timeUnit;
  }

  public void setUpdatedInTheLast(Long updatedInTheLast) {
    this.updatedInTheLast = updatedInTheLast;
  }

  public void setNotUpdatedInTheLast(Long notUpdatedInTheLast) {
    this.notUpdatedInTheLast = notUpdatedInTheLast;
  }

  public LocalDateTime getCreatedSince() {
    return createdSince;
  }

  public LocalDateTime getCreatedUntil() {
    return createdUntil;
  }

  public LocalDateTime getUpdatedSince() {
    return updatedSince;
  }

  public LocalDateTime getUpdatedUntil() {
    return updatedUntil;
  }

  public LocalDateTime getAccessedSince() {
    return accessedSince;
  }

  public LocalDateTime getAccessedUntil() {
    return accessedUntil;
  }

  public TimeUnit getTimeUnit() {
    return timeUnit;
  }

  public Long getUpdatedInTheLast() {
    return updatedInTheLast;
  }

  public Long getNotUpdatedInTheLast() {
    return notUpdatedInTheLast;
  }

}
