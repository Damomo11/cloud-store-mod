package com.cloudstore.client.api;

import com.cloudstore.client.config.CloudStoreConfig;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public final class CloudStoreApi {
    private static final Gson GSON = new Gson();
    private final CloudStoreConfig config;
    private final HttpClient client;
    private volatile String sessionCookie = "";

    public CloudStoreApi(CloudStoreConfig config) {
        this.config = config;
        this.client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(6))
            .build();
    }

    public CompletableFuture<CloudStoreModels.MeResponse> connect() {
        return get("/api/me", CloudStoreModels.MeResponse.class).thenCompose(me -> {
            if (me.loggedIn) {
                return CompletableFuture.completedFuture(me);
            }
            if (config.loginToken.isBlank()) {
                return CompletableFuture.failedFuture(new ApiException("请先在设置中填写云仓库登录密码。"));
            }
            return post("/api/login", Map.of("token", config.loginToken), CloudStoreModels.ActionResponse.class)
                .thenCompose(ignored -> get("/api/me", CloudStoreModels.MeResponse.class))
                .thenCompose(result -> result.loggedIn && result.user != null
                    ? CompletableFuture.completedFuture(result)
                    : CompletableFuture.failedFuture(new ApiException("登录后会话未建立，请检查服务端 Cookie 或反向代理配置。")));
        });
    }

    public CompletableFuture<CloudStoreModels.InventoryResponse> inventory(String ownerUuid) {
        String owner = URLEncoder.encode(ownerUuid, StandardCharsets.UTF_8);
        return get("/api/inventory?owner=" + owner, CloudStoreModels.InventoryResponse.class);
    }

    public CompletableFuture<CloudStoreModels.ActionResponse> deposit(String ownerUuid) {
        return post("/api/action/deposit", Map.of("ownerUuid", ownerUuid), CloudStoreModels.ActionResponse.class);
    }

    public CompletableFuture<CloudStoreModels.ActionResponse> withdraw(
        String ownerUuid,
        CloudStoreModels.Item item,
        long amount,
        String targetUsername
    ) {
        return withdrawMany(ownerUuid, List.of(new CloudStoreModels.WithdrawRequest(item, amount)), targetUsername);
    }

    public CompletableFuture<CloudStoreModels.ActionResponse> withdrawMany(
        String ownerUuid,
        List<CloudStoreModels.WithdrawRequest> items,
        String targetUsername
    ) {
        return post("/api/action/withdraw", Map.of(
            "ownerUuid", ownerUuid,
            "items", items,
            "targetUsername", targetUsername
        ), CloudStoreModels.ActionResponse.class);
    }

    private <T> CompletableFuture<T> get(String path, Class<T> type) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri(path))
            .timeout(Duration.ofSeconds(12))
            .header("Accept", "application/json")
            .GET();
        addSessionCookie(builder);
        return send(builder.build(), type);
    }

    private <T> CompletableFuture<T> post(String path, Object body, Class<T> type) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri(path))
            .timeout(Duration.ofSeconds(12))
            .header("Accept", "application/json")
            .header("Content-Type", "application/json; charset=utf-8")
            .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body), StandardCharsets.UTF_8));
        addSessionCookie(builder);
        return send(builder.build(), type);
    }

    private void addSessionCookie(HttpRequest.Builder builder) {
        String cookie = sessionCookie;
        if (!cookie.isBlank()) builder.header("Cookie", cookie);
    }

    private URI uri(String path) {
        try {
            return URI.create(CloudStoreConfig.normalizeUrl(config.apiBaseUrl) + path);
        } catch (IllegalArgumentException exception) {
            throw new ApiException("云仓库地址格式无效。", exception);
        }
    }

    private <T> CompletableFuture<T> send(HttpRequest request, Class<T> type) {
        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
            .thenApply(response -> {
                captureSessionCookie(response);
                return parseResponse(response, type);
            })
            .exceptionally(error -> {
                Throwable cause = unwrap(error);
                if (cause instanceof ApiException apiException) {
                    throw apiException;
                }
                if (cause instanceof java.net.http.HttpTimeoutException) {
                    throw new ApiException("连接云仓库超时。", cause);
                }
                if (cause instanceof IOException) {
                    throw new ApiException("无法连接云仓库，请检查服务地址。", cause);
                }
                throw new ApiException(cause.getMessage() == null ? "云仓库请求失败。" : cause.getMessage(), cause);
            });
    }

    private void captureSessionCookie(HttpResponse<String> response) {
        for (String value : response.headers().allValues("set-cookie")) {
            String cookie = value.split(";", 2)[0].trim();
            if (cookie.startsWith("cs_sid=") && cookie.length() > "cs_sid=".length()) {
                sessionCookie = cookie;
                return;
            }
        }
    }

    private <T> T parseResponse(HttpResponse<String> response, Class<T> type) {
        String body = response.body();
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new ApiException(errorMessage(body, "请求失败（HTTP " + response.statusCode() + "）。"));
        }
        try {
            return GSON.fromJson(body, type);
        } catch (RuntimeException exception) {
            throw new ApiException("云仓库返回了无法识别的数据。", exception);
        }
    }

    private String errorMessage(String body, String fallback) {
        try {
            JsonObject object = JsonParser.parseString(body).getAsJsonObject();
            if (object.has("error")) {
                return object.get("error").getAsString();
            }
        } catch (RuntimeException ignored) {
        }
        return fallback;
    }

    private Throwable unwrap(Throwable error) {
        Throwable current = error;
        while ((current instanceof CompletionException || current instanceof java.util.concurrent.ExecutionException)
            && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    public static final class ApiException extends RuntimeException {
        public ApiException(String message) {
            super(message);
        }

        public ApiException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
