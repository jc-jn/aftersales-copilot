package com.aftersales.copilot.ticket.api;
import jakarta.validation.constraints.*;
public final class TicketRequests { private TicketRequests(){}
 public record Create(@NotNull Long orderItemId,@NotBlank @Size(max=24) String requestedType,@NotBlank @Size(max=160) String title,@NotBlank String description,String clientRequestId){}
 public record Message(@NotBlank String content,@NotBlank String visibility,String messageType,@NotNull Integer version){}
 public record Command(@NotNull Integer version){}
 public record Transfer(@NotNull Long agentId,@NotNull Integer version,@Size(max=255) String reason){}
}
