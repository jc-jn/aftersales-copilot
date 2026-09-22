package com.aftersales.copilot.common.ai;

public interface AiTaskScheduler {
    void scheduleTicketAnalysis(long ticketId, int ticketVersion, long customerId);
}
