package com.aftersales.copilot.catalog.infrastructure.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("product")
public record ProductPo(
        @TableId(type = IdType.INPUT) Long id,
        String productCode,
        String name,
        String brand,
        String category,
        String description,
        String status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime deletedAt
) {
}
