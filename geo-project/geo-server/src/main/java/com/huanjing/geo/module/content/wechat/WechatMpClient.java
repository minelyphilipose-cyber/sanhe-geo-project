package com.huanjing.geo.module.content.wechat;

import java.util.List;

public interface WechatMpClient {

    QueryAuthResult queryAuth(String authCode);

    AuthorizerInfoResult getAuthorizerInfo(String authorizerAppid);

    AuthorizerTokenResult refreshAuthorizerToken(String authorizerAppid, String refreshToken);

    MaterialResult addThumbMaterial(String authorizerAccessToken, byte[] content, String filename);

    UploadImageResult uploadContentImage(String authorizerAccessToken, byte[] content, String filename);

    DraftResult addDraft(String authorizerAccessToken, DraftArticle article);

    PublishResult submitPublish(String authorizerAccessToken, String mediaId);

    PublishStatusResult getPublishStatus(String authorizerAccessToken, String publishId);

    MaterialCountResult getMaterialCount(String authorizerAccessToken);

    MenuResult getMenu(String authorizerAccessToken);

    void createMenu(String authorizerAccessToken, String menuJson);

    void sendCustomTextMessage(String authorizerAccessToken, String openid, String content);

    record QueryAuthResult(
            String authorizerAppid,
            String authorizerAccessToken,
            String authorizerRefreshToken,
            int expiresIn,
            String funcInfoJson
    ) {
    }

    record AuthorizerInfoResult(
            String accountName,
            String headImg,
            String qrcodeUrl,
            String principalName,
            String verifyTypeInfo
    ) {
    }

    record AuthorizerTokenResult(String authorizerAccessToken, String authorizerRefreshToken, int expiresIn) {
    }

    record MaterialResult(String mediaId) {
    }

    record UploadImageResult(String url) {
    }

    record DraftResult(String mediaId) {
    }

    record PublishResult(String publishId) {
    }

    record PublishStatusResult(int publishStatus, String articleId, String articleUrl, String rawResponse, String failIndex) {
        public PublishStatusResult(int publishStatus, String articleId, String rawResponse, String failIndex) {
            this(publishStatus, articleId, null, rawResponse, failIndex);
        }
    }

    record MaterialCountResult(int voiceCount, int videoCount, int imageCount, int newsCount) {
    }

    record MenuResult(String rawResponse) {
    }

    record DraftArticle(
            String title,
            String author,
            String digest,
            String content,
            String contentSourceUrl,
            String thumbMediaId,
            int needOpenComment,
            int onlyFansCanComment
    ) {
    }

    record WechatError(int errcode, String errmsg) {
        public boolean isCredentialError() {
            return errcode == 40001 || errcode == 42001;
        }

        public boolean isPermissionError() {
            return errcode == 48001;
        }

        public boolean isRateLimited() {
            return List.of(45009, 45011).contains(errcode);
        }
    }
}
