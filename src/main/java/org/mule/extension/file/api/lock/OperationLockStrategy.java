/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.extension.file.api.lock;

import java.util.concurrent.TimeUnit;

public interface OperationLockStrategy {

  public static final Boolean NO_LOCK = false;
  public static final Boolean WITH_LOCK = true;

  public Long getLockTimeout();

  public TimeUnit getLockTimeoutUnit();

}
