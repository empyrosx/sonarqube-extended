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

import org.sonar.ce.task.projectanalysis.component.Component;
import org.sonar.ce.task.projectanalysis.issue.IssueVisitor;
import org.sonar.core.issue.DefaultIssue;

import java.util.*;

public class PullRequestIssueVisitor extends IssueVisitor {

    private final List<DefaultIssue> issues = new ArrayList<>();

    private final Map<DefaultIssue, String> fileIssues = new HashMap<>();

    @Override
    public void onIssue(Component component, DefaultIssue defaultIssue) {
        if (Component.Type.FILE.equals(component.getType())) {
            Optional<String> scmPath = component.getReportAttributes().getScmPath();
            scmPath.ifPresent(filePath -> fileIssues.put(defaultIssue, filePath));
        }
        issues.add(defaultIssue);
    }

    public List<DefaultIssue> getIssues() {
        return Collections.unmodifiableList(issues);
    }

    public String getFileName(DefaultIssue issue) {
        return fileIssues.get(issue);
    }

}