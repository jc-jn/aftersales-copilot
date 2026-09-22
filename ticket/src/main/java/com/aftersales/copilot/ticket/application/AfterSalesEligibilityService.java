package com.aftersales.copilot.ticket.application;

import com.aftersales.copilot.auth.domain.AuthenticatedUser;
import com.aftersales.copilot.auth.domain.UserRole;
import com.aftersales.copilot.ticket.domain.TicketException;
import com.aftersales.copilot.ticket.infrastructure.mapper.TicketMapper;
import org.springframework.stereotype.Service;

import java.time.*;
import java.util.*;

@Service
public class AfterSalesEligibilityService {
    private final TicketMapper mapper;
    public AfterSalesEligibilityService(TicketMapper mapper) { this.mapper = mapper; }

    public EligibilityResult calculate(AuthenticatedUser user, long itemId) {
        var item = mapper.findEligibilityItem(itemId, user.id()).orElseThrow(() ->
                new TicketException("ORDER_NOT_OWNED", "订单项不属于当前用户或不存在", org.springframework.http.HttpStatus.FORBIDDEN));
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        var rule = mapper.findRule(item.category(), item.productId(), item.skuId(), now).orElse(null);
        if (rule == null) return new EligibilityResult(Long.toUnsignedString(itemId), List.of());
        return new EligibilityResult(Long.toUnsignedString(itemId), List.of(
                result("REFUND_ONLY", item, rule.refundOnlyDays(), now, item.orderStatus().equals("DELIVERED")),
                result("RETURN_REFUND", item, rule.returnRefundDays(), now, item.orderStatus().equals("DELIVERED")),
                result("EXCHANGE", item, rule.exchangeDays(), now, item.orderStatus().equals("DELIVERED")),
                result("REPAIR", item, rule.repairDays(), now, item.warrantyExpireAt() != null && item.warrantyExpireAt().isAfter(now))));
    }

    private Option result(String type, TicketMapper.EligibilityItem item, int days, LocalDateTime now, boolean baseEligible) {
        LocalDateTime anchor = item.deliveredAt() == null ? item.createdAt() : item.deliveredAt();
        var result = com.aftersales.copilot.ticket.domain.EligibilityCalculator.calculate(anchor, now, days, baseEligible && !item.orderStatus().equals("CANCELLED"), item.paidAmountCent(), item.refundedAmountCent(), type);
        return new Option(type, result.eligible(), result.deadline(), result.eligible() ? result.remainingRefundableCent() : 0L, item.orderStatus().equals("CANCELLED") ? "ORDER_CANCELLED" : result.reason());
    }

    public record EligibilityResult(String orderItemId, List<Option> options) {}
    public record Option(String type, boolean eligible, LocalDateTime deadline, long remainingRefundableCent, String reason) {}
}
