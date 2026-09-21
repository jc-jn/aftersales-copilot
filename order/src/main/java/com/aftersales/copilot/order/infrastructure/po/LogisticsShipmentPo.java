package com.aftersales.copilot.order.infrastructure.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("logistics_shipment")
public record LogisticsShipmentPo(
        @TableId(type = IdType.INPUT) Long id,
        String shipmentNo,
        Long orderId,
        String type,
        String carrierCode,
        String trackingNo,
        String status,
        LocalDateTime shippedAt,
        LocalDateTime deliveredAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
