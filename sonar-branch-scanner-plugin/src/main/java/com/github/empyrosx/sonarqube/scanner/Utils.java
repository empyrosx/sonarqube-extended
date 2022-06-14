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

import org.sonarqube.ws.client.WsRequest;
import org.sonarqube.ws.client.WsResponse;

import javax.annotation.Nullable;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.InvocationTargetException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class Utils {

    private Utils() {
    }

    public static String encodeForUrl(@Nullable String url) {
        try {
            return URLEncoder.encode(url == null ? "" : url, StandardCharsets.UTF_8.name());

        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException("Encoding is not supported", e);
        }
    }

    public static WsResponse call(Object wsClient, WsRequest request) {
        try {
            return (WsResponse) wsClient.getClass().getMethod("call", WsRequest.class).invoke(wsClient, request);
        } catch (ReflectiveOperationException ex) {
//            ex.printStackTrace();
//            handleIfInvocationException(ex);
            throw (RuntimeException) ex.getCause();
//            throw new IllegalStateException("Could not execute ScannerWsClient", ex);
        }
    }

    private static void handleIfInvocationException(ReflectiveOperationException ex) {
        if (!(ex instanceof InvocationTargetException)) {
            return;
        }
        Throwable cause = ex.getCause();
        if (cause instanceof Error) {
            throw (Error) cause;
        } else if (cause instanceof RuntimeException) {
            throw (RuntimeException) cause;
        }
    }

}
