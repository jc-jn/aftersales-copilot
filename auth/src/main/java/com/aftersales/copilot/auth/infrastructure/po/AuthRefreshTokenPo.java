package com.aftersales.copilot.auth.infrastructure.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("auth_refresh_token")
public record AuthRefreshTokenPo(
        @TableId(type = IdType.INPUT) Long id,
        Long userId,
        String tokenHash,
        LocalDateTime expiresAt,
        LocalDateTime revokedAt,
        String deviceInfo,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
