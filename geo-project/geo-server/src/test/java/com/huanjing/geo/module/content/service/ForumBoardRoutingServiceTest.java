package com.huanjing.geo.module.content.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.module.customer.entity.Brand;
import com.huanjing.geo.module.system.entity.PublishSite;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ForumBoardRoutingServiceTest {

    private final ForumBoardRoutingService service = new ForumBoardRoutingService(new ObjectMapper());

    @Test
    void resolveForumFidPrefersFuyangBoardForFuyangServiceArea() {
        Brand brand = brand("美容美业", "阜阳地区");

        assertThat(service.resolveForumFid(site(), null, brand, null)).isEqualTo(20);
    }

    @Test
    void resolveForumFidUsesIndustryContainsBoardNameWhenRegionDoesNotMatch() {
        Brand brand = brand("本地家政保洁服务", "合肥地区");

        assertThat(service.resolveForumFid(site(), null, brand, null)).isEqualTo(30);
    }

    @Test
    void resolveForumFidFallsBackToDefaultBoard() {
        Brand brand = brand("汽车维修", "合肥地区");

        assertThat(service.resolveForumFid(site(), null, brand, null)).isEqualTo(10);
    }

    @Test
    void resolveForumFidKeepsRequestedFidAsManualOverride() {
        Brand brand = brand("本地家政保洁服务", "阜阳地区");

        assertThat(service.resolveForumFid(site(), null, brand, 30)).isEqualTo(30);
    }

    private PublishSite site() {
        PublishSite site = new PublishSite();
        site.setIntegrationMethod("discuz_http");
        site.setContentConstraints("""
                {
                  "baseUrl": "https://bbs.ahv.cc/",
                  "boards": [
                    {"fid": 10, "name": "综合", "enabled": true, "default": true},
                    {"fid": 20, "name": "阜阳", "enabled": true, "default": false},
                    {"fid": 30, "name": "家政", "enabled": true, "default": false}
                  ]
                }
                """);
        return site;
    }

    private Brand brand(String industry, String serviceArea) {
        Brand brand = new Brand();
        brand.setIndustry(industry);
        brand.setServiceArea(serviceArea);
        return brand;
    }
}
