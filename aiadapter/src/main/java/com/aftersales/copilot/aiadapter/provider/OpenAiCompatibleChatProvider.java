package com.aftersales.copilot.aiadapter.provider;

import com.fasterxml.jackson.databind.ObjectMapper; import org.springframework.beans.factory.annotation.Value; import org.springframework.context.annotation.Profile; import org.springframework.stereotype.Component; import org.springframework.web.reactive.function.client.WebClient;
import java.util.*; import java.util.concurrent.CompletableFuture;

@Component @Profile("real-ai")
public class OpenAiCompatibleChatProvider implements ChatProvider {
    private final WebClient client; private final ObjectMapper mapper; private final String model;
    public OpenAiCompatibleChatProvider(WebClient.Builder builder,ObjectMapper mapper,@Value("${app.ai.llm-base-url:https://api.openai.com/v1}") String baseUrl,@Value("${app.ai.llm-api-key:}") String apiKey,@Value("${app.ai.llm-model:gpt-4o-mini}") String model){this.mapper=mapper;this.model=model;this.client=builder.baseUrl(baseUrl).defaultHeader("Authorization","Bearer "+apiKey).build();}
    public CompletableFuture<ProviderResult> structured(String prompt, Map<String,Object> schema) {
        Map<String,Object> request=new LinkedHashMap<>(); request.put("model",model); request.put("temperature",0); request.put("messages",List.of(Map.of("role","system","content","Output JSON only."),Map.of("role","user","content",prompt))); request.put("response_format",Map.of("type","json_object"));
        return client.post().uri("/chat/completions").bodyValue(request).retrieve().bodyToMono(Map.class).map(raw->{try{var choices=(List<Map<String,Object>>)raw.get("choices"); var message=(Map<String,Object>)choices.get(0).get("message"); var content=String.valueOf(message.get("content")); return new ProviderResult(mapper.readValue(content,Map.class),"openai-compatible",model,prompt.length(),content.length());}catch(Exception e){throw new IllegalStateException("invalid structured provider response",e);}}).toFuture();
    }
}
