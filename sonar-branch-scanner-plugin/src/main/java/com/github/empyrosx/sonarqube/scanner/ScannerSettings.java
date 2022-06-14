/*
 * Copyright (C) 2022 Zimichev Dmitri
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
package com.github.empyrosx.sonarqube.scanner;

public class ScannerSettings {

    // branch analyze
    public static final String SONAR_BRANCH_NAME = "sonar.branch.name";
    public static final String SONAR_BRANCH_TARGET = "sonar.branch.target";

    // pull request analyze
    public static final String SONAR_PR_KEY = "sonar.pullrequest.key";
    public static final String SONAR_PR_BRANCH = "sonar.pullrequest.branch";
    public static final String SONAR_PR_BASE = "sonar.pullrequest.base";
}
