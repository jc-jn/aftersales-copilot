package com.aftersales.copilot.ticket.infrastructure.po;
import com.baomidou.mybatisplus.annotation.*; import java.time.LocalDateTime;
@TableName("ticket_timeline")
public record TicketTimelinePo(@TableId(type=IdType.INPUT) Long id,Long ticketId,String eventType,String actorType,Long actorId,String summary,String detailJson,LocalDateTime createdAt) {}
