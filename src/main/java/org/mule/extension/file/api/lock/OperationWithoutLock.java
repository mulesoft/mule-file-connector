/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.extension.file.api.lock;

import java.sql.Time;
import java.util.concurrent.TimeUnit;

public class OperationWithoutLock implements OperationLockMode {

  @Override
  public boolean willLock() {
    return false;
  }

  @Override
  public Long getLockTimeout() {
    return -1L;
  }

  @Override
  public TimeUnit getLockTimeoutUnit() {
    return TimeUnit.MILLISECONDS;
  }

}
