package com.aftersales.copilot.order.infrastructure.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("logistics_event")
public record LogisticsEventPo(
        @TableId(type = IdType.INPUT) Long id,
        Long shipmentId,
        LocalDateTime eventTime,
        String statusCode,
        String description,
        String location,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
