/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.extension.file;

import static org.mule.extension.file.AllureConstants.FileFeature.FILE_EXTENSION;
import static org.mule.runtime.core.api.util.UUID.getUUID;

import org.mule.functional.listener.ExceptionListener;
import org.mule.runtime.api.component.location.Location;
import org.mule.runtime.api.source.SchedulerMessageSource;
import org.mule.tck.probe.PollingProber;
import org.mule.tck.probe.Probe;

import org.junit.Test;

import java.io.File;

import io.qameta.allure.Description;
import io.qameta.allure.Feature;

@Feature(FILE_EXTENSION)
public class FileCommonUseCasesTestCase extends FileConnectorTestCase {

  private static final String LIST_AND_DELETE_FILES_FLOW = "listAndDeleteFiles";

  @Override
  protected String getConfigFile() {
    return "file-common-use-cases-config.xml";
  }

  @Description("List files from folder, process them and delete them. Single threaded to avoid file concurrency problems.")
  @Test
  public void fileProcessingSingleThreaded() throws Exception {
    ExceptionListener exceptionListener = new ExceptionListener(notificationListenerRegistry);

    File firstFile = createInputFile();
    File secondFile = createInputFile();

    SchedulerMessageSource schedulerMessageSource = (SchedulerMessageSource) locator
        .find(Location.builder().globalName(LIST_AND_DELETE_FILES_FLOW).addSourcePart().build()).get();
    schedulerMessageSource.start();

    new PollingProber(RECEIVE_TIMEOUT, 100).check(new Probe() {

      @Override
      public boolean isSatisfied() {
        return !firstFile.exists() && !secondFile.exists();
      }

      @Override
      public String describeFailure() {
        return "Some files were not consumed by the flow";
      }
    });
    exceptionListener.assertNotInvoked();
  }

  private File createInputFile() {
    File workingDirectory = new File(workingDir.getValue());
    File inputFolder = new File(workingDirectory, "input");
    return new File(inputFolder, getUUID());
  }

}
