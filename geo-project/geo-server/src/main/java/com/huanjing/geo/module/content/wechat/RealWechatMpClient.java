package com.huanjing.geo.module.content.wechat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.common.util.HttpClientUtil;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "geo.wechat.client", name = "mode", havingValue = "real")
public class RealWechatMpClient implements WechatMpClient {
    private static final String API_BASE = "https://api.weixin.qq.com";
    private static final int CONNECT_TIMEOUT_MS = 5000;
    private static final int REQUEST_TIMEOUT_MS = 15000;

    private final ObjectMapper objectMapper;
    private final WechatApiErrorHandler errorHandler;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(java.time.Duration.ofMillis(CONNECT_TIMEOUT_MS))
            .build();

    @PostConstruct
    public void warnIfActivated() {
        log.warn("RealWechatMpClient activated. Draft media APIs are still guarded by stage rollout.");
    }

    @Override
    public QueryAuthResult queryAuth(String authCode) {
        throw unsupported();
    }

    @Override
    public AuthorizerInfoResult getAuthorizerInfo(String authorizerAppid) {
        throw unsupported();
    }

    @Override
    public AuthorizerTokenResult refreshAuthorizerToken(String authorizerAppid, String refreshToken) {
        throw unsupported();
    }

    @Override
    public MaterialResult addThumbMaterial(String authorizerAccessToken, byte[] content, String filename) {
        JsonNode root = postMultipart(
                "/cgi-bin/material/add_material",
                authorizerAccessToken,
                Map.of("type", "thumb"),
                "media",
                content,
                filename
        );
        return new MaterialResult(root.path("media_id").asText());
    }

    @Override
    public UploadImageResult uploadContentImage(String authorizerAccessToken, byte[] content, String filename) {
        JsonNode root = postMultipart(
                "/cgi-bin/media/uploadimg",
                authorizerAccessToken,
                Map.of(),
                "media",
                content,
                filename
        );
        return new UploadImageResult(root.path("url").asText());
    }

    @Override
    public DraftResult addDraft(String authorizerAccessToken, DraftArticle article) {
        ObjectNode item = objectMapper.createObjectNode();
        item.put("title", article.title());
        item.put("author", article.author());
        item.put("digest", article.digest());
        item.put("content", article.content());
        item.put("content_source_url", article.contentSourceUrl());
        item.put("thumb_media_id", article.thumbMediaId());
        item.put("need_open_comment", article.needOpenComment());
        item.put("only_fans_can_comment", article.onlyFansCanComment());
        ObjectNode body = objectMapper.createObjectNode();
        body.putArray("articles").add(item);
        JsonNode root = post("/cgi-bin/draft/add", authorizerAccessToken, body);
        return new DraftResult(root.path("media_id").asText());
    }

    @Override
    public PublishResult submitPublish(String authorizerAccessToken, String mediaId) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("media_id", mediaId);
        JsonNode root = post("/cgi-bin/freepublish/submit", authorizerAccessToken, body);
        return new PublishResult(root.path("publish_id").asText());
    }

    @Override
    public PublishStatusResult getPublishStatus(String authorizerAccessToken, String publishId) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("publish_id", publishId);
        JsonNode root = post("/cgi-bin/freepublish/get", authorizerAccessToken, body);
        return new PublishStatusResult(
                root.path("publish_status").asInt(-1),
                root.path("article_id").asText(null),
                firstArticleUrl(root),
                root.toString(),
                root.path("fail_idx").asText(null)
        );
    }

    private String firstArticleUrl(JsonNode root) {
        JsonNode items = root.path("article_detail").path("item");
        if (!items.isArray()) {
            return null;
        }
        String first = null;
        for (JsonNode item : items) {
            String url = item.path("article_url").asText(null);
            if (url == null || url.isBlank()) {
                continue;
            }
            if (item.path("idx").asInt(-1) == 1) {
                return url;
            }
            if (first == null) {
                first = url;
            }
        }
        return first;
    }

    @Override
    public MaterialCountResult getMaterialCount(String authorizerAccessToken) {
        JsonNode root = get("/cgi-bin/material/get_materialcount", authorizerAccessToken);
        return new MaterialCountResult(
                root.path("voice_count").asInt(0),
                root.path("video_count").asInt(0),
                root.path("image_count").asInt(0),
                root.path("news_count").asInt(0)
        );
    }

    @Override
    public void sendCustomTextMessage(String authorizerAccessToken, String openid, String content) {
        ObjectNode text = objectMapper.createObjectNode();
        text.put("content", content);
        ObjectNode body = objectMapper.createObjectNode();
        body.put("touser", openid);
        body.put("msgtype", "text");
        body.set("text", text);
        post("/cgi-bin/message/custom/send", authorizerAccessToken, body);
    }

    private UnsupportedOperationException unsupported() {
        log.error("Real WeChat MP client called but not implemented yet");
        return new UnsupportedOperationException("Real WeChat MP client is not implemented in this stage");
    }

    private JsonNode get(String path, String accessToken) {
        try {
            HttpClientUtil.HttpResult response = HttpClientUtil.get(
                    API_BASE + path + "?access_token=" + accessToken,
                    Map.of(),
                    CONNECT_TIMEOUT_MS,
                    REQUEST_TIMEOUT_MS
            );
            return parse(response.body());
        } catch (BizException ex) {
            log.warn("WeChat MP api failed path={} code={} message={}", path, ex.getCode(), ex.getMessage());
            throw ex;
        } catch (Exception ex) {
            log.error("WeChat MP request error path={}", path, ex);
            throw new BizException(500, "wechat mp request failed");
        }
    }

    private JsonNode post(String path, String accessToken, JsonNode body) {
        try {
            HttpClientUtil.HttpResult response = HttpClientUtil.postJson(
                    API_BASE + path + "?access_token=" + accessToken,
                    Map.of("Content-Type", "application/json"),
                    objectMapper.writeValueAsString(body),
                    CONNECT_TIMEOUT_MS,
                    REQUEST_TIMEOUT_MS
            );
            return parse(response.body());
        } catch (BizException ex) {
            log.warn("WeChat MP api failed path={} code={} message={}", path, ex.getCode(), ex.getMessage());
            throw ex;
        } catch (Exception ex) {
            log.error("WeChat MP request error path={}", path, ex);
            throw new BizException(500, "wechat mp request failed");
        }
    }

    private JsonNode postMultipart(String path,
                                   String accessToken,
                                   Map<String, String> queryParams,
                                   String fieldName,
                                   byte[] content,
                                   String filename) {
        String boundary = "----geo-wechat-" + UUID.randomUUID().toString().replace("-", "");
        try {
            byte[] body = multipartBody(boundary, fieldName, filename, content);
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(API_BASE + path + query(accessToken, queryParams)))
                    .timeout(java.time.Duration.ofMillis(REQUEST_TIMEOUT_MS))
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body));
            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            return parse(response.body());
        } catch (BizException ex) {
            log.warn("WeChat MP multipart api failed path={} code={} message={}", path, ex.getCode(), ex.getMessage());
            throw ex;
        } catch (Exception ex) {
            log.error("WeChat MP multipart request error path={}", path, ex);
            throw new BizException(500, "wechat mp request failed");
        }
    }

    private String query(String accessToken, Map<String, String> params) {
        StringBuilder builder = new StringBuilder("?access_token=").append(accessToken);
        if (params != null) {
            params.forEach((key, value) -> builder.append('&').append(key).append('=').append(value));
        }
        return builder.toString();
    }

    private byte[] multipartBody(String boundary, String fieldName, String filename, byte[] content) {
        String safeName = filename == null || filename.isBlank() ? "image.png" : filename;
        String header = "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"" + fieldName + "\"; filename=\"" + safeName + "\"\r\n"
                + "Content-Type: " + imageContentType(safeName) + "\r\n\r\n";
        String footer = "\r\n--" + boundary + "--\r\n";
        byte[] headerBytes = header.getBytes(StandardCharsets.UTF_8);
        byte[] footerBytes = footer.getBytes(StandardCharsets.UTF_8);
        byte[] fileBytes = content == null ? new byte[0] : content;
        byte[] body = new byte[headerBytes.length + fileBytes.length + footerBytes.length];
        System.arraycopy(headerBytes, 0, body, 0, headerBytes.length);
        System.arraycopy(fileBytes, 0, body, headerBytes.length, fileBytes.length);
        System.arraycopy(footerBytes, 0, body, headerBytes.length + fileBytes.length, footerBytes.length);
        return body;
    }

    private String imageContentType(String filename) {
        String lower = filename == null ? "" : filename.toLowerCase();
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (lower.endsWith(".png")) {
            return "image/png";
        }
        if (lower.endsWith(".gif")) {
            return "image/gif";
        }
        if (lower.endsWith(".bmp")) {
            return "image/bmp";
        }
        return "image/jpeg";
    }

    private JsonNode parse(String body) throws Exception {
        JsonNode root = objectMapper.readTree(body == null ? "{}" : body);
        errorHandler.throwIfError("wechat-mp", root);
        return root;
    }
}
