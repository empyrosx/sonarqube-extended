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
package org.sonar.db.rule;

import org.assertj.core.api.Assertions;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

public class RuleDescriptionSectionDtoTest {
  private static final RuleDescriptionSectionDto SECTION = RuleDescriptionSectionDto.builder()
    .key("key")
    .uuid("uuid")
    .content("desc").build();


  @Test
  public void setDefault_whenKeyAlreadySet_shouldThrow() {
    RuleDescriptionSectionDto.RuleDescriptionSectionDtoBuilder builderWithKey = RuleDescriptionSectionDto.builder()
      .key("tagada");
    assertThatExceptionOfType(IllegalArgumentException.class)
      .isThrownBy(builderWithKey::setDefault)
      .withMessage("Only one of setDefault and key methods can be called");
  }

  @Test
  public void setKey_whenDefaultAlreadySet_shouldThrow() {
    RuleDescriptionSectionDto.RuleDescriptionSectionDtoBuilder builderWithDefault = RuleDescriptionSectionDto.builder()
      .setDefault();
    assertThatExceptionOfType(IllegalArgumentException.class)
      .isThrownBy(() -> builderWithDefault.key("balbal"))
      .withMessage("Only one of setDefault and key methods can be called");
  }


  @Test
  public void testToString() {
    Assertions.assertThat(SECTION).hasToString("RuleDescriptionSectionDto[uuid='uuid', key='key', content='desc']");
  }
}
