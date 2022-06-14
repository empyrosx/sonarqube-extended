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

import org.sonar.scanner.scan.branch.BranchConfiguration;
import org.sonar.scanner.scan.branch.BranchType;

import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

@Immutable
public class BranchConfigurationImpl implements BranchConfiguration {
    private final BranchType branchType;
    private final String branchName;
    @Nullable
    private final String referenceBranchName;
    @Nullable
    private final String targetBranchName;
    @Nullable
    private final String pullRequestKey;

    BranchConfigurationImpl(BranchType branchType,
                            String branchName,
                            @Nullable String referenceBranchName,
                            @Nullable String targetBranchName,
                            @Nullable String pullRequestKey) {
        this.branchType = branchType;
        this.branchName = branchName;
        this.referenceBranchName = referenceBranchName;
        this.targetBranchName = targetBranchName;
        this.pullRequestKey = pullRequestKey;
    }

    public BranchType branchType() {
        return this.branchType;
    }

    public String branchName() {
        return this.branchName;
    }

    @CheckForNull
    public String targetBranchName() {
        return this.targetBranchName;
    }

    @CheckForNull
    public String referenceBranchName() {
        return this.referenceBranchName;
    }

    public String pullRequestKey() {
        if (this.branchType != BranchType.PULL_REQUEST) {
            throw new IllegalStateException("Only a branch of type PULL_REQUEST can have a pull request key.");
        } else {
            return this.pullRequestKey;
        }
    }
}
