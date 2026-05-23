package com.huanjing.geo.module.content.service.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DiscuzForumProfileTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void resolveBoardUsesEnabledDefaultBoardWhenBoardsExist() throws Exception {
        DiscuzForumProfile profile = objectMapper.readValue("""
                {
                  "fid": 1,
                  "baseUrl": "https://bbs.ahv.cc/",
                  "boards": [
                    {"fid": 12, "name": "综合交流", "enabled": true, "default": false},
                    {"fid": 18, "name": "经验分享", "enabled": true, "default": true},
                    {"fid": 31, "name": "闲聊灌水", "enabled": false, "default": false}
                  ]
                }
                """, DiscuzForumProfile.class);

        assertThat(profile.resolveBoard(null)).get().extracting(DiscuzForumProfile.Board::getFid).isEqualTo(18);
        assertThat(profile.resolveBoard(12)).get().extracting(DiscuzForumProfile.Board::getName).isEqualTo("综合交流");
        assertThat(profile.resolveBoard(31)).isEmpty();
    }

    @Test
    void withFidReturnsCopyAndMaterializesPostUrls() {
        DiscuzForumProfile profile = new DiscuzForumProfile();
        profile.setBaseUrl("https://bbs.ahv.cc/");
        profile.setFid(12);
        profile.setPostPageUrl("https://bbs.ahv.cc/forum.php?mod=post&action=newthread&fid=?");

        DiscuzForumProfile copy = profile.withFid(18);

        assertThat(profile.getFid()).isEqualTo(12);
        assertThat(copy.getFid()).isEqualTo(18);
        assertThat(copy.postPageUri().toString())
                .isEqualTo("https://bbs.ahv.cc/forum.php?mod=post&action=newthread&fid=18");
        assertThat(copy.postSubmitUri().toString())
                .isEqualTo("https://bbs.ahv.cc/forum.php?mod=post&action=newthread&fid=18&extra=&topicsubmit=yes");
    }
}
