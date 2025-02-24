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
package org.sonar.server.rule.ws;

import org.junit.Rule;
import org.junit.Test;
import org.sonar.api.resources.Languages;
import org.sonar.api.rule.RuleKey;
import org.sonar.api.rule.RuleStatus;
import org.sonar.api.rule.Severity;
import org.sonar.api.utils.System2;
import org.sonar.core.util.SequenceUuidFactory;
import org.sonar.core.util.UuidFactory;
import org.sonar.db.DbTester;
import org.sonar.db.rule.RuleDto;
import org.sonar.server.es.EsTester;
import org.sonar.server.exceptions.ForbiddenException;
import org.sonar.server.exceptions.UnauthorizedException;
import org.sonar.server.rule.RuleCreator;
import org.sonar.server.rule.RuleDescriptionFormatter;
import org.sonar.server.rule.index.RuleIndexer;
import org.sonar.server.tester.UserSessionRule;
import org.sonar.server.text.MacroInterpreter;
import org.sonar.server.ws.TestRequest;
import org.sonar.server.ws.TestResponse;
import org.sonar.server.ws.WsActionTester;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.sonar.api.rules.RuleType.BUG;
import static org.sonar.api.rules.RuleType.CODE_SMELL;
import static org.sonar.db.permission.GlobalPermission.ADMINISTER_QUALITY_PROFILES;
import static org.sonar.db.rule.RuleDescriptionSectionDto.createDefaultRuleDescriptionSection;
import static org.sonar.db.rule.RuleTesting.newCustomRule;
import static org.sonar.db.rule.RuleTesting.newTemplateRule;
import static org.sonar.server.util.TypeValidationsTesting.newFullTypeValidations;
import static org.sonar.test.JsonAssert.assertJson;

public class CreateActionTest {

  private System2 system2 = mock(System2.class);

  @Rule
  public UserSessionRule userSession = UserSessionRule.standalone();

  @Rule
  public DbTester db = DbTester.create(system2);

  @Rule
  public EsTester es = EsTester.create();

  private UuidFactory uuidFactory = new SequenceUuidFactory();

  private WsActionTester ws = new WsActionTester(new CreateAction(db.getDbClient(),
    new RuleCreator(system2, new RuleIndexer(es.client(), db.getDbClient()), db.getDbClient(), newFullTypeValidations(), uuidFactory),
    new RuleMapper(new Languages(), createMacroInterpreter(), new RuleDescriptionFormatter()),
    new RuleWsSupport(db.getDbClient(), userSession)));

  @Test
  public void check_definition() {
    assertThat(ws.getDef().isPost()).isTrue();
    assertThat(ws.getDef().isInternal()).isFalse();
    assertThat(ws.getDef().responseExampleAsString()).isNotNull();
    assertThat(ws.getDef().description()).isNotNull();
  }

  @Test
  public void create_custom_rule() {
    logInAsQProfileAdministrator();
    // Template rule
    RuleDto templateRule = newTemplateRule(RuleKey.of("java", "S001")).setType(CODE_SMELL);
    db.rules().insert(templateRule);
    db.rules().insertRuleParam(templateRule, param -> param.setName("regex").setType("STRING").setDescription("Reg ex").setDefaultValue(".*"));

    String result = ws.newRequest()
      .setParam("custom_key", "MY_CUSTOM")
      .setParam("template_key", templateRule.getKey().toString())
      .setParam("name", "My custom rule")
      .setParam("markdown_description", "Description")
      .setParam("severity", "MAJOR")
      .setParam("status", "BETA")
      .setParam("type", BUG.name())
      .setParam("params", "regex=a.*")
      .execute().getInput();

    assertJson(result).isSimilarTo("{\n" +
      "  \"rule\": {\n" +
      "    \"key\": \"java:MY_CUSTOM\",\n" +
      "    \"repo\": \"java\",\n" +
      "    \"name\": \"My custom rule\",\n" +
      "    \"htmlDesc\": \"Description\",\n" +
      "    \"severity\": \"MAJOR\",\n" +
      "    \"status\": \"BETA\",\n" +
      "    \"type\": \"BUG\",\n" +
      "    \"internalKey\": \"InternalKeyS001\",\n" +
      "    \"isTemplate\": false,\n" +
      "    \"templateKey\": \"java:S001\",\n" +
      "    \"sysTags\": [\"systag1\", \"systag2\"],\n" +
      "    \"lang\": \"js\",\n" +
      "    \"params\": [\n" +
      "      {\n" +
      "        \"key\": \"regex\",\n" +
      "        \"htmlDesc\": \"Reg ex\",\n" +
      "        \"defaultValue\": \"a.*\",\n" +
      "        \"type\": \"STRING\"\n" +
      "      }\n" +
      "    ]\n" +
      "  }\n" +
      "}\n");
  }

  @Test
  public void create_custom_rule_with_prevent_reactivation_param_to_true() {
    logInAsQProfileAdministrator();
    RuleDto templateRule = newTemplateRule(RuleKey.of("java", "S001"));
    db.rules().insert(templateRule);
    // insert a removed rule
    RuleDto customRule = newCustomRule(templateRule)
      .setRuleKey("MY_CUSTOM")
      .setStatus(RuleStatus.REMOVED)
      .setName("My custom rule")
      .addOrReplaceRuleDescriptionSectionDto(createDefaultRuleDescriptionSection(uuidFactory.create(), "Description"))
      .setDescriptionFormat(RuleDto.Format.MARKDOWN)
      .setSeverity(Severity.MAJOR);
    db.rules().insert(customRule);

    TestResponse response = ws.newRequest()
      .setParam("custom_key", "MY_CUSTOM")
      .setParam("template_key", templateRule.getKey().toString())
      .setParam("name", "My custom rule")
      .setParam("markdown_description", "Description")
      .setParam("severity", "MAJOR")
      .setParam("prevent_reactivation", "true")
      .execute();

    assertThat(response.getStatus()).isEqualTo(409);
    assertJson(response.getInput()).isSimilarTo("{\n" +
      "  \"rule\": {\n" +
      "    \"key\": \"java:MY_CUSTOM\",\n" +
      "    \"repo\": \"java\",\n" +
      "    \"name\": \"My custom rule\",\n" +
      "    \"htmlDesc\": \"Description\",\n" +
      "    \"severity\": \"MAJOR\",\n" +
      "    \"status\": \"REMOVED\",\n" +
      "    \"isTemplate\": false\n" +
      "  }\n" +
      "}\n");
  }

  @Test
  public void create_custom_rule_of_non_existing_template_should_fail() {
    logInAsQProfileAdministrator();

    TestRequest request = ws.newRequest()
      .setParam("custom_key", "MY_CUSTOM")
      .setParam("template_key", "non:existing")
      .setParam("name", "My custom rule")
      .setParam("markdown_description", "Description")
      .setParam("severity", "MAJOR")
      .setParam("prevent_reactivation", "true");

    assertThatThrownBy(request::execute)
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("The template key doesn't exist: non:existing");
  }

  @Test
  public void create_custom_rule_of_removed_template_should_fail() {
    logInAsQProfileAdministrator();

    RuleDto templateRule = db.rules().insert(r -> r.setIsTemplate(true).setStatus(RuleStatus.REMOVED));

    TestRequest request = ws.newRequest()
      .setParam("custom_key", "MY_CUSTOM")
      .setParam("template_key", templateRule.getKey().toString())
      .setParam("name", "My custom rule")
      .setParam("markdown_description", "Description")
      .setParam("severity", "MAJOR")
      .setParam("prevent_reactivation", "true");

    assertThatThrownBy(request::execute)
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("The template key doesn't exist: " + templateRule.getKey());
  }

  @Test
  public void throw_ForbiddenException_if_not_profile_administrator() {
    userSession.logIn();

    assertThatThrownBy(() -> ws.newRequest().execute())
      .isInstanceOf(ForbiddenException.class);
  }

  @Test
  public void throw_UnauthorizedException_if_not_logged_in() {
    assertThatThrownBy(() -> ws.newRequest().execute())
      .isInstanceOf(UnauthorizedException.class);
  }

  private static MacroInterpreter createMacroInterpreter() {
    MacroInterpreter macroInterpreter = mock(MacroInterpreter.class);
    doAnswer(returnsFirstArg()).when(macroInterpreter).interpret(anyString());
    return macroInterpreter;
  }

  private void logInAsQProfileAdministrator() {
    userSession
      .logIn()
      .addPermission(ADMINISTER_QUALITY_PROFILES);
  }

}
