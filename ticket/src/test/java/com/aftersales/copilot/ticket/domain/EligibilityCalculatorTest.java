package com.aftersales.copilot.ticket.domain;

import org.junit.jupiter.api.Test;
import java.time.*;
import static org.assertj.core.api.Assertions.assertThat;

class EligibilityCalculatorTest {
    @Test void deadlineIsInclusive() {
        var anchor = Instant.parse("2026-09-15T06:30:00Z").atZone(ZoneOffset.UTC).toLocalDateTime();
        var result = EligibilityCalculator.calculate(anchor, anchor.plusDays(7), 7, true, 1000, 0, "REFUND_ONLY");
        assertThat(result.eligible()).isTrue();
    }
    @Test void refundedAmountReducesRemainingAmount() {
        var now = LocalDateTime.of(2026, 9, 1, 0, 0);
        var result = EligibilityCalculator.calculate(now, now, 7, true, 1000, 350, "REFUND_ONLY");
        assertThat(result.remainingRefundableCent()).isEqualTo(650);
    }
    @Test void expiredWindowIsRejected() {
        var anchor = LocalDateTime.of(2026, 9, 1, 0, 0);
        var result = EligibilityCalculator.calculate(anchor, anchor.plusDays(8), 7, true, 1000, 0, "EXCHANGE");
        assertThat(result.eligible()).isFalse();
        assertThat(result.reason()).isEqualTo("EXCHANGE_WINDOW_EXPIRED");
    }
}
