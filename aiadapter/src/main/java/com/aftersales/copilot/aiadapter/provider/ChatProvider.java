package com.aftersales.copilot.aiadapter.provider;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

public interface ChatProvider {
    CompletableFuture<ProviderResult> structured(String prompt, Map<String,Object> schema);
    record ProviderResult(Map<String,Object> result,String provider,String model,int inputTokens,int outputTokens) {}
}
