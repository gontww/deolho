package com.deolho.client;

import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.concurrent.CompletableFuture;

/**
 * Core utility client to transmit captured exception events asynchronously.
 */
public class DeOlhoClient {

    private static final HttpClient httpClient = HttpClient.newHttpClient();

    public static void report(String serverUrl, String appName, String environment, Throwable throwable) {
        if (serverUrl == null || serverUrl.isBlank()) {
            return;
        }

        CompletableFuture.runAsync(() -> {
            try {
                String host = InetAddress.getLocalHost().getHostName();
                String exceptionName = throwable.getClass().getName();
                String message = throwable.getMessage() != null ? throwable.getMessage() : "";

                StringWriter sw = new StringWriter();
                PrintWriter pw = new PrintWriter(sw);
                throwable.printStackTrace(pw);
                String stacktrace = sw.toString();

                String jsonPayload = String.format(
                    "{\"application\":\"%s\",\"level\":\"ERROR\",\"message\":\"%s\",\"exception\":\"%s\",\"stacktrace\":\"%s\",\"host\":\"%s\",\"environment\":\"%s\"}",
                    escapeJson(appName),
                    escapeJson(message),
                    escapeJson(exceptionName),
                    escapeJson(stacktrace),
                    escapeJson(host),
                    escapeJson(environment)
                );

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(serverUrl))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                        .build();

                httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                        .thenAccept(response -> {
                            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                                System.err.println("[DeOlho] Failed to deliver exception report: Status " + response.statusCode());
                            }
                        });
            } catch (Exception e) {
                // Fail silently to avoid breaking the host application
                System.err.println("[DeOlho] Failed sending log event: " + e.getMessage());
            }
        });
    }

    private static String escapeJson(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\b", "\\b")
                .replace("\f", "\\f")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
