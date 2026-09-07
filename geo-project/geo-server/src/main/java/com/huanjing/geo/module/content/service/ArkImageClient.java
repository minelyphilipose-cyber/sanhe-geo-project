package com.huanjing.geo.module.content.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.system.entity.AiPlatformConfig;
import com.huanjing.geo.module.system.service.PlatformCredentialService;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;

@Service
public class ArkImageClient {
    private final ObjectMapper json;
    private final PlatformCredentialService credentials;
    private final HttpClient http;

    @org.springframework.beans.factory.annotation.Autowired
    public ArkImageClient(ObjectMapper json, PlatformCredentialService credentials) {
        this(json, credentials, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20))
                .followRedirects(HttpClient.Redirect.NEVER).build());
    }

    ArkImageClient(ObjectMapper json, PlatformCredentialService credentials, HttpClient http) {
        this.json = json;
        this.credentials = credentials;
        this.http = http;
    }

    public GeneratedImage generate(AiPlatformConfig config, String prompt) throws Exception {
        String key = credentials.resolvePrimaryCredentialStrict(config.getPrimaryKeyRef(), config.getApiKey());
        if (key == null || key.isBlank()) throw new BizException(400, "图片模型密钥未配置或无法读取");
        HttpRequest request = HttpRequest.newBuilder(endpoint(config.getApiUrl()))
                .timeout(Duration.ofMillis(Math.min(600_000, Math.max(300_000,
                        config.getTimeoutMs() == null ? 300_000 : config.getTimeoutMs()))))
                .header("Authorization", "Bearer " + key).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(payload(config.getModelId(), prompt))))
                .build();
        JsonNode response = json.readTree(send(request, 1024 * 1024, "豆包生图"));
        JsonNode data = response.path("data");
        if (!data.isArray() || data.size() != 1 || data.get(0).path("url").asText().isBlank()) {
            throw new BizException(502, "豆包未返回预期的单张图片，请重试该页");
        }
        URI download = downloadUri(data.get(0).path("url").asText());
        byte[] bytes = send(HttpRequest.newBuilder(download).timeout(Duration.ofMinutes(2)).GET().build(),
                50 * 1024 * 1024, "生成图片下载");
        String type = imageType(bytes);
        return new GeneratedImage(bytes, type, "jpg".equals(type) ? "image/jpeg" : "image/" + type);
    }

    static Map<String, Object> payload(String model, String prompt) {
        return Map.of("model", model, "prompt", prompt, "size", "1440x2560", "response_format", "url",
                "watermark", false, "stream", false, "sequential_image_generation", "disabled");
    }

    static URI endpoint(String base) {
        if (base == null) throw new BizException(400, "图片模型接口地址未配置");
        String url = base.trim().replaceAll("/+$", "");
        URI uri = URI.create(url.endsWith("/images/generations") ? url : url + "/images/generations");
        if (!"https".equals(uri.getScheme()) || !"ark.cn-beijing.volces.com".equals(uri.getHost())
                || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null
                || (uri.getPort() != -1 && uri.getPort() != 443)
                || !"/api/v3/images/generations".equals(uri.getPath())) {
            throw new BizException(400, "请配置火山方舟北京接口 https://ark.cn-beijing.volces.com/api/v3");
        }
        return uri;
    }

    static URI downloadUri(String url) {
        URI uri = URI.create(url);
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        if (!"https".equals(uri.getScheme()) || uri.getUserInfo() != null
                || (uri.getPort() != -1 && uri.getPort() != 443)
                || !(host.endsWith(".volces.com") || host.endsWith(".byteimg.com") || host.endsWith(".volccdn.com"))) {
            throw new BizException(502, "豆包返回了不受支持的图片下载地址");
        }
        return uri;
    }

    private byte[] send(HttpRequest request, int limit, String operation) throws Exception {
        HttpResponse<InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
        try (InputStream body = response.body()) {
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw upstreamFailure(request, response, body, operation);
            }
            byte[] bytes = body.readNBytes(limit + 1);
            if (bytes.length == 0 || bytes.length > limit) throw new BizException(502, operation + "返回文件为空或过大");
            return bytes;
        }
    }

    private BizException upstreamFailure(HttpRequest request, HttpResponse<?> response, InputStream body, String operation) {
        StringBuilder message = new StringBuilder(operation + "失败，HTTP " + response.statusCode());
        String requestId = response.headers().firstValue("x-request-id")
                .orElse(response.headers().firstValue("x-tt-logid").orElse(""));
        try {
            // Retain only diagnostic fields, never the full body, prompt, headers or signed URLs.
            byte[] bytes = body.readNBytes(8193);
            if (bytes.length <= 8192) {
                JsonNode root = json.readTree(bytes);
                if (root != null) {
                    JsonNode error = root.path("error");
                    appendDiagnostic(message, "错误码", error.path("code").asText(""), request, 100);
                    appendDiagnostic(message, "说明", error.path("message").asText(""), request, 450);
                    appendDiagnostic(message, "参数", error.path("param").asText(""), request, 80);
                    if (requestId.isBlank()) requestId = root.path("request_id").asText(error.path("request_id").asText(""));
                }
            }
        } catch (Exception ignored) { /* Malformed or unavailable error bodies must preserve the HTTP failure. */ }
        appendDiagnostic(message, "Request ID", requestId, request, 120);
        return new BizException(502, message.toString());
    }

    private static void appendDiagnostic(StringBuilder target, String label, String value, HttpRequest request, int limit) {
        String credential = request.headers().firstValue("Authorization").orElse("").replaceFirst("(?i)^Bearer\\s+", "");
        String safe = credential.isBlank() ? value : value.replace(credential, "[已隐藏]");
        safe = safe.replaceAll("(?i)https?://[^\\s<>]+", "[链接已隐藏]")
                .replaceAll("(?i)Bearer\\s+[^\\s,;]+", "Bearer [已隐藏]")
                .replaceAll("[\\p{Cntrl}\\s]+", " ").trim();
        if (!safe.isBlank()) target.append("；").append(label).append("：")
                .append(safe.length() > limit ? safe.substring(0, limit) + "…" : safe);
    }

    static String imageType(byte[] bytes) {
        if (bytes.length >= 8 && bytes[0] == (byte) 0x89 && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G') return "png";
        if (bytes.length >= 3 && bytes[0] == (byte) 0xff && bytes[1] == (byte) 0xd8 && bytes[2] == (byte) 0xff) return "jpg";
        if (bytes.length >= 12 && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P') return "webp";
        throw new BizException(502, "图片文件格式不支持，仅接受 PNG、JPEG、WebP");
    }

    public record GeneratedImage(byte[] bytes, String extension, String contentType) { }
}
