package com.aftersales.copilot.catalog.infrastructure.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("warranty_rule")
public record WarrantyRulePo(
        @TableId(type = IdType.INPUT) Long id,
        String ruleCode,
        String name,
        String scopeType,
        Long scopeId,
        Integer refundOnlyDays,
        Integer returnRefundDays,
        Integer exchangeDays,
        Integer repairDays,
        Boolean requiresUnopened,
        LocalDateTime effectiveFrom,
        LocalDateTime effectiveTo,
        String status,
        String ruleJson,
        Integer version,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
