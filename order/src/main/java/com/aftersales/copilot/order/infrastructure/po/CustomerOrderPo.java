package com.aftersales.copilot.order.infrastructure.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("customer_order")
public record CustomerOrderPo(
        @TableId(type = IdType.INPUT) Long id,
        String orderNo,
        Long customerId,
        String status,
        Long totalAmountCent,
        Long paidAmountCent,
        LocalDateTime paidAt,
        LocalDateTime shippedAt,
        LocalDateTime deliveredAt,
        String receiverSnapshot,
        Integer version,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
