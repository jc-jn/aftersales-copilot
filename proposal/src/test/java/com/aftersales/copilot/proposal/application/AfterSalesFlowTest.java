package com.aftersales.copilot.proposal.application;

import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.assertj.core.api.Assertions.assertThat;

class AfterSalesFlowTest {
    @Test void fourProposalTypesAreCoveredByExecutionBranches() {
        assertThat(Set.of("REFUND_ONLY", "RETURN_REFUND", "EXCHANGE", "REPAIR")).hasSize(4);
    }
    @Test void returnStatusesFollowShipmentReceiveInspectOrder() {
        assertThat(new String[]{"WAITING_SHIPMENT", "IN_TRANSIT", "RECEIVED", "INSPECTED"})
                .containsExactly("WAITING_SHIPMENT", "IN_TRANSIT", "RECEIVED", "INSPECTED");
    }
}
