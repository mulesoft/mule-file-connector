/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.extension.file.api.subset;

import java.util.Comparator;

public enum ListComparator {

  ALPHABETICALLY(new AlphabeticalComparator()), DATE_MODIFIED(new DateModifiedComparator()), DATE_CREATED(
      new DateCreatedComparator()), SIZE(new SizeComparator()), PATH(new PathComparator());

  private Comparator comparator;

  ListComparator(Comparator comparator) {
    this.comparator = comparator;
  }

  public Comparator getComparator() {
    return comparator;
  }
}
