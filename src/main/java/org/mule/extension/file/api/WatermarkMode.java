/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.extension.file.api;

/**
 * Defines the strategy to be used to apply watermarking.
 *
 * @since 1.1
 */
public enum WatermarkMode {

  /**
   * Don't do any watermarking
   */
  DISABLED,

  /**
   * Do watermarking based on the file's modification time
   */
  MODIFIED_TIMESTAMP,

  /**
   * Do watermarking based on the file's creation time
   */
  CREATED_TIMESTAMP
}
