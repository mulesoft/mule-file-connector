/*
 * Copyright 2023 Salesforce, Inc. All rights reserved.
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
 * A {@link Comparator} for comparing files by their size.
 *
 * @since 1.4.0
 */
public class SizeComparator implements Comparator<Result<InputStream, LocalFileAttributes>> {

  /**
   * Compares the size of the files found in the attributes
   *
   */
  @Override
  public int compare(Result<InputStream, LocalFileAttributes> o1, Result<InputStream, LocalFileAttributes> o2) {
    long size1 = o1.getAttributes().get().getSize();
    long size2 = o2.getAttributes().get().getSize();
    return Long.compare(size1, size2);
  }
}
