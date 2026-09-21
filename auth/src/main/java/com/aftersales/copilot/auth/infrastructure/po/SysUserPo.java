package com.aftersales.copilot.auth.infrastructure.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("sys_user")
public record SysUserPo(
        @TableId(type = IdType.INPUT) Long id,
        String username,
        String email,
        String passwordHash,
        String displayName,
        String role,
        String status,
        Boolean agentOnline,
        LocalDateTime lastAssignedAt,
        LocalDateTime lastLoginAt,
        Integer version,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime deletedAt
) {
}
