package com.aftersales.copilot.ticket.infrastructure.po;
import com.baomidou.mybatisplus.annotation.*; import java.time.LocalDateTime;
@TableName("ticket_message")
public record TicketMessagePo(@TableId(type=IdType.INPUT) Long id,Long ticketId,String senderType,Long senderId,String visibility,String messageType,String content,LocalDateTime createdAt,LocalDateTime updatedAt) {}
