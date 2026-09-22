package com.aftersales.copilot.common.events;

public record TicketAnalysisRequested(long taskId, long ticketId, int ticketVersion) {}
