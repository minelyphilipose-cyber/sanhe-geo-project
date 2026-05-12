package com.huanjing.geo.module.content.wechat;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.function.Supplier;

@Slf4j
@Service
public class WechatOutboundIpService {
    private static final String UNKNOWN = "unknown";
    private static final URI DETECT_URI = URI.create("https://ifconfig.me");
    private static final Duration TIMEOUT = Duration.ofSeconds(2);

    private final String configuredIp;
    private final Supplier<String> detector;
    private volatile String cachedIp = UNKNOWN;

    @Autowired
    public WechatOutboundIpService(@Value("${WECHAT_OUTBOUND_IP:}") String configuredIp) {
        this(configuredIp, WechatOutboundIpService::detectViaIfconfig);
    }

    WechatOutboundIpService(String configuredIp, Supplier<String> detector) {
        this.configuredIp = trimToNull(configuredIp);
        this.detector = detector;
    }

    @PostConstruct
    public void detectOnStartup() {
        if (StringUtils.hasText(configuredIp)) {
            cachedIp = configuredIp;
            return;
        }
        try {
            String detected = trimToNull(detector.get());
            cachedIp = StringUtils.hasText(detected) ? detected : UNKNOWN;
        } catch (Exception ex) {
            cachedIp = UNKNOWN;
            log.warn("Failed to detect WeChat outbound IP, fallback to unknown");
        }
    }

    public String currentOutboundIp() {
        if (StringUtils.hasText(configuredIp)) {
            return configuredIp;
        }
        return StringUtils.hasText(cachedIp) ? cachedIp : UNKNOWN;
    }

    private static String detectViaIfconfig() {
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(TIMEOUT)
                    .build();
            HttpRequest request = HttpRequest.newBuilder(DETECT_URI)
                    .timeout(TIMEOUT)
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return UNKNOWN;
            }
            return response.body();
        } catch (Exception ex) {
            return UNKNOWN;
        }
    }

    private static String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }
}
