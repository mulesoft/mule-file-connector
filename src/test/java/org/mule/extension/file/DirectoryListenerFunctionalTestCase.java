/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.extension.file;

import static java.nio.file.Files.setLastModifiedTime;
import static java.time.LocalDateTime.now;
import static java.time.ZoneId.systemDefault;
import static java.time.temporal.ChronoUnit.HOURS;
import static org.apache.commons.io.FileUtils.write;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mule.extension.file.AllureConstants.FileFeature.FILE_EXTENSION;
import static org.mule.tck.probe.PollingProber.check;
import static org.mule.tck.probe.PollingProber.checkNot;
import org.mule.extension.file.api.LocalFileAttributes;
import org.mule.extension.file.common.api.FileAttributes;
import org.mule.runtime.api.exception.MuleException;
import org.mule.runtime.api.lifecycle.Startable;
import org.mule.runtime.api.lifecycle.Stoppable;
import org.mule.runtime.api.message.Message;
import org.mule.runtime.api.util.Reference;
import org.mule.runtime.core.api.event.CoreEvent;
import org.mule.runtime.core.api.processor.Processor;

import java.io.File;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

import io.qameta.allure.Description;
import io.qameta.allure.Feature;
import org.junit.Test;

@Feature(FILE_EXTENSION)
public class DirectoryListenerFunctionalTestCase extends FileConnectorTestCase {

  private static final String MATCHERLESS_LISTENER_FOLDER_NAME = "matcherless";
  private static final String SHARED_LISTENER_FOLDER_NAME = "shared";
  private static final String WITH_MATCHER_FOLDER_NAME = "withMatcher";
  private static final String WATCH_FILE = "watchme.txt";
  private static final String WATCH_CONTENT = "who watches the watchmen?";
  private static final String DR_MANHATTAN = "Dr. Manhattan";
  private static final String MATCH_FILE = "matchme.txt";
  private static final int PROBER_TIMEOUT = 5000;
  private static final int PROBER_DELAY = 100;

  private static List<Message> RECEIVED_MESSAGES;


  public static class TestProcessor implements Processor {

    @Override
    public CoreEvent process(CoreEvent event) throws MuleException {
      RECEIVED_MESSAGES.add(event.getMessage());
      return event;
    }
  }


  private File withMatcherFolder;
  private String listenerFolder;
  private String sharedFolder;

  @Override
  protected String getConfigFile() {
    return "directory-listener-config.xml";
  }

  @Override
  protected void doSetUpBeforeMuleContextCreation() throws Exception {
    super.doSetUpBeforeMuleContextCreation();
    temporaryFolder.newFolder(MATCHERLESS_LISTENER_FOLDER_NAME);
    temporaryFolder.newFolder(WITH_MATCHER_FOLDER_NAME);
    temporaryFolder.newFolder(SHARED_LISTENER_FOLDER_NAME);

    listenerFolder = Paths.get(temporaryFolder.getRoot().getAbsolutePath(), MATCHERLESS_LISTENER_FOLDER_NAME).toString();
    sharedFolder = Paths.get(temporaryFolder.getRoot().getAbsolutePath(), SHARED_LISTENER_FOLDER_NAME).toString();
    withMatcherFolder = Paths.get(temporaryFolder.getRoot().getAbsolutePath(), WITH_MATCHER_FOLDER_NAME).toFile();
    RECEIVED_MESSAGES = new CopyOnWriteArrayList<>();
  }

  @Override
  protected void doTearDown() throws Exception {
    RECEIVED_MESSAGES = null;
  }

  @Test
  @Description("Verifies that a created file is picked")
  public void onFileCreated() throws Exception {
    final File file = new File(listenerFolder, WATCH_FILE);
    write(file, WATCH_CONTENT);
    assertPoll(file, WATCH_CONTENT);
  }

  @Test
  @Description("Verifies that files created in subdirs are picked")
  public void recursive() throws Exception {
    File subdir = new File(listenerFolder, "subdir");
    assertThat(subdir.mkdirs(), is(true));
    final File file = new File(subdir, WATCH_FILE);
    write(file, WATCH_CONTENT);

    assertPoll(file, WATCH_CONTENT);
  }

  @Test
  @Description("Verifies that only files compliant with the matcher are picked")
  public void matcher() throws Exception {
    final File file = new File(withMatcherFolder, MATCH_FILE);
    final File rejectedFile = new File(withMatcherFolder, WATCH_FILE);
    write(file, DR_MANHATTAN);
    write(rejectedFile, WATCH_CONTENT);

    assertPoll(file, DR_MANHATTAN);
    checkNot(PROBER_TIMEOUT, PROBER_DELAY, () -> RECEIVED_MESSAGES.size() > 1);
  }

  @Test
  @Description("Verifies that files created in subdirs are not picked")
  public void nonRecursive() throws Exception {
    stopFlow("listenWithoutMatcher");

    startFlow("listenNonRecursive");
    File subdir = new File(listenerFolder, "subdir");
    assertThat(subdir.mkdirs(), is(true));
    File file = new File(subdir, WATCH_FILE);
    write(file, WATCH_CONTENT);

    expectNot(file);

    file = new File(listenerFolder, "nonRecursive.txt");
    final String nonRecursiveContent = "you shall not recurse";
    write(file, nonRecursiveContent);

    assertPoll(file, nonRecursiveContent);
  }

  @Test
  @Description("Verifies that files are moved after processing")
  public void moveTo() throws Exception {
    stopFlow("listenWithoutMatcher");
    startFlow("moveTo");

    onFileCreated();
    check(PROBER_TIMEOUT, PROBER_DELAY,
          () -> !new File(listenerFolder, WATCH_FILE).exists() && new File(sharedFolder, WATCH_FILE).exists());
  }

  @Test
  @Description("Verifies that files are moved and renamed after processing")
  public void moveToWithRename() throws Exception {
    stopFlow("listenWithoutMatcher");
    startFlow("moveToWithRename");

    onFileCreated();
    check(PROBER_TIMEOUT, PROBER_DELAY,
          () -> !new File(listenerFolder, WATCH_FILE).exists() && new File(sharedFolder, "renamed.txt").exists());
  }

  @Test
  @Description("Tests the case of watermark on update timestamp, processing only files that have been modified after the prior poll")
  public void watermarkForModifiedFiles() throws Exception {
    stopFlow("listenWithoutMatcher");
    startFlow("modifiedWatermark");

    final File file = new File(listenerFolder, WATCH_FILE);
    final File file2 = new File(listenerFolder, WATCH_FILE + "2");
    write(file, WATCH_CONTENT);
    write(file2, WATCH_CONTENT);

    check(PROBER_TIMEOUT, PROBER_DELAY, () -> {
      if (RECEIVED_MESSAGES.size() == 2) {
        return RECEIVED_MESSAGES.stream().anyMatch(m -> containsPath(m, file.getPath())) &&
            RECEIVED_MESSAGES.stream().anyMatch(m -> containsPath(m, file2.getPath()));
      }

      return false;
    });

    assertThat(file.exists(), is(true));
    assertThat(file2.exists(), is(true));

    RECEIVED_MESSAGES.clear();
    final String modifiedData = "modified!";
    write(file, modifiedData);

    check(PROBER_TIMEOUT, PROBER_DELAY, () -> {
      if (RECEIVED_MESSAGES.size() == 1) {
        Message message = RECEIVED_MESSAGES.get(0);
        return containsPath(message, file.getPath()) && message.getPayload().getValue().toString().contains(modifiedData);
      }

      return false;
    });
  }

  @Test
  @Description("Tests the case of watermark on created timestamp, processing only files that have been created after the prior poll")
  public void watermarkForCreatedFiles() throws Exception {
    final String testFlowName = "creationWatermark";
    stopFlow("listenWithoutMatcher");
    startFlow(testFlowName);

    final File file = new File(listenerFolder, WATCH_FILE);
    final File file2 = new File(listenerFolder, WATCH_FILE + "2");
    write(file, WATCH_CONTENT);
    write(file2, WATCH_CONTENT);

    check(PROBER_TIMEOUT, PROBER_DELAY, () -> {
      if (RECEIVED_MESSAGES.size() == 2) {
        return RECEIVED_MESSAGES.stream().anyMatch(m -> containsPath(m, file.getPath())) &&
            RECEIVED_MESSAGES.stream().anyMatch(m -> containsPath(m, file2.getPath()));
      }

      return false;
    });

    stopFlow(testFlowName);
    final File ignoredFile = new File(listenerFolder, "ignoreMe.txt");
    write(ignoredFile, WATCH_CONTENT);
    setLastModifiedTime(ignoredFile.toPath(), FileTime.from(now().minus(1, HOURS)
        .atZone(systemDefault()).toInstant()));

    RECEIVED_MESSAGES.clear();
    startFlow(testFlowName);

    checkNot(PROBER_TIMEOUT, PROBER_DELAY, () -> !RECEIVED_MESSAGES.isEmpty());
  }

  private boolean containsPath(Message message, String path) {
    LocalFileAttributes attrs = (LocalFileAttributes) message.getAttributes().getValue();
    return attrs.getPath().equals(path);
  }

  private void assertPoll(File file, Object expectedContent) {
    Message message = expect(file);

    String payload = toString(message.getPayload().getValue());
    assertThat(payload, equalTo(expectedContent));
  }

  private Message expect(File file) {
    Reference<Message> messageHolder = new Reference<>();
    check(PROBER_TIMEOUT, PROBER_DELAY, () -> {
      getPicked(file).ifPresent(messageHolder::set);
      return messageHolder.get() != null;
    });

    return messageHolder.get();
  }

  private void expectNot(File file) {
    checkNot(PROBER_TIMEOUT, PROBER_DELAY, () -> getPicked(file).isPresent());
  }

  private Optional<Message> getPicked(File file) {
    return RECEIVED_MESSAGES.stream()
        .filter(message -> {
          FileAttributes attributes = (FileAttributes) message.getAttributes().getValue();
          return attributes.getPath().equals(file.getAbsolutePath());
        })
        .findFirst();
  }

  private void startFlow(String flowName) throws Exception {
    ((Startable) getFlowConstruct(flowName)).start();
  }

  private void stopFlow(String flowName) throws Exception {
    ((Stoppable) getFlowConstruct(flowName)).stop();
  }
}
