/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.extension.file.api.subset;

import org.mule.extension.file.api.LocalFileAttributes;
import org.mule.runtime.extension.api.runtime.operation.Result;

import java.io.InputStream;
import java.util.Comparator;

/**
 * An enum for users to choose the sorting criteria.
 *
 * @since 1.4.0
 */
public enum ListComparator {

  /**
   * Sort by name of the file.
   */
  ALPHABETICALLY(new AlphabeticalComparator()),
  /**
   * Sort by date modified of the file.
   */
  DATE_MODIFIED(new DateModifiedComparator()),
  /**
   * Sort by date created of the file.
   */
  DATE_CREATED(new DateCreatedComparator()),
  /**
   * Sort by size of the file.
   */
  SIZE(new SizeComparator()),
  /**
   * Sort by path of the file.
   */
  PATH(new PathComparator());

  private final Comparator<Result<InputStream, LocalFileAttributes>> comparator;

  ListComparator(Comparator<Result<InputStream, LocalFileAttributes>> comparator) {
    this.comparator = comparator;
  }

  public Comparator<Result<InputStream, LocalFileAttributes>> getComparator() {
    return comparator;
  }
}
