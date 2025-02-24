/*
 * SonarQube
 * Copyright (C) 2009-2022 SonarSource SA
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.server.issue.ws;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.sonar.api.issue.Issue;
import org.sonar.api.utils.System2;
import org.sonar.db.DbTester;
import org.sonar.db.component.ComponentDbTester;
import org.sonar.db.component.ComponentDto;
import org.sonar.db.issue.IssueDbTester;
import org.sonar.db.issue.IssueDto;
import org.sonar.db.protobuf.DbCommons;
import org.sonar.db.protobuf.DbIssues;
import org.sonar.db.rule.RuleDto;
import org.sonar.db.user.UserDto;
import org.sonar.server.exceptions.ForbiddenException;
import org.sonar.server.issue.ws.pull.PullActionProtobufObjectGenerator;
import org.sonar.server.issue.ws.pull.PullActionResponseWriter;
import org.sonar.server.tester.UserSessionRule;
import org.sonar.server.ws.TestRequest;
import org.sonar.server.ws.TestResponse;
import org.sonar.server.ws.WsActionTester;
import org.sonarqube.ws.Common;
import org.sonarqube.ws.Issues;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.sonar.api.web.UserRole.USER;
import static org.sonar.db.component.ComponentTesting.newFileDto;

public class PullActionTest {

  private static final long NOW = 10_000_000_000L;
  private static final long PAST = 1_000_000_000L;

  private static final String DEFAULT_BRANCH = "master";

  @Rule
  public DbTester dbTester = DbTester.create();

  @Rule
  public UserSessionRule userSession = UserSessionRule.standalone();

  @Rule
  public DbTester db = DbTester.create(System2.INSTANCE);

  private final System2 system2 = mock(System2.class);
  private final PullActionProtobufObjectGenerator pullActionProtobufObjectGenerator = new PullActionProtobufObjectGenerator();

  private final PullActionResponseWriter pullActionResponseWriter = new PullActionResponseWriter(system2, pullActionProtobufObjectGenerator);

  private final IssueDbTester issueDbTester = new IssueDbTester(db);
  private final ComponentDbTester componentDbTester = new ComponentDbTester(db);

  private final PullAction underTest = new PullAction(db.getDbClient(), userSession, pullActionResponseWriter);
  private final WsActionTester tester = new WsActionTester(underTest);

  private RuleDto correctRule, incorrectRule;
  private ComponentDto correctProject, incorrectProject;
  private ComponentDto correctFile, incorrectFile;

  @Before
  public void setUp() {
    when(system2.now()).thenReturn(NOW);
    correctRule = db.rules().insertIssueRule();
    correctProject = db.components().insertPrivateProject();
    correctFile = db.components().insertComponent(newFileDto(correctProject));

    incorrectRule = db.rules().insertIssueRule();
    incorrectProject = db.components().insertPrivateProject();
    incorrectFile = db.components().insertComponent(newFileDto(incorrectProject));
  }

  @Test
  public void givenMissingParams_expectIllegalArgumentException() {
    TestRequest request = tester.newRequest();

    assertThatThrownBy(() -> request.executeProtobuf(Issues.IssuesPullQueryTimestamp.class))
      .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void givenNotExistingProjectKey_throwException() {
    TestRequest request = tester.newRequest()
      .setParam("projectKey", "projectKey")
      .setParam("branchName", DEFAULT_BRANCH);

    assertThatThrownBy(request::execute)
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("Invalid projectKey parameter");
  }

  @Test
  public void givenValidProjectKeyWithoutPermissionsTo_throwException() {
    userSession.logIn();

    TestRequest request = tester.newRequest()
      .setParam("projectKey", correctProject.getKey())
      .setParam("branchName", DEFAULT_BRANCH);

    assertThatThrownBy(request::execute)
      .isInstanceOf(ForbiddenException.class)
      .hasMessage("Insufficient privileges");
  }

  @Test
  public void givenValidProjectKeyAndOneIssueOnBranch_returnOneIssue() throws IOException {
    DbCommons.TextRange textRange = DbCommons.TextRange.newBuilder()
      .setStartLine(1)
      .setEndLine(2)
      .setStartOffset(3)
      .setEndOffset(4)
      .build();
    DbIssues.Locations.Builder mainLocation = DbIssues.Locations.newBuilder()
      .setChecksum("hash")
      .setTextRange(textRange);

    IssueDto issueDto = issueDbTester.insertIssue(p -> p.setSeverity("MINOR")
      .setManualSeverity(true)
      .setMessage("message")
      .setCreatedAt(NOW)
      .setStatus(Issue.STATUS_RESOLVED)
      .setLocations(mainLocation.build())
      .setType(Common.RuleType.BUG.getNumber()));
    loginWithBrowsePermission(issueDto);

    TestRequest request = tester.newRequest()
      .setParam("projectKey", issueDto.getProjectKey())
      .setParam("branchName", DEFAULT_BRANCH);

    TestResponse response = request.execute();
    List<Issues.IssueLite> issues = readAllIssues(response);
    Issues.IssueLite issueLite = issues.get(0);

    assertThat(issues).hasSize(1);

    assertThat(issueLite.getKey()).isEqualTo(issueDto.getKey());
    assertThat(issueLite.getUserSeverity()).isEqualTo("MINOR");
    assertThat(issueLite.getCreationDate()).isEqualTo(NOW);
    assertThat(issueLite.getResolved()).isTrue();
    assertThat(issueLite.getRuleKey()).isEqualTo(issueDto.getRuleKey().rule());
    assertThat(issueLite.getType()).isEqualTo(Common.RuleType.forNumber(issueDto.getType()).name());

    Issues.Location location = issueLite.getMainLocation();
    assertThat(location.getMessage()).isEqualTo(issueDto.getMessage());

    Issues.TextRange locationTextRange = location.getTextRange();
    assertThat(locationTextRange.getStartLine()).isEqualTo(1);
    assertThat(locationTextRange.getEndLine()).isEqualTo(2);
    assertThat(locationTextRange.getStartLineOffset()).isEqualTo(3);
    assertThat(locationTextRange.getEndLineOffset()).isEqualTo(4);
    assertThat(locationTextRange.getHash()).isEqualTo("hash");
  }

  @Test
  public void givenIssueOnAnotherBranch_returnOneIssue() throws IOException {
    ComponentDto developBranch = componentDbTester.insertPrivateProjectWithCustomBranch("develop");
    ComponentDto developFile = db.components().insertComponent(newFileDto(developBranch));
    generateIssues(correctRule, developBranch, developFile, 1);
    loginWithBrowsePermission(developBranch.uuid(), developFile.uuid());

    TestRequest request = tester.newRequest()
      .setParam("projectKey", developBranch.getKey())
      .setParam("branchName", "develop");

    TestResponse response = request.execute();
    List<Issues.IssueLite> issues = readAllIssues(response);

    assertThat(issues).hasSize(1);
  }

  @Test
  public void inIncrementalModeReturnClosedIssues() throws IOException {
    IssueDto openIssue = issueDbTester.insertIssue(p -> p.setSeverity("MINOR")
      .setManualSeverity(true)
      .setMessage("openIssue")
      .setCreatedAt(NOW)
      .setStatus(Issue.STATUS_OPEN)
      .setType(Common.RuleType.BUG.getNumber()));

    issueDbTester.insertIssue(p -> p.setSeverity("MINOR")
      .setMessage("closedIssue")
      .setCreatedAt(NOW)
      .setStatus(Issue.STATUS_CLOSED)
      .setType(Common.RuleType.BUG.getNumber())
      .setComponentUuid(openIssue.getComponentUuid())
      .setProjectUuid(openIssue.getProjectUuid())
      .setIssueUpdateTime(PAST)
      .setIssueCreationTime(PAST));

    loginWithBrowsePermission(openIssue);

    TestRequest request = tester.newRequest()
      .setParam("projectKey", openIssue.getProjectKey())
      .setParam("branchName", DEFAULT_BRANCH)
      .setParam("changedSince", PAST + "");

    TestResponse response = request.execute();
    List<Issues.IssueLite> issues = readAllIssues(response);

    assertThat(issues).hasSize(2);
  }

  @Test
  public void given15IssuesInTheTable_returnOnly10ThatBelongToProject() throws IOException {
    loginWithBrowsePermission(correctProject.uuid(), correctFile.uuid());
    generateIssues(correctRule, correctProject, correctFile, 10);
    generateIssues(incorrectRule, incorrectProject, incorrectFile, 5);

    TestRequest request = tester.newRequest()
      .setParam("projectKey", correctProject.getKey())
      .setParam("branchName", DEFAULT_BRANCH);

    TestResponse response = request.execute();
    List<Issues.IssueLite> issues = readAllIssues(response);

    assertThat(issues).hasSize(10);
  }

  @Test
  public void givenNoIssuesBelongToTheProject_return0Issues() throws IOException {
    loginWithBrowsePermission(correctProject.uuid(), correctFile.uuid());
    generateIssues(incorrectRule, incorrectProject, incorrectFile, 5);

    TestRequest request = tester.newRequest()
      .setParam("projectKey", correctProject.getKey())
      .setParam("branchName", DEFAULT_BRANCH);

    TestResponse response = request.execute();
    List<Issues.IssueLite> issues = readAllIssues(response);

    assertThat(issues).isEmpty();
  }

  @Test
  public void testLanguagesParam_return1Issue() throws IOException {
    loginWithBrowsePermission(correctProject.uuid(), correctFile.uuid());
    RuleDto javaRule = db.rules().insert(r -> r.setLanguage("java"));

    IssueDto javaIssue = issueDbTester.insertIssue(p -> p.setSeverity("MINOR")
      .setManualSeverity(true)
      .setMessage("openIssue")
      .setCreatedAt(NOW)
      .setRule(javaRule)
      .setRuleUuid(javaRule.getUuid())
      .setStatus(Issue.STATUS_OPEN)
      .setLanguage("java")
      .setProject(correctProject)
      .setComponent(correctFile)
      .setType(Common.RuleType.BUG.getNumber()));

    TestRequest request = tester.newRequest()
      .setParam("projectKey", correctProject.getKey())
      .setParam("branchName", DEFAULT_BRANCH)
      .setParam("languages", "java");

    TestResponse response = request.execute();
    List<Issues.IssueLite> issues = readAllIssues(response);

    assertThat(issues).hasSize(1);
    assertThat(issues.get(0).getKey()).isEqualTo(javaIssue.getKey());
  }

  @Test
  public void testLanguagesParam_givenWrongLanguage_return0Issues() throws IOException {
    loginWithBrowsePermission(correctProject.uuid(), correctFile.uuid());
    RuleDto javascriptRule = db.rules().insert(r -> r.setLanguage("javascript"));

    issueDbTester.insertIssue(p -> p.setSeverity("MINOR")
      .setManualSeverity(true)
      .setMessage("openIssue")
      .setCreatedAt(NOW)
      .setRule(javascriptRule)
      .setRuleUuid(javascriptRule.getUuid())
      .setStatus(Issue.STATUS_OPEN)
      .setProject(correctProject)
      .setComponent(correctFile)
      .setType(2));

    TestRequest request = tester.newRequest()
      .setParam("projectKey", correctProject.getKey())
      .setParam("branchName", DEFAULT_BRANCH)
      .setParam("languages", "java");

    TestResponse response = request.execute();
    List<Issues.IssueLite> issues = readAllIssues(response);

    assertThat(issues).isEmpty();
  }

  @Test
  public void testRuleRepositoriesParam_return1IssueForGivenRepository() throws IOException {
    loginWithBrowsePermission(correctProject.uuid(), correctFile.uuid());
    RuleDto javaRule = db.rules().insert(r -> r.setRepositoryKey("java"));
    RuleDto javaScriptRule = db.rules().insert(r -> r.setRepositoryKey("javascript"));

    IssueDto issueDto = issueDbTester.insertIssue(p -> p.setSeverity("MINOR")
      .setManualSeverity(true)
      .setMessage("openIssue")
      .setCreatedAt(NOW)
      .setRule(javaRule)
      .setStatus(Issue.STATUS_OPEN)
      .setProject(correctProject)
      .setComponent(correctFile)
      .setType(2));

    //this one should not be returned - it is a different rule repository
    issueDbTester.insertIssue(p -> p.setSeverity("MINOR")
      .setManualSeverity(true)
      .setMessage("openIssue")
      .setCreatedAt(NOW)
      .setRule(javaScriptRule)
      .setStatus(Issue.STATUS_OPEN)
      .setProject(correctProject)
      .setComponent(correctFile)
      .setType(Common.RuleType.BUG.getNumber()));

    TestRequest request = tester.newRequest()
      .setParam("projectKey", correctProject.getKey())
      .setParam("branchName", DEFAULT_BRANCH)
      .setParam("ruleRepositories", "java");

    TestResponse response = request.execute();
    List<Issues.IssueLite> issues = readAllIssues(response);

    assertThat(issues).hasSize(1);
    assertThat(issues.get(0).getKey()).isEqualTo(issueDto.getKey());
  }

  private void generateIssues(RuleDto rule, ComponentDto project, ComponentDto file, int numberOfIssues) {
    for (int j = 0; j < numberOfIssues; j++) {
      issueDbTester.insert(i -> i.setProject(project)
        .setComponentUuid(file.uuid())
        .setRuleUuid(rule.getUuid())
        .setStatus(Issue.STATUS_OPEN)
        .setType(2));
    }
  }

  private List<Issues.IssueLite> readAllIssues(TestResponse response) throws IOException {
    List<Issues.IssueLite> issues = new ArrayList<>();
    InputStream inputStream = response.getInputStream();
    Issues.IssuesPullQueryTimestamp.parseDelimitedFrom(inputStream);

    while (inputStream.available() > 0) {
      issues.add(Issues.IssueLite.parseDelimitedFrom(inputStream));
    }

    return issues;
  }

  private void loginWithBrowsePermission(IssueDto issueDto) {
    loginWithBrowsePermission(issueDto.getProjectUuid(), issueDto.getComponentUuid());
  }

  private void loginWithBrowsePermission(String projectUuid, String componentUuid) {
    UserDto user = dbTester.users().insertUser("john");
    userSession.logIn(user)
      .addProjectPermission(USER,
        db.getDbClient().componentDao().selectByUuid(dbTester.getSession(), projectUuid).get(),
        db.getDbClient().componentDao().selectByUuid(dbTester.getSession(), componentUuid).get());
  }

}
