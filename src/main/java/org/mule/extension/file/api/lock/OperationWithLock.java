/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.extension.file.api.lock;

import org.mule.runtime.extension.api.annotation.param.Optional;
import org.mule.runtime.extension.api.annotation.param.Parameter;
import org.mule.runtime.extension.api.annotation.param.display.DisplayName;
import org.mule.runtime.extension.api.annotation.param.display.Summary;

import java.util.concurrent.TimeUnit;

public class OperationWithLock implements OperationLockMode {

  @Parameter
  @Summary("Sets the for how long the operation will try to lock the file. Works in tandem with the Lock Timeout Unit parameter. By default it is set to 0, which means it will only try once to lock the file.")
  @DisplayName("Lock Timeout")
  @Optional()
  private Long lockTimeout;

  @Parameter
  @Summary("Time Unit that determines how to interpret the Lock Timeout parameter. By default it is MILLISECONDS. Can be one of: NANOSECONDS, MICROSECONDS, MILLISECONDS, SECONDS, MINUTES, HOURS, DAYS.")
  @DisplayName("Lock Timeout Unit")
  @Optional()
  private TimeUnit lockTimeoutUnit;

  @Override
  public boolean willLock() {
    return true;
  }

  public Long getLockTimeout() {
    return lockTimeout;
  }

  public TimeUnit getLockTimeoutUnit() {
    return lockTimeoutUnit;
  }

}
