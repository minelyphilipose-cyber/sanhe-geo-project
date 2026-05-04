package com.huanjing.geo.module.content.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huanjing.geo.module.content.entity.WechatComponentTicket;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface WechatComponentTicketMapper extends BaseMapper<WechatComponentTicket> {
    @Insert("""
            INSERT INTO wechat_component_ticket (
              component_appid,
              component_verify_ticket_cipher,
              received_at,
              expires_at,
              created_at,
              updated_at
            ) VALUES (
              #{componentAppid},
              #{componentVerifyTicketCipher},
              #{receivedAt},
              #{expiresAt},
              CURRENT_TIMESTAMP,
              CURRENT_TIMESTAMP
            )
            ON DUPLICATE KEY UPDATE
              component_verify_ticket_cipher = VALUES(component_verify_ticket_cipher),
              received_at = VALUES(received_at),
              expires_at = VALUES(expires_at),
              updated_at = CURRENT_TIMESTAMP
            """)
    int upsertByAppid(WechatComponentTicket ticket);
}
