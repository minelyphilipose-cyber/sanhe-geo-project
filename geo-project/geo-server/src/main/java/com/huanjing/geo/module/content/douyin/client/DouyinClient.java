package com.huanjing.geo.module.content.douyin.client;

import com.huanjing.geo.module.content.douyin.client.dto.DouyinCodeTokenRequest;
import com.huanjing.geo.module.content.douyin.client.dto.DouyinCreateImageTextRequest;
import com.huanjing.geo.module.content.douyin.client.dto.DouyinCreateImageTextResponse;
import com.huanjing.geo.module.content.douyin.client.dto.DouyinImageUploadRequest;
import com.huanjing.geo.module.content.douyin.client.dto.DouyinImageUploadResponse;
import com.huanjing.geo.module.content.douyin.client.dto.DouyinRefreshAccessTokenRequest;
import com.huanjing.geo.module.content.douyin.client.dto.DouyinTokenResponse;

public interface DouyinClient {

    /**
     * Exchange an OAuth authorization code for a user access token.
     *
     * <p>Official API: POST https://open.douyin.com/oauth/access_token/</p>
     * <p>Docs reviewed: 2026-05-05
     * https://partner.open-douyin.com/docs/resource/zh-CN/dop/develop/openapi/account-permission/get-access-token</p>
     */
    DouyinTokenResponse exchangeCodeForToken(DouyinCodeTokenRequest request);

    /**
     * Refresh an expired or near-expired user access token using refresh_token.
     *
     * <p>Official API: POST https://open.douyin.com/oauth/refresh_token/</p>
     * <p>Docs reviewed: 2026-05-05
     * https://partner.open-douyin.com/docs/resource/zh-CN/dop/develop/openapi/account-permission/refresh-access-token</p>
     */
    DouyinTokenResponse refreshAccessToken(DouyinRefreshAccessTokenRequest request);

    /**
     * Upload one image and return Douyin image metadata.
     *
     * <p>Official API:
     * POST https://open.douyin.com/api/douyin/v1/video/upload_image/?open_id={open_id}</p>
     * <p>Docs reviewed: 2026-05-05
     * https://partner.open-douyin.com/docs/resource/zh-CN/dop/develop/openapi/video-management/douyin/create-image-text/image-upload</p>
     */
    DouyinImageUploadResponse uploadImage(DouyinImageUploadRequest request);

    /**
     * Create a Douyin image-text post with previously uploaded image ids.
     *
     * <p>Official API:
     * POST https://open.douyin.com/api/douyin/v1/video/create_image_text/?open_id={open_id}</p>
     * <p>Docs reviewed: 2026-05-05
     * https://partner.open-douyin.com/docs/resource/zh-CN/dop/develop/openapi/video-management/douyin/create-image-text/create-image-text</p>
     */
    DouyinCreateImageTextResponse createImageText(DouyinCreateImageTextRequest request);
}
