package com.huanjing.geo.module.content.douyin.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.huanjing.geo.module.content.config.DouyinClientProperties;
import com.huanjing.geo.module.content.douyin.client.dto.DouyinCodeTokenRequest;
import com.huanjing.geo.module.content.douyin.client.dto.DouyinCreateImageTextRequest;
import com.huanjing.geo.module.content.douyin.client.dto.DouyinCreateImageTextResponse;
import com.huanjing.geo.module.content.douyin.client.dto.DouyinImageUploadRequest;
import com.huanjing.geo.module.content.douyin.client.dto.DouyinImageUploadResponse;
import com.huanjing.geo.module.content.douyin.client.dto.DouyinRefreshAccessTokenRequest;
import com.huanjing.geo.module.content.douyin.client.dto.DouyinTokenResponse;
import com.huanjing.geo.module.content.douyin.client.exception.DouyinClientException;
import com.huanjing.geo.module.content.douyin.client.exception.DouyinErrorMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "geo.douyin.client", name = "mode", havingValue = "real")
public class RealDouyinClient implements DouyinClient {
    private static final String ACCESS_TOKEN_HEADER = "access-token";

    private final DouyinClientProperties properties;
    private final ObjectMapper objectMapper;

    @Override
    public DouyinTokenResponse exchangeCodeForToken(DouyinCodeTokenRequest request) {
        List<FormPart> form = List.of(
                new FormPart("client_key", request.getClientKey()),
                new FormPart("client_secret", request.getClientSecret()),
                new FormPart("code", request.getCode()),
                new FormPart("grant_type", blankToDefault(request.getGrantType(), "authorization_code"))
        );
        HttpResponse<String> response = send(formPost("/oauth/access_token/", form));
        JsonNode root = parseRoot(response);
        throwIfFailed(response.statusCode(), root, response.body());
        JsonNode data = root.path("data");
        DouyinTokenResponse token = objectMapper.convertValue(data, DouyinTokenResponse.class);
        token.setMessage(text(root.path("message")));
        token.setLogId(firstText(data.path("log_id"), root.path("log_id"), root.path("extra").path("logid")));
        token.setRawBody(response.body());
        return token;
    }

    @Override
    public DouyinTokenResponse refreshAccessToken(DouyinRefreshAccessTokenRequest request) {
        List<FormPart> form = List.of(
                new FormPart("client_key", request.getClientKey()),
                new FormPart("grant_type", blankToDefault(request.getGrantType(), "refresh_token")),
                new FormPart("refresh_token", request.getRefreshToken())
        );
        HttpResponse<String> response = send(formPost("/oauth/refresh_token/", form));
        JsonNode root = parseRoot(response);
        throwIfFailed(response.statusCode(), root, response.body());
        JsonNode data = root.path("data");
        DouyinTokenResponse token = objectMapper.convertValue(data, DouyinTokenResponse.class);
        token.setMessage(text(root.path("message")));
        token.setLogId(firstText(data.path("log_id"), root.path("log_id"), root.path("extra").path("logid")));
        token.setRawBody(response.body());
        return token;
    }

    @Override
    public DouyinImageUploadResponse uploadImage(DouyinImageUploadRequest request) {
        String boundary = "----geo-douyin-" + UUID.randomUUID().toString().replace("-", "");
        HttpRequest.Builder builder = baseRequest("/api/douyin/v1/video/upload_image/?open_id=" + encode(request.getOpenId()))
                .header(ACCESS_TOKEN_HEADER, required(request.getAccessToken(), "access token"))
                .header("Content-Type", MediaType.MULTIPART_FORM_DATA_VALUE + "; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArrays(multipartImageBody(boundary, request)));

        HttpResponse<String> response = send(builder);
        JsonNode root = parseRoot(response);
        throwIfFailed(response.statusCode(), root, response.body());
        JsonNode data = root.path("data");
        JsonNode image = data.path("image");
        JsonNode extra = root.path("extra");
        DouyinImageUploadResponse upload = objectMapper.convertValue(image, DouyinImageUploadResponse.class);
        upload.setErrorCode(longValue(data.path("error_code")));
        upload.setDescription(firstText(data.path("description"), root.path("message")));
        upload.setExtraErrorCode(longValue(extra.path("error_code")));
        upload.setExtraDescription(text(extra.path("description")));
        upload.setLogId(text(extra.path("logid")));
        upload.setNow(longValue(extra.path("now")));
        upload.setSubErrorCode(longValue(extra.path("sub_error_code")));
        upload.setSubDescription(text(extra.path("sub_description")));
        upload.setRawBody(response.body());
        return upload;
    }

    @Override
    public DouyinCreateImageTextResponse createImageText(DouyinCreateImageTextRequest request) {
        ObjectNode body = objectMapper.createObjectNode();
        body.set("image_list", objectMapper.valueToTree(request.getImageList()));
        putText(body, "text", request.getText());
        putArray(body, "at_users", request.getAtUsers());
        putNumber(body, "download_type", request.getDownloadType());
        putNumber(body, "private_status", request.getPrivateStatus());
        putText(body, "micro_app_id", request.getMicroAppId());
        putText(body, "micro_app_title", request.getMicroAppTitle());
        putText(body, "micro_app_url", request.getMicroAppUrl());
        putNumber(body, "music_id", request.getMusicId());
        putBoolean(body, "poi_commerce", request.getPoiCommerce());
        putText(body, "poi_id", request.getPoiId());
        putNumber(body, "task_id", request.getTaskId());
        putText(body, "agent_client_key", request.getAgentClientKey());

        HttpRequest.Builder builder = baseRequest("/api/douyin/v1/video/create_image_text/?open_id=" + encode(request.getOpenId()))
                .header(ACCESS_TOKEN_HEADER, required(request.getAccessToken(), "access token"))
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .POST(HttpRequest.BodyPublishers.ofString(toJson(body), StandardCharsets.UTF_8));

        HttpResponse<String> response = send(builder);
        JsonNode root = parseRoot(response);
        throwIfFailed(response.statusCode(), root, response.body());
        JsonNode data = root.path("data");
        JsonNode extra = root.path("extra");
        DouyinCreateImageTextResponse create = objectMapper.convertValue(data, DouyinCreateImageTextResponse.class);
        create.setExtraErrorCode(longValue(extra.path("error_code")));
        create.setExtraDescription(text(extra.path("description")));
        create.setLogId(text(extra.path("logid")));
        create.setNow(longValue(extra.path("now")));
        create.setSubErrorCode(longValue(extra.path("sub_error_code")));
        create.setSubDescription(text(extra.path("sub_description")));
        create.setRawBody(response.body());
        return create;
    }

    private HttpRequest.Builder formPost(String path, List<FormPart> parts) {
        return baseRequest(path)
                .header("Content-Type", MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                .POST(HttpRequest.BodyPublishers.ofString(formBody(parts), StandardCharsets.UTF_8));
    }

    private HttpRequest.Builder baseRequest(String pathAndQuery) {
        return HttpRequest.newBuilder(uri(pathAndQuery))
                .timeout(Duration.ofMillis(properties.getRequestTimeoutMs()));
    }

    private URI uri(String pathAndQuery) {
        String baseUrl = properties.getBaseUrl();
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        return URI.create(baseUrl + pathAndQuery);
    }

    private HttpResponse<String> send(HttpRequest.Builder builder) {
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMillis(properties.getConnectTimeoutMs()))
                    .build();
            return client.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new DouyinClientException(0, null, "douyin request failed: " + e.getMessage(), null, true, null);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DouyinClientException(0, null, "douyin request interrupted", null, true, null);
        }
    }

    private JsonNode parseRoot(HttpResponse<String> response) {
        try {
            return objectMapper.readTree(response.body());
        } catch (IOException e) {
            throw new DouyinClientException(response.statusCode(), null, "invalid douyin response json", null, false, response.body());
        }
    }

    private void throwIfFailed(int httpStatus, JsonNode root, String rawBody) {
        Long errorCode = firstNonZeroLong(
                root.path("data").path("error_code"),
                root.path("error_code"),
                root.path("extra").path("error_code"),
                root.path("extra").path("sub_error_code")
        );
        if (httpStatus >= 200 && httpStatus < 300 && errorCode == null) {
            return;
        }
        String description = firstText(
                root.path("data").path("description"),
                root.path("description"),
                root.path("extra").path("description"),
                root.path("extra").path("sub_description"),
                root.path("message")
        );
        String logId = firstText(root.path("data").path("log_id"), root.path("extra").path("logid"), root.path("log_id"));
        throw DouyinErrorMapper.toException(httpStatus, errorCode, description, logId, rawBody);
    }

    private List<byte[]> multipartImageBody(String boundary, DouyinImageUploadRequest request) {
        List<byte[]> body = new ArrayList<>();
        String contentType = blankToDefault(request.getContentType(), MediaType.APPLICATION_OCTET_STREAM_VALUE);
        String filename = blankToDefault(request.getFilename(), "image");
        body.add(("--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"image\"; filename=\"" + filename + "\"\r\n"
                + "Content-Type: " + contentType + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        body.add(request.getImageBytes() == null ? new byte[0] : request.getImageBytes());
        body.add(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return body;
    }

    private String formBody(List<FormPart> parts) {
        StringBuilder builder = new StringBuilder();
        for (FormPart part : parts) {
            if (builder.length() > 0) {
                builder.append('&');
            }
            builder.append(encode(part.name())).append('=').append(encode(part.value()));
        }
        return builder.toString();
    }

    private String encode(String value) {
        return URLEncoder.encode(required(value, "request value"), StandardCharsets.UTF_8);
    }

    private String required(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new DouyinClientException(0, null, "douyin " + name + " is required", null, false, null);
        }
        return value;
    }

    private String toJson(ObjectNode body) {
        try {
            return objectMapper.writeValueAsString(body);
        } catch (IOException e) {
            throw new DouyinClientException(0, null, "failed to serialize douyin request", null, false, null);
        }
    }

    private void putText(ObjectNode body, String name, String value) {
        if (value != null) {
            body.put(name, value);
        }
    }

    private void putArray(ObjectNode body, String name, List<String> value) {
        if (value != null) {
            body.set(name, objectMapper.valueToTree(value));
        }
    }

    private void putNumber(ObjectNode body, String name, Integer value) {
        if (value != null) {
            body.put(name, value);
        }
    }

    private void putNumber(ObjectNode body, String name, Long value) {
        if (value != null) {
            body.put(name, value);
        }
    }

    private void putBoolean(ObjectNode body, String name, Boolean value) {
        if (value != null) {
            body.put(name, value);
        }
    }

    private String blankToDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private String firstText(JsonNode... nodes) {
        for (JsonNode node : nodes) {
            String value = text(node);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private String text(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        String value = node.asText();
        return value == null || value.isBlank() ? null : value;
    }

    private Long firstNonZeroLong(JsonNode... nodes) {
        for (JsonNode node : nodes) {
            Long value = longValue(node);
            if (value != null && value != 0L) {
                return value;
            }
        }
        return null;
    }

    private Long longValue(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (node.isNumber()) {
            return node.longValue();
        }
        String value = node.asText();
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private record FormPart(String name, String value) {
    }
}
