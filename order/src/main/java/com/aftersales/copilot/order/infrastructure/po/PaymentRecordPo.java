package com.aftersales.copilot.order.infrastructure.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("payment_record")
public record PaymentRecordPo(
        @TableId(type = IdType.INPUT) Long id,
        String paymentNo,
        Long orderId,
        String channel,
        Long amountCent,
        String status,
        LocalDateTime paidAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
