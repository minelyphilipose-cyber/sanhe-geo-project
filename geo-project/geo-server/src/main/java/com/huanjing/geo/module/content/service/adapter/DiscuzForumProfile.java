package com.huanjing.geo.module.content.service.adapter;

import lombok.Data;
import org.springframework.util.StringUtils;

import java.net.URI;

@Data
public class DiscuzForumProfile {
    private String baseUrl;
    private String loginPageUrl;
    private String loginSubmitUrl;
    private String postPageUrl;
    private String postSubmitUrl;
    private Integer fid = 317;
    private Integer connectTimeoutMs = 5000;
    private Integer requestTimeoutMs = 30000;
    private Boolean rememberLogin = true;
    private String successUrlRegex = "(thread|forum)\\-\\d+";

    public URI baseUri() {
        String value = StringUtils.hasText(baseUrl) ? baseUrl : "https://www.right.com.cn/forum/";
        if (!value.endsWith("/")) {
            value = value + "/";
        }
        return URI.create(value);
    }

    public URI loginPageUri() {
        return resolve(StringUtils.hasText(loginPageUrl) ? loginPageUrl : "forum.php?mod=post&action=newthread&fid=" + fid);
    }

    public URI loginSubmitUri() {
        return resolve(StringUtils.hasText(loginSubmitUrl)
                ? loginSubmitUrl
                : "member.php?mod=logging&action=login&loginsubmit=yes&infloat=yes&lssubmit=yes");
    }

    public URI postPageUri() {
        return resolve(StringUtils.hasText(postPageUrl) ? postPageUrl : "forum.php?mod=post&action=newthread&fid=" + fid);
    }

    public URI postSubmitUri() {
        return resolve(StringUtils.hasText(postSubmitUrl)
                ? postSubmitUrl
                : "forum.php?mod=post&action=newthread&fid=" + fid + "&extra=&topicsubmit=yes");
    }

    private URI resolve(String value) {
        URI uri = URI.create(value);
        return uri.isAbsolute() ? uri : baseUri().resolve(uri);
    }
}
