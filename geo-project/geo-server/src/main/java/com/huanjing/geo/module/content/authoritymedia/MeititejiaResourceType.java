package com.huanjing.geo.module.content.authoritymedia;

public enum MeititejiaResourceType {
    NEWS_MEDIA(1, "media_lst", "create_media_order", "query_media_order"),
    WEMEDIA(2, "wmedia_lst", "create_wmedia_order", "query_wmedia_order"),
    VIDEO(6, "video_lst", "create_video_order", "query_video_order"),
    OVERSEAS(7, "overseas_media_lst", "create_overseas_media_order", "query_overseas_media_order");

    private final int idsType;
    private final String listPath;
    private final String createOrderPath;
    private final String queryOrderPath;

    MeititejiaResourceType(int idsType, String listPath, String createOrderPath, String queryOrderPath) {
        this.idsType = idsType;
        this.listPath = listPath;
        this.createOrderPath = createOrderPath;
        this.queryOrderPath = queryOrderPath;
    }

    public int idsType() {
        return idsType;
    }

    public String listPath() {
        return listPath;
    }

    public String createOrderPath() {
        return createOrderPath;
    }

    public String queryOrderPath() {
        return queryOrderPath;
    }
}
