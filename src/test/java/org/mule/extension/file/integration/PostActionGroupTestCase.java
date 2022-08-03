/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.extension.file.integration;

import static org.mule.extension.file.AllureConstants.FileFeature.FILE_EXTENSION;
import org.mule.extension.file.internal.source.PostActionGroup;
import org.mule.tck.junit4.AbstractMuleTestCase;
import org.mule.tck.size.SmallTest;

import io.qameta.allure.Description;
import io.qameta.allure.Feature;
import org.junit.Test;

@SmallTest
@Feature(FILE_EXTENSION)
public class PostActionGroupTestCase extends AbstractMuleTestCase {

  @Test
  @Description("tests all the valid states of post action parameters")
  public void validAction() {
    new PostActionGroup(true, null, null, true, false).validateSelf();
    new PostActionGroup(true, null, null, false, false).validateSelf();
    new PostActionGroup(false, "someDir", null, false, false).validateSelf();
    new PostActionGroup(false, "someDir", "thisone.txt", false, false).validateSelf();
    new PostActionGroup(false, null, "thisone.txt", false, false).validateSelf();
  }

  @Test(expected = IllegalArgumentException.class)
  @Description("verifies that autoDelete and moveToDirectory cannot be set at the same time")
  public void deleteAndMove() {
    new PostActionGroup(true, "someDir", null, true, false).validateSelf();
  }

  @Test(expected = IllegalArgumentException.class)
  @Description("verifies that autoDelete and renameTo cannot be set at the same time")
  public void deleteAndRename() {
    new PostActionGroup(true, null, "thisone.txt", true, false).validateSelf();
  }
}
