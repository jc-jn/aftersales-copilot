package com.aftersales.copilot.aiadapter.application;

import com.aftersales.copilot.aiadapter.provider.ChatProvider;
import com.aftersales.copilot.common.ai.AiTaskScheduler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate; import org.springframework.stereotype.Service; import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime; import java.time.ZoneOffset; import java.util.*;

@Service
public class AiTaskApplicationService implements AiTaskScheduler {
    private final JdbcTemplate jdbc; private final ObjectMapper json; private final ChatProvider provider;
    public AiTaskApplicationService(JdbcTemplate jdbc,ObjectMapper json,ChatProvider provider){this.jdbc=jdbc;this.json=json;this.provider=provider;}
    @Override @Transactional
    public void scheduleTicketAnalysis(long ticketId,int ticketVersion,long customerId){
        long taskId = Math.abs(UUID.randomUUID().getMostSignificantBits()); String dedup="TICKET_ANALYSIS:"+ticketId+":"+ticketVersion; LocalDateTime now=LocalDateTime.now(ZoneOffset.UTC);
        String snapshot="{\"ticketId\":"+ticketId+",\"ticketVersion\":"+ticketVersion+",\"customerId\":"+customerId+"}";
        try { jdbc.update("INSERT INTO ai_task(id,biz_type,biz_id,dedup_key,status,attempt_count,max_attempts,request_snapshot,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?,?,?)",taskId,"TICKET_ANALYSIS",ticketId,dedup,"PENDING",0,3,snapshot,now,now); }
        catch(Exception e){ return; }
        Map<String,Object> envelope=new LinkedHashMap<>(); envelope.put("eventId",UUID.randomUUID().toString()); envelope.put("eventType","ticket.ai.analyze.requested.v1"); envelope.put("occurredAt",now.toString()); envelope.put("traceId",UUID.randomUUID().toString()); envelope.put("producer","aftersales-server"); envelope.put("schemaVersion",1); envelope.put("data",Map.of("taskId",taskId,"ticketId",ticketId,"ticketVersion",ticketVersion,"customerId",customerId));
        try { jdbc.update("INSERT INTO outbox_event(event_id,aggregate_type,aggregate_id,event_type,routing_key,payload,status,retry_count,created_at,updated_at) VALUES(?,?,?,?,?,?,?,0,?,?)",envelope.get("eventId"),"AI_TASK",taskId,"ticket.ai.analyze.requested.v1","ticket.ai.analyze.requested.v1",json.writeValueAsString(envelope),"NEW",now,now); } catch(Exception e){throw new IllegalStateException(e);}
    }
    @Transactional public Map<String,Object> callback(Map<String,Object> payload) throws Exception {
        long taskId=((Number)payload.get("taskId")).longValue(); String status=String.valueOf(payload.getOrDefault("status","FAILED")); LocalDateTime now=LocalDateTime.now(ZoneOffset.UTC);
        if("SUCCEEDED".equals(status)){
            Map<String,Object> result=(Map<String,Object>)payload.getOrDefault("result",Map.of()); long ticketId=((Number)payload.get("ticketId")).longValue(); int version=((Number)payload.getOrDefault("ticketVersion",0)).intValue();
            jdbc.update("INSERT IGNORE INTO ai_analysis(id,ticket_id,task_id,ticket_version,intent,confidence,priority_suggestion,sentiment,extracted_json,missing_fields_json,reply_suggestion,proposal_suggestion_json,needs_human,risk_flags_json,citations_json,provider,model,prompt_version,input_tokens,output_tokens,estimated_cost_micros,latency_ms,stale,created_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",Math.abs(UUID.randomUUID().getMostSignificantBits()),ticketId,taskId,version,String.valueOf(result.getOrDefault("intent","OTHER")),Double.parseDouble(String.valueOf(result.getOrDefault("confidence",0))),String.valueOf(result.getOrDefault("prioritySuggestion","MEDIUM")),String.valueOf(result.getOrDefault("sentiment","NEUTRAL")),json.writeValueAsString(result.get("extracted")),json.writeValueAsString(result.get("missingFields")),result.get("replySuggestion"),json.writeValueAsString(result.get("proposalSuggestion")),Boolean.TRUE.equals(result.get("needsHuman")),json.writeValueAsString(result.get("riskFlags")),json.writeValueAsString(result.get("citations")),"fake","fake-v1","ticket-analysis-v1",0,0,0,0,false,now);
            jdbc.update("UPDATE ai_task SET status='SUCCEEDED',finished_at=?,updated_at=? WHERE id=? AND status<>'SUCCEEDED'",now,now,taskId);
        } else jdbc.update("UPDATE ai_task SET status='FAILED',error_code=?,error_message=?,finished_at=?,updated_at=? WHERE id=? AND status<>'SUCCEEDED'",payload.getOrDefault("errorCode","AI_FAILED"),payload.getOrDefault("errorMessage","analysis failed"),now,now,taskId);
        return Map.of("accepted",true);
    }
}
