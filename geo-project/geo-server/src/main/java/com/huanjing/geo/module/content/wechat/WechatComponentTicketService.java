package com.huanjing.geo.module.content.wechat;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huanjing.geo.module.content.config.WechatOpenPlatformProperties;
import com.huanjing.geo.module.content.entity.WechatComponentTicket;
import com.huanjing.geo.module.content.mapper.WechatComponentTicketMapper;
import com.huanjing.geo.module.system.service.MpCredentialCipherService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class WechatComponentTicketService {
    private static final Duration REDIS_TTL = Duration.ofMinutes(12);
    private static final Duration TICKET_VALID_FOR = Duration.ofMinutes(10);
    private static final String KEY_PREFIX = "wechat:component_ticket:";

    private final WechatOpenPlatformProperties properties;
    private final StringRedisTemplate redisTemplate;
    private final WechatComponentTicketMapper ticketMapper;
    private final MpCredentialCipherService cipherService;

    public void storeTicket(String componentAppid, String ticket) {
        if (!StringUtils.hasText(ticket)) {
            return;
        }
        String appid = StringUtils.hasText(componentAppid) ? componentAppid : properties.getComponentAppid();
        LocalDateTime receivedAt = LocalDateTime.now();
        try {
            redisTemplate.opsForValue().set(redisKey(appid), ticket, REDIS_TTL);
        } catch (Exception ex) {
            log.warn("Failed to cache WeChat component ticket in Redis, DB fallback still updated");
        }

        WechatComponentTicket row = new WechatComponentTicket();
        row.setComponentAppid(appid);
        row.setComponentVerifyTicketCipher(cipherService.encryptForStorage(ticket));
        row.setReceivedAt(receivedAt);
        row.setExpiresAt(receivedAt.plus(TICKET_VALID_FOR));
        ticketMapper.upsertByAppid(row);
        log.info("WeChat component ticket received componentAppid={} receivedAt={}", appid, receivedAt);
    }

    public String getLatestTicket(String componentAppid) {
        String appid = StringUtils.hasText(componentAppid) ? componentAppid : properties.getComponentAppid();
        try {
            String cached = redisTemplate.opsForValue().get(redisKey(appid));
            if (StringUtils.hasText(cached)) {
                return cached;
            }
        } catch (Exception ex) {
            log.warn("Failed to read WeChat component ticket from Redis, fallback to DB");
        }
        WechatComponentTicket row = ticketMapper.selectOne(new LambdaQueryWrapper<WechatComponentTicket>()
                .eq(WechatComponentTicket::getComponentAppid, appid)
                .last("LIMIT 1"));
        if (row == null || !StringUtils.hasText(row.getComponentVerifyTicketCipher())) {
            return null;
        }
        return cipherService.decrypt(row.getComponentVerifyTicketCipher());
    }

    private String redisKey(String componentAppid) {
        return KEY_PREFIX + componentAppid;
    }
}
