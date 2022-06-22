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
 * A {@link Comparator} for comparing files by their name.
 *
 * @since 1.4.0
 */
public class AlphabeticalComparator implements Comparator<Result<InputStream, LocalFileAttributes>> {

  @Override
  public int compare(Result<InputStream, LocalFileAttributes> o1, Result<InputStream, LocalFileAttributes> o2) {
    return o1.getAttributes().get().getName().compareTo(o2.getAttributes().get().getName());
  }
}
