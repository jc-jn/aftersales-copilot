package com.aftersales.copilot.ticket.infrastructure.po;
import com.baomidou.mybatisplus.annotation.*; import java.time.LocalDateTime;
@TableName("service_ticket")
public record ServiceTicketPo(@TableId(type=IdType.INPUT) Long id,String ticketNo,Long customerId,Long orderId,Long orderItemId,String requestedType,String confirmedType,String status,String priority,String title,String description,Long assignedAgentId,LocalDateTime firstResponseAt,LocalDateTime resolvedAt,LocalDateTime closedAt,String resolutionCode,String resolutionNote,Integer version,LocalDateTime createdAt,LocalDateTime updatedAt) {}
