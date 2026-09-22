package com.aftersales.copilot.common.ai;
public interface AiTaskRetryService { Object latestAnalysis(long ticketId); Object retryTicketAnalysis(long ticketId, long operatorId); }
