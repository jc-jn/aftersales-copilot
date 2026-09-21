package com.aftersales.copilot.catalog.infrastructure.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("product_sku")
public record ProductSkuPo(
        @TableId(type = IdType.INPUT) Long id,
        Long productId,
        String skuCode,
        String specJson,
        Long salePriceCent,
        Integer warrantyMonths,
        Boolean serialRequired,
        String status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime deletedAt
) {
}
