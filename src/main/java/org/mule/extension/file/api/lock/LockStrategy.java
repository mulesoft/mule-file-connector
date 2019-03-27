/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.extension.file.api.lock;

import org.mule.extension.file.internal.FileOperations;

import java.util.concurrent.TimeUnit;

/**
 * Describes a locking strategy for the supported {@link FileOperations}. This interface is not responsible for
 * locking the file.
 *
 * since 2.0
 */
public interface LockStrategy {

  /**
   * @return the time the operation will spend trying to obtain the lock.
   */
  public long getLockTimeout();

  /**
   * @return the {@link TimeUnit} to interpret the Lock Timeout.
   */
  public TimeUnit getLockTimeoutUnit();

}
