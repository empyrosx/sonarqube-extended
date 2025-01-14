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

import org.sonar.api.utils.MessageException;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.sonar.scanner.bootstrap.ScannerWsClient;
import org.sonar.scanner.protocol.GsonHelper;
import org.sonar.scanner.scan.branch.ProjectPullRequests;
import org.sonar.scanner.scan.branch.ProjectPullRequestsLoader;
import org.sonar.scanner.scan.branch.PullRequestInfo;
import org.sonarqube.ws.client.GetRequest;
import org.sonarqube.ws.client.HttpException;
import org.sonarqube.ws.client.WsResponse;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

public class ProjectPullRequestsLoaderImpl implements ProjectPullRequestsLoader {
    private static final Logger LOG = Loggers.get(ProjectPullRequestsLoaderImpl.class);
    private static final String PROJECT_PULL_REQUESTS_URL = "/api/project_pull_requests/list";
    private final ScannerWsClient wsClient;

    public ProjectPullRequestsLoaderImpl(ScannerWsClient wsClient) {
        this.wsClient = wsClient;
    }

    public ProjectPullRequests load(@Nullable String projectKey) {
        return new ProjectPullRequests(this.loadPullRequests(projectKey));
    }

    private List<PullRequestInfo> loadPullRequests(String projectKey) {
        GetRequest request = new GetRequest(getUrl(projectKey));

        try (WsResponse response = Utils.call(this.wsClient, request)) {
            return parseResponse(response);
        } catch (IOException e) {
            throw MessageException.of("Could not load pull requests from server", e);
        } catch (HttpException e) {
            if (404 == e.code()) {
                return Collections.emptyList();
            } else {
                throw MessageException.of("Could not load pull requests from server", e);
            }
        }
    }

    private static String getUrl(String projectKey) {
        return PROJECT_PULL_REQUESTS_URL + "?project=" + Utils.encodeForUrl(projectKey);
    }

    private static List<PullRequestInfo> parseResponse(WsResponse response) throws IOException {
        try (Reader reader = response.contentReader()) {
            WsProjectPullRequestsResponse pullRequestsResponse = GsonHelper.create().fromJson(reader, WsProjectPullRequestsResponse.class);
            return pullRequestsResponse.pullRequests.stream()
                    .map((data) -> new PullRequestInfo(data.key, data.branch, data.base, data.analysisDate.getTime()))
                    .collect(Collectors.toList());
        }
    }

    private static class WsProjectPullRequestsResponse {
        private List<WsProjectPullRequest> pullRequests = new ArrayList<>();
    }

    private static class WsProjectPullRequest {
        private String key;
        private String branch;
        private String base;
        private Date analysisDate;
    }
}

