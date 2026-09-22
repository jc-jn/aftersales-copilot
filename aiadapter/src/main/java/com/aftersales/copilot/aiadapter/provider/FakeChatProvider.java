package com.aftersales.copilot.aiadapter.provider;

import org.springframework.context.annotation.Profile; import org.springframework.stereotype.Component;
import java.util.Map; import java.util.concurrent.CompletableFuture;

@Component @Profile("!real-ai")
public class FakeChatProvider implements ChatProvider {
    public CompletableFuture<ProviderResult> structured(String prompt, Map<String,Object> schema) {
        String intent = prompt != null && prompt.contains("退货") ? "RETURN_REFUND" : prompt != null && prompt.contains("换") ? "EXCHANGE" : prompt != null && prompt.contains("维修") ? "REPAIR" : "REFUND_ONLY";
        Map<String,Object> result = new java.util.LinkedHashMap<>(); result.put("intent",intent); result.put("confidence",0.72); result.put("prioritySuggestion","MEDIUM"); result.put("sentiment","NEUTRAL"); result.put("extracted",Map.of("issue","UNKNOWN")); result.put("missingFields",java.util.List.of()); result.put("needsHuman",false); result.put("riskFlags",java.util.List.of()); result.put("replySuggestion","您好，我们已收到您的售后问题，将由客服继续处理。"); result.put("proposalSuggestion",null); result.put("citations",java.util.List.of());
        return CompletableFuture.completedFuture(new ProviderResult(result,"fake","fake-v1",prompt==null?0:prompt.length(),40));
    }
}
