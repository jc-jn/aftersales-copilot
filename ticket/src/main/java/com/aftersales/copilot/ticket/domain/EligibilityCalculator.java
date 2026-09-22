package com.aftersales.copilot.ticket.domain;

import java.time.LocalDateTime;

public final class EligibilityCalculator {
    private EligibilityCalculator() {}
    public static Result calculate(LocalDateTime anchor, LocalDateTime now, int windowDays,
                                   boolean baseEligible, long paidCent, long refundedCent, String type) {
        LocalDateTime deadline = anchor == null ? null : anchor.plusDays(windowDays);
        boolean eligible = baseEligible && deadline != null && !now.isAfter(deadline) && paidCent > refundedCent;
        String reason = eligible ? null : (deadline != null && now.isAfter(deadline) ? type + "_WINDOW_EXPIRED" : "NOT_ELIGIBLE");
        return new Result(eligible, deadline, Math.max(0, paidCent - refundedCent), reason);
    }
    public record Result(boolean eligible, LocalDateTime deadline, long remainingRefundableCent, String reason) {}
}
