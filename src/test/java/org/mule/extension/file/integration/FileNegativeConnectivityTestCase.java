/*
 * Copyright 2023 Salesforce, Inc. All rights reserved.
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.extension.file.integration;

import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.mule.extension.file.AllureConstants.FileFeature.FILE_EXTENSION;
import static org.mule.extension.file.common.api.exceptions.FileError.FILE_DOESNT_EXIST;
import static org.mule.extension.file.common.api.exceptions.FileError.FILE_IS_NOT_DIRECTORY;
import static org.mule.extension.file.common.api.exceptions.FileError.ILLEGAL_PATH;
import static org.mule.functional.junit4.matchers.ThrowableCauseMatcher.hasCause;
import static org.mule.tck.junit4.matcher.ErrorTypeMatcher.errorType;
import org.mule.runtime.api.connection.ConnectionException;
import org.mule.tck.junit4.rule.SystemProperty;
import org.mule.tck.util.TestConnectivityUtils;

import java.io.IOException;

import io.qameta.allure.Feature;
import io.qameta.allure.Story;
import org.hamcrest.Matcher;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

@Feature(FILE_EXTENSION)
@Story("Negative Connectivity Testing")
public class FileNegativeConnectivityTestCase extends FileConnectorTestCase {

  private static final Matcher<Exception> CONNECTION_EXCEPTION_MATCHER =
      is(allOf(instanceOf(ConnectionException.class), hasCause(instanceOf(ConnectionException.class))));
  private TestConnectivityUtils utils;

  @Rule
  public SystemProperty rule = TestConnectivityUtils.disableAutomaticTestConnectivity();

  @Override
  protected String getConfigFile() {
    return "file-negative-connectivity-test-config.xml";
  }

  @Before
  public void createUtils() {
    utils = new TestConnectivityUtils(registry);
  }

  @Test
  public void configFileDoesntExist() {
    utils.assertFailedConnection("configFileDoesntExist", CONNECTION_EXCEPTION_MATCHER, is(errorType(FILE_DOESNT_EXIST)));
  }

  @Test
  public void configFileIsNotDirectory() throws IOException {
    temporaryFolder.newFile("file.zip");
    utils.assertFailedConnection("configFileIsNotDirectory", CONNECTION_EXCEPTION_MATCHER, is(errorType(FILE_IS_NOT_DIRECTORY)));
  }

  @Test
  public void configIllegalPath() throws IOException {
    System.clearProperty("user.home");
    utils.assertFailedConnection("configIllegalPath", CONNECTION_EXCEPTION_MATCHER, is(errorType(ILLEGAL_PATH)));
  }

}
