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
package com.github.empyrosx.sonarqube.ce;

import org.sonar.api.PropertyType;
import org.sonar.api.SonarQubeSide;
import org.sonar.api.config.PropertyDefinition;
import org.sonar.api.resources.Qualifiers;
import org.sonar.core.extension.CoreExtension;

/**
 * Implements branch feature.
 */
public class BranchCoreExtension implements CoreExtension {

    private static final String PULL_REQUEST_CATEGORY_LABEL = "Pull Request";
    private static final String GENERAL = "General";
    private static final String GITLAB_INTEGRATION_SUBCATEGORY_LABEL = "Integration With Gitlab";

    @Override
    public String getName() {
        return "Pull requests";
    }

    @Override
    public void load(Context context) {
        if (SonarQubeSide.COMPUTE_ENGINE == context.getRuntime().getSonarQubeSide()) {
            context.addExtensions(BranchReportAnalysisComponentProvider.class, BranchEditionProvider.class);
        }

        context.addExtensions(
                PropertyDefinition.builder("sonar.pullrequest.provider")
                        .onlyOnQualifiers(Qualifiers.PROJECT)
                        .subCategory(PULL_REQUEST_CATEGORY_LABEL)
                        .subCategory(GENERAL)
                        .name("Provider")
                        .type(PropertyType.SINGLE_SELECT_LIST)
                        .options("GitlabServer")
                        .build(),
                PropertyDefinition.builder("sonar.pullrequest.gitlab.url")
                        .onQualifiers(Qualifiers.PROJECT)
                        .subCategory(PULL_REQUEST_CATEGORY_LABEL)
                        .subCategory(GITLAB_INTEGRATION_SUBCATEGORY_LABEL)
                        .name("URL for Gitlab (Server or Cloud) instance")
                        .description("Example: https://ci-server.local/gitlab")
                        .type(PropertyType.STRING)
                        .defaultValue("https://gitlab.com")
                        .build(),
                PropertyDefinition.builder("sonar.pullrequest.gitlab.token")
                        .onQualifiers(Qualifiers.PROJECT)
                        .subCategory(PULL_REQUEST_CATEGORY_LABEL)
                        .subCategory(GITLAB_INTEGRATION_SUBCATEGORY_LABEL)
                        .name("The token for the user to comment to the PR on Gitlab (Server or Cloud) instance")
                        .description("Token used for authentication and commenting to your Gitlab instance")
                        .type(PropertyType.STRING)
                        .build(),
                PropertyDefinition.builder("sonar.pullrequest.gitlab.project")
                        .onQualifiers(Qualifiers.PROJECT)
                        .subCategory(PULL_REQUEST_CATEGORY_LABEL)
                        .subCategory(GITLAB_INTEGRATION_SUBCATEGORY_LABEL)
                        .name("Repository project for the Gitlab (Server or Cloud) instance")
                        .description("The repository project can be user/repo or just project ID")
                        .type(PropertyType.STRING)
                        .build(),
                PropertyDefinition.builder("sonar.pullrequest.gitlab.checker")
                        .onQualifiers(Qualifiers.PROJECT)
                        .subCategory(PULL_REQUEST_CATEGORY_LABEL)
                        .subCategory(GITLAB_INTEGRATION_SUBCATEGORY_LABEL)
                        .name("Pipeline name")
                        .description("Name of pipeline")
                        .type(PropertyType.STRING)
                        .defaultValue("SonarQube")
                        .build()
        );
    }
}
