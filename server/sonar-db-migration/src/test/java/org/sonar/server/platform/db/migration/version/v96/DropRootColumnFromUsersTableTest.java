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
package org.sonar.server.platform.db.migration.version.v96;

import java.sql.SQLException;
import java.sql.Types;
import org.junit.Rule;
import org.junit.Test;
import org.sonar.db.CoreDbTester;
import org.sonar.server.platform.db.migration.step.DdlChange;

public class DropRootColumnFromUsersTableTest {

  private static final String COLUMN_NAME = "is_root";
  private static final String TABLE_NAME = "users";

  @Rule
  public final CoreDbTester db = CoreDbTester.createForSchema(DropRootColumnFromUsersTableTest.class, "schema.sql");

  private final DdlChange dropRuleDescriptionColumn = new DropRootColumnFromUsersTable(db.database());

  @Test
  public void migration_should_drop_is_root_column() throws SQLException {
    db.assertColumnDefinition(TABLE_NAME, COLUMN_NAME, Types.BOOLEAN, null, false);
    dropRuleDescriptionColumn.execute();
    db.assertColumnDoesNotExist(TABLE_NAME, COLUMN_NAME);
  }

  @Test
  public void migration_should_be_reentrant() throws SQLException {
    db.assertColumnDefinition(TABLE_NAME, COLUMN_NAME, Types.BOOLEAN, null, false);
    dropRuleDescriptionColumn.execute();
    // re-entrant
    dropRuleDescriptionColumn.execute();
    db.assertColumnDoesNotExist(TABLE_NAME, COLUMN_NAME);
  }
}