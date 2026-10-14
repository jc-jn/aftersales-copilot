package com.aftersales.copilot.ticket.application;

import com.aftersales.copilot.auth.domain.AuthenticatedUser;
import com.aftersales.copilot.auth.domain.UserRole;
import com.aftersales.copilot.auth.infrastructure.SnowflakeIdGenerator;
import com.aftersales.copilot.common.ai.AiTaskScheduler;
import com.aftersales.copilot.ticket.api.TicketRequests;
import com.aftersales.copilot.ticket.domain.TicketException;
import com.aftersales.copilot.ticket.domain.TicketStateMachine;
import com.aftersales.copilot.ticket.infrastructure.mapper.TicketMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TicketCreationTest {
    private static final long ID = 368224378833338368L;
    private final TicketMapper mapper = mock(TicketMapper.class);
    private final SnowflakeIdGenerator ids = mock(SnowflakeIdGenerator.class);
    private final AiTaskScheduler ai = mock(AiTaskScheduler.class);
    private final TicketApplicationService service = new TicketApplicationService(mapper, ids, new TicketStateMachine(), ai);
    private final AuthenticatedUser customer = new AuthenticatedUser(1001L, "customer01", null, "Customer", UserRole.CUSTOMER);
    private final TicketRequests.Create request = new TicketRequests.Create(3101L, "REPAIR", "Repair", "Charging issue", "request-1");

    private void prepareCreate() {
        when(ids.nextId()).thenReturn(ID);
        when(mapper.findByClientRequest(1001L, "request-1")).thenReturn(Optional.empty());
        when(mapper.findOrderItemForCustomer(3101L, 1001L)).thenReturn(Optional.of(new TicketMapper.OrderContext(3101L, 3001L, 1001L, "DELIVERED", "Phone")));
    }

    @Test void guardConflictMustNotReturnNewUnqueuedTicketAsIdempotentReplay() {
        prepareCreate();
        doThrow(new DuplicateKeyException("active guard")).when(mapper).insertGuard(eq(3101L), eq(ID), any());
        assertThatThrownBy(() -> service.create(customer, request)).isInstanceOfSatisfying(TicketException.class,
                e -> assertThat(e.code()).isEqualTo("ACTIVE_TICKET_EXISTS"));
        verify(mapper, times(1)).findByClientRequest(1001L, "request-1");
        verify(mapper, never()).findView(anyLong());
        verifyNoInteractions(ai);
    }

    @Test void unsuccessfulQueueTransitionCannotReportSuccessOrScheduleAnalysis() {
        prepareCreate();
        when(mapper.transition(eq(ID), eq("PENDING_ASSIGNMENT"), isNull(), eq(0), any())).thenReturn(0);
        assertThatThrownBy(() -> service.create(customer, request)).isInstanceOfSatisfying(TicketException.class,
                e -> assertThat(e.code()).isEqualTo("TICKET_VERSION_CONFLICT"));
        verifyNoInteractions(ai);
    }

    @Test void ticketIdsRemainExactStringsInListAndUnwrappedDetail() throws Exception {
        var view = new TicketMapper.TicketView(ID, "AS-test", 1001L, 3001L, 3101L, "REPAIR", null,
                "PENDING_ASSIGNMENT", "MEDIUM", "Repair", "Charging issue", null, null, null, null, null, null, 1, null, null);
        var json = new ObjectMapper();
        var list = json.valueToTree(List.of(view));
        var detail = json.valueToTree(new TicketApplicationService.TicketDetail(view, List.of(), List.of()));
        for (var ticket : List.of(list.get(0), detail)) {
            assertThat(ticket.path("id").isTextual()).isTrue();
            assertThat(ticket.path("id").asText()).isEqualTo(Long.toString(ID));
            assertThat(ticket.path("customerId").asText()).isEqualTo("1001");
            assertThat(ticket.path("assignedAgentId").isNull()).isTrue();
            assertThat(ticket.path("version").isInt()).isTrue();
        }
    }
}
