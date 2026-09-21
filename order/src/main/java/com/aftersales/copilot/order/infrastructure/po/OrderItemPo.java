package com.aftersales.copilot.order.infrastructure.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("order_item")
public record OrderItemPo(
        @TableId(type = IdType.INPUT) Long id,
        Long orderId,
        Long skuId,
        String productNameSnapshot,
        String skuSpecSnapshot,
        Long unitPriceCent,
        Integer quantity,
        Long paidAmountCent,
        Long refundedAmountCent,
        String serialNumber,
        LocalDateTime warrantyExpireAt,
        Integer version,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
