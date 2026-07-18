package com.huanjing.geo.module.extension.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("local_agent_session")
public class LocalAgentSession {
    @TableId(type = IdType.AUTO)
    private Long id;
    /**
     * Optional legacy brand scope. A null value is the normal account-wide helper session and
     * must still be constrained by the operator's current brand access and environment binding.
     */
    private Long brandId;
    private Long operatorId;
    private String accessTokenLookupHash;
    private String accessTokenHash;
    private String accessTokenHashAlg;
    private String accessTokenSalt;
    private String hmacSecret;
    private String deviceSecretHash;
    private String helperName;
    private String userAgent;
    private String status;
    private LocalDateTime boundAt;
    private LocalDateTime lastSeenAt;
    private LocalDateTime expiresAt;
    private LocalDateTime revokedAt;
    private Long revokedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
