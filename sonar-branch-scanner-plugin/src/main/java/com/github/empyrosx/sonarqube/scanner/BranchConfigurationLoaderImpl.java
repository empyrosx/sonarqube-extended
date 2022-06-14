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

import org.apache.commons.lang.StringUtils;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.sonar.scanner.scan.branch.*;

import java.util.*;

import static com.github.empyrosx.sonarqube.scanner.ScannerSettings.*;

public class BranchConfigurationLoaderImpl implements BranchConfigurationLoader {
    private static final Logger LOG = Loggers.get(BranchConfigurationLoaderImpl.class);
    private static final Set<String> BRANCH_PARAMETERS = new HashSet<>(Arrays.asList(SONAR_BRANCH_NAME));
    private static final Set<String> PULL_REQUEST_PARAMETERS = new HashSet<>(Arrays.asList(SONAR_PR_KEY, SONAR_PR_BRANCH, SONAR_PR_BASE));

    public BranchConfigurationLoaderImpl() {
    }

    @Override
    public BranchConfiguration load(Map<String, String> localSettings, ProjectBranches branches, ProjectPullRequests pullRequests) {
        if (BRANCH_PARAMETERS.stream().anyMatch(localSettings::containsKey)) {
            return createBranchConfiguration(localSettings.get(SONAR_BRANCH_NAME), branches);
        }

        if (PULL_REQUEST_PARAMETERS.stream().anyMatch(localSettings::containsKey)) {
            return createPullRequestConfiguration(localSettings, branches, pullRequests);
        }

        return new DefaultBranchConfiguration();
    }

    private static BranchConfiguration createBranchConfiguration(String branchName, ProjectBranches branches) {
        BranchInfo existingBranch = branches.get(branchName);

        if (null == existingBranch) {
            return new BranchConfigurationImpl(BranchType.BRANCH, branchName, branches.defaultBranchName(), null, null);
        }

        return new BranchConfigurationImpl(existingBranch.type(), branchName, existingBranch.name(), null, null);
    }

    private static BranchConfiguration createPullRequestConfiguration(Map<String, String> localSettings, ProjectBranches branches, ProjectPullRequests pullRequests) {
        String pullRequestKey = StringUtils.trimToNull(localSettings.get(SONAR_PR_KEY));
        String pullRequestBranch = StringUtils.trimToNull(localSettings.get(SONAR_PR_BRANCH));
        String pullRequestBase = StringUtils.trimToNull(localSettings.get(SONAR_PR_BASE));
        if (null == pullRequestBase || pullRequestBase.isEmpty()) {
            return new BranchConfigurationImpl(BranchType.PULL_REQUEST, pullRequestBranch, branches.defaultBranchName(),
                    branches.defaultBranchName(), pullRequestKey);
        } else {
            return new BranchConfigurationImpl(BranchType.PULL_REQUEST, pullRequestBranch,
                    Optional.ofNullable(branches.get(pullRequestBase))
                            .map(b -> pullRequestBase)
                            .orElse(branches.defaultBranchName()),
                    pullRequestBase, pullRequestKey);
        }
    }
}
