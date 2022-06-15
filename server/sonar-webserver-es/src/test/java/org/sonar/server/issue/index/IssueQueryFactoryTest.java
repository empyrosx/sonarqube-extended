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
package org.sonar.server.issue.index;

import java.time.Clock;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Map;
import org.junit.Rule;
import org.junit.Test;
import org.sonar.api.resources.Qualifiers;
import org.sonar.api.rule.RuleKey;
import org.sonar.db.DbTester;
import org.sonar.db.component.ComponentDto;
import org.sonar.db.component.SnapshotDto;
import org.sonar.db.metric.MetricDto;
import org.sonar.db.rule.RuleDbTester;
import org.sonar.db.rule.RuleDto;
import org.sonar.db.user.UserDto;
import org.sonar.server.issue.SearchRequest;
import org.sonar.server.tester.UserSessionRule;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.sonar.api.measures.CoreMetrics.ANALYSIS_FROM_SONARQUBE_9_4_KEY;
import static org.sonar.api.resources.Qualifiers.APP;
import static org.sonar.api.utils.DateUtils.addDays;
import static org.sonar.api.utils.DateUtils.parseDateTime;
import static org.sonar.api.web.UserRole.USER;
import static org.sonar.db.component.ComponentTesting.newDirectory;
import static org.sonar.db.component.ComponentTesting.newFileDto;
import static org.sonar.db.component.ComponentTesting.newModuleDto;
import static org.sonar.db.component.ComponentTesting.newProjectCopy;
import static org.sonar.db.component.ComponentTesting.newSubPortfolio;
import static org.sonar.db.newcodeperiod.NewCodePeriodType.REFERENCE_BRANCH;
import static org.sonar.db.rule.RuleTesting.newRule;

public class IssueQueryFactoryTest {

  @Rule
  public UserSessionRule userSession = UserSessionRule.standalone();
  @Rule
  public DbTester db = DbTester.create();

  private final RuleDbTester ruleDbTester = new RuleDbTester(db);
  private final Clock clock = mock(Clock.class);
  private final IssueQueryFactory underTest = new IssueQueryFactory(db.getDbClient(), clock, userSession);

  @Test
  public void create_from_parameters() {
    String ruleAdHocName = "New Name";
    UserDto user = db.users().insertUser(u -> u.setLogin("joanna"));
    ComponentDto project = db.components().insertPrivateProject();
    ComponentDto module = db.components().insertComponent(newModuleDto(project));
    ComponentDto file = db.components().insertComponent(newFileDto(project));

    RuleDto rule1 = ruleDbTester.insert(r -> r.setAdHocName(ruleAdHocName));
    RuleDto rule2 = ruleDbTester.insert(r -> r.setAdHocName(ruleAdHocName));
    newRule(RuleKey.of("findbugs", "NullReference"));
    SearchRequest request = new SearchRequest()
      .setIssues(asList("anIssueKey"))
      .setSeverities(asList("MAJOR", "MINOR"))
      .setStatuses(asList("CLOSED"))
      .setResolutions(asList("FALSE-POSITIVE"))
      .setResolved(true)
      .setProjects(asList(project.getDbKey()))
      .setDirectories(asList("aDirPath"))
      .setFiles(asList(file.uuid()))
      .setAssigneesUuid(asList(user.getUuid()))
      .setScopes(asList("MAIN", "TEST"))
      .setLanguages(asList("xoo"))
      .setTags(asList("tag1", "tag2"))
      .setAssigned(true)
      .setCreatedAfter("2013-04-16T09:08:24+0200")
      .setCreatedBefore("2013-04-17T09:08:24+0200")
      .setRules(asList(rule1.getKey().toString(), rule2.getKey().toString()))
      .setSort("CREATION_DATE")
      .setAsc(true);

    IssueQuery query = underTest.create(request);

    assertThat(query.issueKeys()).containsOnly("anIssueKey");
    assertThat(query.severities()).containsOnly("MAJOR", "MINOR");
    assertThat(query.statuses()).containsOnly("CLOSED");
    assertThat(query.resolutions()).containsOnly("FALSE-POSITIVE");
    assertThat(query.resolved()).isTrue();
    assertThat(query.projectUuids()).containsOnly(project.uuid());
    assertThat(query.files()).containsOnly(file.uuid());
    assertThat(query.assignees()).containsOnly(user.getUuid());
    assertThat(query.scopes()).containsOnly("TEST", "MAIN");
    assertThat(query.languages()).containsOnly("xoo");
    assertThat(query.tags()).containsOnly("tag1", "tag2");
    assertThat(query.onComponentOnly()).isFalse();
    assertThat(query.assigned()).isTrue();
    assertThat(query.rules()).hasSize(2);
    assertThat(query.ruleUuids()).hasSize(2);
    assertThat(query.directories()).containsOnly("aDirPath");
    assertThat(query.createdAfter().date()).isEqualTo(parseDateTime("2013-04-16T09:08:24+0200"));
    assertThat(query.createdAfter().inclusive()).isTrue();
    assertThat(query.createdBefore()).isEqualTo(parseDateTime("2013-04-17T09:08:24+0200"));
    assertThat(query.sort()).isEqualTo(IssueQuery.SORT_BY_CREATION_DATE);
    assertThat(query.asc()).isTrue();

  }

  @Test
  public void create_with_rule_key_that_does_not_exist_in_the_db() {
    db.users().insertUser(u -> u.setLogin("joanna"));
    ComponentDto project = db.components().insertPrivateProject();
    db.components().insertComponent(newModuleDto(project));
    db.components().insertComponent(newFileDto(project));
    newRule(RuleKey.of("findbugs", "NullReference"));
    SearchRequest request = new SearchRequest()
      .setRules(asList("unknown:key1", "unknown:key2"));

    IssueQuery query = underTest.create(request);

    assertThat(query.rules()).isEmpty();
    assertThat(query.ruleUuids()).containsExactly("non-existing-uuid");
  }

  @Test
  public void leak_period_start_date_is_exclusive() {
    long leakPeriodStart = addDays(new Date(), -14).getTime();

    ComponentDto project = db.components().insertPublicProject();
    ComponentDto file = db.components().insertComponent(newFileDto(project));

    SnapshotDto analysis = db.components().insertSnapshot(project, s -> s.setPeriodDate(leakPeriodStart));

    SearchRequest request = new SearchRequest()
      .setComponentUuids(Collections.singletonList(file.uuid()))
      .setOnComponentOnly(true)
      .setSinceLeakPeriod(true);

    IssueQuery query = underTest.create(request);

    assertThat(query.componentUuids()).containsOnly(file.uuid());
    assertThat(query.createdAfter().date()).isEqualTo(new Date(leakPeriodStart));
    assertThat(query.createdAfter().inclusive()).isFalse();
    assertThat(query.newCodeOnReference()).isNull();
  }

  @Test
  public void in_new_code_period_start_date_is_exclusive() {
    long newCodePeriodStart = addDays(new Date(), -14).getTime();

    ComponentDto project = db.components().insertPublicProject();
    ComponentDto file = db.components().insertComponent(newFileDto(project));

    SnapshotDto analysis = db.components().insertSnapshot(project, s -> s.setPeriodDate(newCodePeriodStart));

    SearchRequest request = new SearchRequest()
      .setComponentUuids(Collections.singletonList(file.uuid()))
      .setOnComponentOnly(true)
      .setInNewCodePeriod(true);

    IssueQuery query = underTest.create(request);

    assertThat(query.componentUuids()).containsOnly(file.uuid());
    assertThat(query.createdAfter().date()).isEqualTo(new Date(newCodePeriodStart));
    assertThat(query.createdAfter().inclusive()).isFalse();
    assertThat(query.newCodeOnReference()).isNull();
  }

  @Test
  public void leak_period_relies_on_date_for_reference_branch_with_analysis_after_sonarqube_94() {
    long leakPeriodStart = addDays(new Date(), -14).getTime();

    ComponentDto project = db.components().insertPublicProject();
    ComponentDto file = db.components().insertComponent(newFileDto(project));

    SnapshotDto analysis = db.components().insertSnapshot(project, s -> s.setPeriodMode(REFERENCE_BRANCH.name())
      .setPeriodParam("master"));

    MetricDto analysisMetric = db.measures().insertMetric(m -> m.setKey(ANALYSIS_FROM_SONARQUBE_9_4_KEY));
    db.measures().insertLiveMeasure(project, analysisMetric, measure -> measure.setData("true"));

    SearchRequest request = new SearchRequest()
      .setComponentUuids(Collections.singletonList(file.uuid()))
      .setOnComponentOnly(true)
      .setSinceLeakPeriod(true);

    IssueQuery query = underTest.create(request);

    assertThat(query.componentUuids()).containsOnly(file.uuid());
    assertThat(query.newCodeOnReference()).isTrue();
    assertThat(query.createdAfter()).isNull();
  }
  @Test
  public void new_code_period_does_not_rely_on_date_for_reference_branch_with_analysis_after_sonarqube_94() {
    ComponentDto project = db.components().insertPublicProject();
    ComponentDto file = db.components().insertComponent(newFileDto(project));

    db.components().insertSnapshot(project, s -> s.setPeriodMode(REFERENCE_BRANCH.name())
      .setPeriodParam("master"));

    MetricDto analysisMetric = db.measures().insertMetric(m -> m.setKey(ANALYSIS_FROM_SONARQUBE_9_4_KEY));
    db.measures().insertLiveMeasure(project, analysisMetric, measure -> measure.setData("true"));

    SearchRequest request = new SearchRequest()
      .setComponentUuids(Collections.singletonList(file.uuid()))
      .setOnComponentOnly(true)
      .setInNewCodePeriod(true);

    IssueQuery query = underTest.create(request);

    assertThat(query.componentUuids()).containsOnly(file.uuid());
    assertThat(query.newCodeOnReference()).isTrue();
    assertThat(query.createdAfter()).isNull();
  }

  @Test
  public void dates_are_inclusive() {
    when(clock.getZone()).thenReturn(ZoneId.of("Europe/Paris"));
    SearchRequest request = new SearchRequest()
      .setCreatedAfter("2013-04-16")
      .setCreatedBefore("2013-04-17");

    IssueQuery query = underTest.create(request);

    assertThat(query.createdAfter().date()).isEqualTo(parseDateTime("2013-04-16T00:00:00+0200"));
    assertThat(query.createdAfter().inclusive()).isTrue();
    assertThat(query.createdBefore()).isEqualTo(parseDateTime("2013-04-18T00:00:00+0200"));
  }

  @Test
  public void creation_date_support_localdate() {
    when(clock.getZone()).thenReturn(ZoneId.of("Europe/Paris"));
    SearchRequest request = new SearchRequest()
      .setCreatedAt("2013-04-16");

    IssueQuery query = underTest.create(request);

    assertThat(query.createdAt()).isEqualTo(parseDateTime("2013-04-16T00:00:00+0200"));
  }

  @Test
  public void use_provided_timezone_to_parse_createdAfter() {
    SearchRequest request = new SearchRequest()
      .setCreatedAfter("2020-04-16")
      .setTimeZone("Australia/Sydney");

    IssueQuery query = underTest.create(request);

    assertThat(query.createdAfter().date()).isEqualTo(parseDateTime("2020-04-16T00:00:00+1000"));
  }

  @Test
  public void use_provided_timezone_to_parse_createdBefore() {
    SearchRequest request = new SearchRequest()
      .setCreatedBefore("2020-04-16")
      .setTimeZone("Europe/Moscow");

    IssueQuery query = underTest.create(request);

    assertThat(query.createdBefore()).isEqualTo(parseDateTime("2020-04-17T00:00:00+0300"));
  }

  @Test
  public void creation_date_support_zoneddatetime() {
    SearchRequest request = new SearchRequest()
      .setCreatedAt("2013-04-16T09:08:24+0200");

    IssueQuery query = underTest.create(request);

    assertThat(query.createdAt()).isEqualTo(parseDateTime("2013-04-16T09:08:24+0200"));
  }

  @Test
  public void add_unknown_when_no_component_found() {
    SearchRequest request = new SearchRequest()
      .setComponents(asList("does_not_exist"));

    IssueQuery query = underTest.create(request);

    assertThat(query.componentUuids()).containsOnly("<UNKNOWN>");
  }

  @Test
  public void query_without_any_parameter() {
    SearchRequest request = new SearchRequest();

    IssueQuery query = underTest.create(request);

    assertThat(query.componentUuids()).isEmpty();
    assertThat(query.projectUuids()).isEmpty();
    assertThat(query.moduleRootUuids()).isEmpty();
    assertThat(query.directories()).isEmpty();
    assertThat(query.files()).isEmpty();
    assertThat(query.viewUuids()).isEmpty();
    assertThat(query.branchUuid()).isNull();
  }

  @Test
  public void fail_if_components_and_components_uuid_params_are_set_at_the_same_time() {
    SearchRequest request = new SearchRequest()
      .setComponents(singletonList("foo"))
      .setComponentUuids(singletonList("bar"));

    assertThatThrownBy(() -> underTest.create(request))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("At most one of the following parameters can be provided: componentKeys and componentUuids");
  }

  @Test
  public void fail_if_invalid_timezone() {
    SearchRequest request = new SearchRequest()
      .setTimeZone("Poitou-Charentes");

    assertThatThrownBy(() -> underTest.create(request))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("TimeZone 'Poitou-Charentes' cannot be parsed as a valid zone ID");
  }

  @Test
  public void param_componentUuids_enables_search_in_view_tree_if_user_has_permission_on_view() {
    ComponentDto view = db.components().insertPublicPortfolio();
    SearchRequest request = new SearchRequest()
      .setComponentUuids(singletonList(view.uuid()));
    userSession.registerComponents(view);

    IssueQuery query = underTest.create(request);

    assertThat(query.viewUuids()).containsOnly(view.uuid());
    assertThat(query.onComponentOnly()).isFalse();
  }

  @Test
  public void application_search_project_issues() {
    ComponentDto project1 = db.components().insertPublicProject();
    ComponentDto project2 = db.components().insertPublicProject();
    ComponentDto application = db.components().insertPublicApplication();
    db.components().insertComponents(newProjectCopy("PC1", project1, application));
    db.components().insertComponents(newProjectCopy("PC2", project2, application));
    userSession.registerApplication(application, project1, project2);

    IssueQuery result = underTest.create(new SearchRequest().setComponentUuids(singletonList(application.uuid())));

    assertThat(result.viewUuids()).containsExactlyInAnyOrder(application.uuid());
  }

  @Test
  public void application_search_project_issues_returns_empty_if_user_cannot_access_child_projects() {
    ComponentDto project1 = db.components().insertPrivateProject();
    ComponentDto project2 = db.components().insertPrivateProject();
    ComponentDto application = db.components().insertPublicApplication();
    db.components().insertComponents(newProjectCopy("PC1", project1, application));
    db.components().insertComponents(newProjectCopy("PC2", project2, application));

    IssueQuery result = underTest.create(new SearchRequest().setComponentUuids(singletonList(application.uuid())));

    assertThat(result.viewUuids()).containsOnly("<UNKNOWN>");
  }

  @Test
  public void application_search_project_issues_on_leak_with_and_without_analysis_after_sonarqube_94() {
    Date now = new Date();
    when(clock.millis()).thenReturn(now.getTime());
    ComponentDto project1 = db.components().insertPublicProject();
    SnapshotDto analysis1 = db.components().insertSnapshot(project1, s -> s.setPeriodDate(addDays(now, -14).getTime()));
    ComponentDto project2 = db.components().insertPublicProject();
    db.components().insertSnapshot(project2, s -> s.setPeriodDate(null));
    ComponentDto project3 = db.components().insertPublicProject();
    ComponentDto project4 = db.components().insertPublicProject();
    SnapshotDto analysis3 =db.components().insertSnapshot(project3,
      s -> s.setPeriodMode(REFERENCE_BRANCH.name()).setPeriodParam("master")
        .setPeriodDate(addDays(now, -14).getTime()));
    db.components().insertSnapshot(project4,
      s -> s.setPeriodMode(REFERENCE_BRANCH.name()).setPeriodParam("master"));
    ComponentDto application = db.components().insertPublicApplication();
    MetricDto analysisMetric = db.measures().insertMetric(m -> m.setKey(ANALYSIS_FROM_SONARQUBE_9_4_KEY));
    db.measures().insertLiveMeasure(project4, analysisMetric, measure -> measure.setData("true"));
    db.components().insertComponents(newProjectCopy("PC1", project1, application));
    db.components().insertComponents(newProjectCopy("PC2", project2, application));
    db.components().insertComponents(newProjectCopy("PC3", project3, application));
    db.components().insertComponents(newProjectCopy("PC4", project4, application));
    userSession.registerApplication(application, project1, project2, project3, project4);

    IssueQuery result = underTest.create(new SearchRequest()
      .setComponentUuids(singletonList(application.uuid()))
      .setSinceLeakPeriod(true));

    assertThat(result.createdAfterByProjectUuids()).hasSize(2);
    assertThat(result.createdAfterByProjectUuids().entrySet()).extracting(Map.Entry::getKey, e -> e.getValue().date(), e -> e.getValue().inclusive()).containsOnly(
      tuple(project1.uuid(), new Date(analysis1.getPeriodDate()), false),
      tuple(project3.uuid(), new Date(analysis3.getPeriodDate()), false));
    assertThat(result.newCodeOnReferenceByProjectUuids()).hasSize(1);
    assertThat(result.newCodeOnReferenceByProjectUuids()).containsOnly(project4.uuid());
    assertThat(result.viewUuids()).containsExactlyInAnyOrder(application.uuid());
  }

  @Test
  public void application_search_project_issues_in_new_code_with_and_without_analysis_after_sonarqube_94() {
    Date now = new Date();
    when(clock.millis()).thenReturn(now.getTime());
    ComponentDto project1 = db.components().insertPublicProject();
    SnapshotDto analysis1 = db.components().insertSnapshot(project1, s -> s.setPeriodDate(addDays(now, -14).getTime()));
    ComponentDto project2 = db.components().insertPublicProject();
    db.components().insertSnapshot(project2, s -> s.setPeriodDate(null));
    ComponentDto project3 = db.components().insertPublicProject();
    ComponentDto project4 = db.components().insertPublicProject();
    SnapshotDto analysis2 = db.components().insertSnapshot(project4,
      s -> s.setPeriodMode(REFERENCE_BRANCH.name()).setPeriodParam("master"));
    ComponentDto application = db.components().insertPublicApplication();
    MetricDto analysisMetric = db.measures().insertMetric(m -> m.setKey(ANALYSIS_FROM_SONARQUBE_9_4_KEY));
    db.measures().insertLiveMeasure(project4, analysisMetric, measure -> measure.setData("true"));
    db.components().insertComponents(newProjectCopy("PC1", project1, application));
    db.components().insertComponents(newProjectCopy("PC2", project2, application));
    db.components().insertComponents(newProjectCopy("PC3", project3, application));
    db.components().insertComponents(newProjectCopy("PC4", project4, application));
    userSession.registerApplication(application, project1, project2, project3, project4);

    IssueQuery result = underTest.create(new SearchRequest()
      .setComponentUuids(singletonList(application.uuid()))
      .setInNewCodePeriod(true));

    assertThat(result.createdAfterByProjectUuids()).hasSize(1);
    assertThat(result.createdAfterByProjectUuids().entrySet()).extracting(Map.Entry::getKey, e -> e.getValue().date(), e -> e.getValue().inclusive()).containsOnly(
      tuple(project1.uuid(), new Date(analysis1.getPeriodDate()), false));
    assertThat(result.newCodeOnReferenceByProjectUuids()).hasSize(1);
    assertThat(result.newCodeOnReferenceByProjectUuids()).containsOnly(project4.uuid());
    assertThat(result.viewUuids()).containsExactlyInAnyOrder(application.uuid());
  }

  @Test
  public void return_empty_results_if_not_allowed_to_search_for_subview() {
    ComponentDto view = db.components().insertPrivatePortfolio();
    ComponentDto subView = db.components().insertComponent(newSubPortfolio(view));
    SearchRequest request = new SearchRequest()
      .setComponentUuids(singletonList(subView.uuid()));

    IssueQuery query = underTest.create(request);

    assertThat(query.viewUuids()).containsOnly("<UNKNOWN>");
  }

  @Test
  public void param_componentUuids_enables_search_on_project_tree_by_default() {
    ComponentDto project = db.components().insertPrivateProject();
    SearchRequest request = new SearchRequest()
      .setComponentUuids(asList(project.uuid()));

    IssueQuery query = underTest.create(request);
    assertThat(query.projectUuids()).containsExactly(project.uuid());
    assertThat(query.onComponentOnly()).isFalse();
  }

  @Test
  public void onComponentOnly_restricts_search_to_specified_componentKeys() {
    ComponentDto project = db.components().insertPrivateProject();
    SearchRequest request = new SearchRequest()
      .setComponents(asList(project.getDbKey()))
      .setOnComponentOnly(true);

    IssueQuery query = underTest.create(request);

    assertThat(query.projectUuids()).isEmpty();
    assertThat(query.componentUuids()).containsExactly(project.uuid());
    assertThat(query.onComponentOnly()).isTrue();
  }

  @Test
  public void should_search_in_tree_with_module_uuid() {
    ComponentDto project = db.components().insertPrivateProject();
    ComponentDto module = db.components().insertComponent(newModuleDto(project));
    SearchRequest request = new SearchRequest()
      .setComponentUuids(asList(module.uuid()));

    IssueQuery query = underTest.create(request);
    assertThat(query.moduleRootUuids()).containsExactly(module.uuid());
    assertThat(query.onComponentOnly()).isFalse();
  }

  @Test
  public void param_componentUuids_enables_search_in_directory_tree() {
    ComponentDto project = db.components().insertPrivateProject();
    ComponentDto dir = db.components().insertComponent(newDirectory(project, "src/main/java/foo"));
    SearchRequest request = new SearchRequest()
      .setComponentUuids(asList(dir.uuid()));

    IssueQuery query = underTest.create(request);

    assertThat(query.directories()).containsOnly(dir.path());
    assertThat(query.onComponentOnly()).isFalse();
  }

  @Test
  public void param_componentUuids_enables_search_by_file() {
    ComponentDto project = db.components().insertPrivateProject();
    ComponentDto file = db.components().insertComponent(newFileDto(project));
    SearchRequest request = new SearchRequest()
      .setComponentUuids(asList(file.uuid()));

    IssueQuery query = underTest.create(request);

    assertThat(query.componentUuids()).containsExactly(file.uuid());
  }

  @Test
  public void param_componentUuids_enables_search_by_test_file() {
    ComponentDto project = db.components().insertPrivateProject();
    ComponentDto file = db.components().insertComponent(newFileDto(project).setQualifier(Qualifiers.UNIT_TEST_FILE));
    SearchRequest request = new SearchRequest()
      .setComponentUuids(asList(file.uuid()));

    IssueQuery query = underTest.create(request);

    assertThat(query.componentUuids()).containsExactly(file.uuid());
  }

  @Test
  public void search_issue_from_branch() {
    ComponentDto project = db.components().insertPrivateProject();
    ComponentDto branch = db.components().insertProjectBranch(project);

    assertThat(underTest.create(new SearchRequest()
      .setProjects(singletonList(branch.getKey()))
      .setBranch(branch.getBranch())))
      .extracting(IssueQuery::branchUuid, query -> new ArrayList<>(query.projectUuids()), IssueQuery::isMainBranch)
      .containsOnly(branch.uuid(), singletonList(project.uuid()), false);

    assertThat(underTest.create(new SearchRequest()
      .setComponents(singletonList(branch.getKey()))
      .setBranch(branch.getBranch())))
      .extracting(IssueQuery::branchUuid, query -> new ArrayList<>(query.projectUuids()), IssueQuery::isMainBranch)
      .containsOnly(branch.uuid(), singletonList(project.uuid()), false);
  }

  @Test
  public void search_file_issue_from_branch() {
    ComponentDto project = db.components().insertPrivateProject();
    ComponentDto branch = db.components().insertProjectBranch(project);
    ComponentDto file = db.components().insertComponent(newFileDto(branch));

    assertThat(underTest.create(new SearchRequest()
      .setComponents(singletonList(file.getKey()))
      .setBranch(branch.getBranch())))
      .extracting(IssueQuery::branchUuid, query -> new ArrayList<>(query.componentUuids()), IssueQuery::isMainBranch)
      .containsOnly(branch.uuid(), singletonList(file.uuid()), false);

    assertThat(underTest.create(new SearchRequest()
      .setComponents(singletonList(branch.getKey()))
      .setFiles(singletonList(file.path()))
      .setBranch(branch.getBranch())))
      .extracting(IssueQuery::branchUuid, query -> new ArrayList<>(query.files()), IssueQuery::isMainBranch)
      .containsOnly(branch.uuid(), singletonList(file.path()), false);

    assertThat(underTest.create(new SearchRequest()
      .setProjects(singletonList(branch.getKey()))
      .setFiles(singletonList(file.path()))
      .setBranch(branch.getBranch())))
      .extracting(IssueQuery::branchUuid, query -> new ArrayList<>(query.files()), IssueQuery::isMainBranch)
      .containsOnly(branch.uuid(), singletonList(file.path()), false);
  }

  @Test
  public void search_issue_on_component_only_from_branch() {
    ComponentDto project = db.components().insertPrivateProject();
    ComponentDto branch = db.components().insertProjectBranch(project);
    ComponentDto file = db.components().insertComponent(newFileDto(branch));

    assertThat(underTest.create(new SearchRequest()
      .setComponents(singletonList(file.getKey()))
      .setBranch(branch.getBranch())
      .setOnComponentOnly(true)))
      .extracting(IssueQuery::branchUuid, query -> new ArrayList<>(query.componentUuids()), IssueQuery::isMainBranch)
      .containsOnly(branch.uuid(), singletonList(file.uuid()), false);
  }

  @Test
  public void search_issues_from_main_branch() {
    ComponentDto project = db.components().insertPublicProject();
    ComponentDto branch = db.components().insertProjectBranch(project);

    assertThat(underTest.create(new SearchRequest()
      .setProjects(singletonList(project.getKey()))
      .setBranch("master")))
      .extracting(IssueQuery::branchUuid, query -> new ArrayList<>(query.projectUuids()), IssueQuery::isMainBranch)
      .containsOnly(project.uuid(), singletonList(project.uuid()), true);
    assertThat(underTest.create(new SearchRequest()
      .setComponents(singletonList(project.getKey()))
      .setBranch("master")))
      .extracting(IssueQuery::branchUuid, query -> new ArrayList<>(query.projectUuids()), IssueQuery::isMainBranch)
      .containsOnly(project.uuid(), singletonList(project.uuid()), true);
  }

  @Test
  public void search_by_application_key() {
    ComponentDto application = db.components().insertPrivateApplication();
    ComponentDto project1 = db.components().insertPrivateProject();
    ComponentDto project2 = db.components().insertPrivateProject();
    db.components().insertComponents(newProjectCopy(project1, application));
    db.components().insertComponents(newProjectCopy(project2, application));
    userSession.registerApplication(application, project1, project2)
      .addProjectPermission(USER, application)
      .addProjectPermission(USER, project1)
      .addProjectPermission(USER, project2);

    assertThat(underTest.create(new SearchRequest()
        .setComponents(singletonList(application.getKey())))
      .viewUuids()).containsExactly(application.uuid());
  }

  @Test
  public void search_by_application_key_and_branch() {
    ComponentDto application = db.components().insertPublicProject(c -> c.setQualifier(APP).setDbKey("app"));
    ComponentDto applicationBranch1 = db.components().insertProjectBranch(application, a -> a.setKey("app-branch1"));
    ComponentDto applicationBranch2 = db.components().insertProjectBranch(application, a -> a.setKey("app-branch2"));
    ComponentDto project1 = db.components().insertPrivateProject(p -> p.setDbKey("prj1"));
    ComponentDto project1Branch1 = db.components().insertProjectBranch(project1);
    ComponentDto fileOnProject1Branch1 = db.components().insertComponent(newFileDto(project1Branch1));
    ComponentDto project1Branch2 = db.components().insertProjectBranch(project1);
    ComponentDto project2 = db.components().insertPrivateProject(p -> p.setDbKey("prj2"));
    db.components().insertComponents(newProjectCopy(project1Branch1, applicationBranch1));
    db.components().insertComponents(newProjectCopy(project2, applicationBranch1));
    db.components().insertComponents(newProjectCopy(project1Branch2, applicationBranch2));

    // Search on applicationBranch1
    assertThat(underTest.create(new SearchRequest()
      .setComponents(singletonList(applicationBranch1.getKey()))
      .setBranch(applicationBranch1.getBranch())))
      .extracting(IssueQuery::branchUuid, query -> new ArrayList<>(query.projectUuids()), IssueQuery::isMainBranch)
      .containsOnly(applicationBranch1.uuid(), Collections.emptyList(), false);

    // Search on project1Branch1
    assertThat(underTest.create(new SearchRequest()
      .setComponents(singletonList(applicationBranch1.getKey()))
      .setProjects(singletonList(project1.getKey()))
      .setBranch(applicationBranch1.getBranch())))
      .extracting(IssueQuery::branchUuid, query -> new ArrayList<>(query.projectUuids()), IssueQuery::isMainBranch)
      .containsOnly(applicationBranch1.uuid(), singletonList(project1.uuid()), false);
  }

  @Test
  public void fail_if_created_after_and_created_since_are_both_set() {
    SearchRequest request = new SearchRequest()
      .setCreatedAfter("2013-07-25T07:35:00+0100")
      .setCreatedInLast("palap");

    try {
      underTest.create(request);
      fail();
    } catch (Exception e) {
      assertThat(e).isInstanceOf(IllegalArgumentException.class).hasMessage("Parameters createdAfter and createdInLast cannot be set simultaneously");
    }
  }

  @Test
  public void set_created_after_from_created_since() {
    Date now = parseDateTime("2013-07-25T07:35:00+0100");
    when(clock.instant()).thenReturn(now.toInstant());
    when(clock.getZone()).thenReturn(ZoneOffset.UTC);
    SearchRequest request = new SearchRequest()
      .setCreatedInLast("1y2m3w4d");
    assertThat(underTest.create(request).createdAfter().date()).isEqualTo(parseDateTime("2012-04-30T07:35:00+0100"));
    assertThat(underTest.create(request).createdAfter().inclusive()).isTrue();

  }

  @Test
  public void fail_if_since_leak_period_and_created_after_set_at_the_same_time() {
    assertThatThrownBy(() -> underTest.create(new SearchRequest()
      .setSinceLeakPeriod(true)
      .setCreatedAfter("2013-07-25T07:35:00+0100")))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("Parameters 'createdAfter' and 'inNewCodePeriod' or 'sinceLeakPeriod' cannot be set simultaneously");
  }

  @Test
  public void fail_if_in_new_code_period_and_created_after_set_at_the_same_time() {
    SearchRequest searchRequest = new SearchRequest()
      .setInNewCodePeriod(true)
      .setCreatedAfter("2013-07-25T07:35:00+0100");

    assertThatThrownBy(() -> underTest.create(searchRequest))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("Parameters 'createdAfter' and 'inNewCodePeriod' or 'sinceLeakPeriod' cannot be set simultaneously");
  }

  @Test
  public void fail_if_since_leak_period_and_created_in_last_set_at_the_same_time() {
    SearchRequest searchRequest = new SearchRequest()
      .setSinceLeakPeriod(true)
      .setCreatedInLast("1y2m3w4d");

    assertThatThrownBy(() -> underTest.create(searchRequest))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("Parameters 'createdInLast' and 'inNewCodePeriod' or 'sinceLeakPeriod' cannot be set simultaneously");
  }

  @Test
  public void fail_if_in_new_code_period_and_created_in_last_set_at_the_same_time() {
    SearchRequest searchRequest = new SearchRequest()
      .setInNewCodePeriod(true)
      .setCreatedInLast("1y2m3w4d");

    assertThatThrownBy(() -> underTest.create(searchRequest))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("Parameters 'createdInLast' and 'inNewCodePeriod' or 'sinceLeakPeriod' cannot be set simultaneously");
  }

  @Test
  public void fail_if_no_component_provided_with_since_leak_period() {
    assertThatThrownBy(() -> underTest.create(new SearchRequest().setInNewCodePeriod(true)))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("One and only one component must be provided when searching in new code period");
  }

  @Test
  public void fail_if_no_component_provided_with_in_new_code_period() {
    SearchRequest searchRequest = new SearchRequest().setInNewCodePeriod(true);

    assertThatThrownBy(() -> underTest.create(searchRequest))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("One and only one component must be provided when searching in new code period");
  }

  @Test
  public void fail_if_several_components_provided_with_since_leak_period() {
    ComponentDto project1 = db.components().insertPrivateProject();
    ComponentDto project2 = db.components().insertPrivateProject();

    assertThatThrownBy(() -> underTest.create(new SearchRequest()
      .setSinceLeakPeriod(true)
      .setComponents(asList(project1.getKey(), project2.getKey()))))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("One and only one component must be provided when searching in new code period");
  }

  @Test
  public void fail_if_several_components_provided_with_in_new_code_period() {
    ComponentDto project1 = db.components().insertPrivateProject();
    ComponentDto project2 = db.components().insertPrivateProject();

    SearchRequest searchRequest = new SearchRequest()
      .setInNewCodePeriod(true)
      .setComponents(asList(project1.getKey(), project2.getKey()));

    assertThatThrownBy(() -> underTest.create(searchRequest))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("One and only one component must be provided when searching in new code period");
  }

  @Test
  public void fail_if_date_is_not_formatted_correctly() {
    assertThatThrownBy(() -> underTest.create(new SearchRequest()
      .setCreatedAfter("unknown-date")))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("'unknown-date' cannot be parsed as either a date or date+time");
  }

}
