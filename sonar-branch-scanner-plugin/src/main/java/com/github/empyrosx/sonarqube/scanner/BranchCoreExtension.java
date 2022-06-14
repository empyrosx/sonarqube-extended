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

import org.sonar.api.SonarQubeSide;
import org.sonar.core.extension.CoreExtension;

/**
 * Scanner core extension for branch analyze.
 */
public class BranchCoreExtension implements CoreExtension {

    public String getName() {
        return "branch-scanner";
    }

    public void load(Context context) {
        if (context.getRuntime().getSonarQubeSide() == SonarQubeSide.SCANNER) {
            context.addExtensions(BranchParamsValidatorImpl.class, BranchConfigurationLoaderImpl.class,
                    ProjectBranchesLoaderImpl.class, ProjectPullRequestsLoaderImpl.class);
        }
    }
}