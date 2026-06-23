package com.huanjing.geo.module.content.wechat;

import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.content.config.WechatMpClientProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "geo.wechat.client", name = "mode", havingValue = "mock", matchIfMissing = true)
public class MockWechatMpClient implements WechatMpClient {

    private final WechatMpClientProperties properties;

    @Override
    public QueryAuthResult queryAuth(String authCode) {
        failIf("invalid_credential", 40001, "invalid credential");
        return new QueryAuthResult(
                "mock_authorizer_appid",
                "mock_authorizer_access_token",
                "mock_authorizer_refresh_token",
                7200,
                "[{\"funcscope_category\":{\"id\":1}},{\"funcscope_category\":{\"id\":13}}]"
        );
    }

    @Override
    public AuthorizerInfoResult getAuthorizerInfo(String authorizerAppid) {
        return new AuthorizerInfoResult(
                "Mock 微信公众号",
                "https://mock.local/head.png",
                "https://mock.local/qrcode.png",
                "Mock Principal",
                "0"
        );
    }

    @Override
    public AuthorizerTokenResult refreshAuthorizerToken(String authorizerAppid, String refreshToken) {
        failIf("invalid_credential", 40001, "invalid credential");
        return new AuthorizerTokenResult("mock_refreshed_access_token", "mock_refreshed_refresh_token", 7200);
    }

    @Override
    public MaterialResult addThumbMaterial(String authorizerAccessToken, byte[] content, String filename) {
        failIf("permission_missing", 48001, "api unauthorized");
        failIf("rate_limit", 45009, "reach max api daily quota limit");
        return new MaterialResult("mock_thumb_media_id");
    }

    @Override
    public UploadImageResult uploadContentImage(String authorizerAccessToken, byte[] content, String filename) {
        failIf("permission_missing", 48001, "api unauthorized");
        return new UploadImageResult("https://mmbiz.qpic.cn/mock/" + safeName(filename));
    }

    @Override
    public DraftResult addDraft(String authorizerAccessToken, DraftArticle article) {
        failIf("permission_missing", 48001, "api unauthorized");
        failIf("rate_limit", 45009, "reach max api daily quota limit");
        return new DraftResult("mock_draft_media_id");
    }

    @Override
    public PublishResult submitPublish(String authorizerAccessToken, String mediaId) {
        failIf("permission_missing", 48001, "api unauthorized");
        failIf("rate_limit", 45009, "reach max api daily quota limit");
        return new PublishResult("mock_publish_id");
    }

    @Override
    public PublishStatusResult getPublishStatus(String authorizerAccessToken, String publishId) {
        failIf("invalid_credential", 40001, "invalid credential");
        failIf("permission_missing", 48001, "api unauthorized");
        String articleUrl = "https://mp.weixin.qq.com/s/mock_article";
        String raw = "{\"publish_status\":0,\"article_id\":\"mock_article_id\",\"article_detail\":{\"count\":1,\"item\":[{\"idx\":1,\"article_url\":\""
                + articleUrl
                + "\"}]}}";
        return new PublishStatusResult(0, "mock_article_id", articleUrl, raw, null);
    }

    @Override
    public MaterialCountResult getMaterialCount(String authorizerAccessToken) {
        failIf("invalid_credential", 40001, "invalid credential");
        failIf("permission_missing", 48001, "api unauthorized");
        return new MaterialCountResult(0, 0, 3, 0);
    }

    @Override
    public void sendCustomTextMessage(String authorizerAccessToken, String openid, String content) {
        failIf("permission_missing", 48001, "api unauthorized");
        failIf("customer_window_timeout", 45015, "response out of time limit");
    }

    private void failIf(String fault, int code, String message) {
        if (fault.equals(properties.getFault())) {
            throw new BizException(code, message);
        }
    }

    private String safeName(String filename) {
        return filename == null || filename.isBlank() ? "image.png" : filename.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
