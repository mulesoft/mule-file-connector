/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.extension.file;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mule.extension.file.AllureConstants.FileFeature.FILE_EXTENSION;

import java.io.File;

import io.qameta.allure.Feature;

@Feature(FILE_EXTENSION)
public class FileMoveTestCase extends FileCopyTestCase {

  @Override
  protected String[] getConfigFiles() {
    return new String[] {"file-move-config.xml", "file-read-config.xml"};
  }

  @Override
  protected String getFlowName() {
    return "move";
  }

  @Override
  protected void assertCopy(String target) throws Exception {
    super.assertCopy(target);
    assertThat(new File(sourcePath).exists(), is(false));
  }
}
