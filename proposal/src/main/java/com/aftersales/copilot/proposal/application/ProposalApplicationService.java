package com.aftersales.copilot.proposal.application;

import com.aftersales.copilot.auth.domain.*;
import com.aftersales.copilot.auth.infrastructure.SnowflakeIdGenerator;
import com.aftersales.copilot.proposal.api.ProposalRequests;
import com.aftersales.copilot.proposal.domain.ProposalException;
import com.aftersales.copilot.proposal.infrastructure.ProposalMapper;
import com.aftersales.copilot.ticket.infrastructure.mapper.TicketMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;

@Service
public class ProposalApplicationService {
    private final ProposalMapper mapper; private final SnowflakeIdGenerator ids;
    public ProposalApplicationService(ProposalMapper mapper, SnowflakeIdGenerator ids) { this.mapper = mapper; this.ids = ids; }

    @Transactional
    public Object create(AuthenticatedUser user, long ticketId, ProposalRequests.Create request) {
        requireAgent(user); var ticket = mapper.lockTicket(ticketId).orElseThrow(ProposalException::notFound);
        checkAgentAccess(user, ticket); if (!ticket.status().equals("PENDING_AGENT")) throw ProposalException.invalid("PROPOSAL_TICKET_STATE", "当前工单不允许创建提案"); if (ticket.version() != request.version()) throw ProposalException.ticketVersion();
        String type = request.type().toUpperCase(Locale.ROOT); if (!Set.of("REFUND_ONLY", "RETURN_REFUND", "EXCHANGE", "REPAIR").contains(type)) throw ProposalException.invalid("PROPOSAL_TYPE_INVALID", "不支持的提案类型");
        long remaining = Math.max(0, ticket.paidAmountCent() - ticket.refundedAmountCent()); long amount = request.refundAmountCent();
        if (type.equals("REFUND_ONLY") && amount > remaining) throw new ProposalException("PROPOSAL_AMOUNT_INVALID", "提案金额超过订单项可退金额", org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY);
        if (!type.equals("REFUND_ONLY")) amount = 0;
        if (mapper.findPending(ticketId).isPresent()) throw ProposalException.invalid("PROPOSAL_PENDING_EXISTS", "该工单已有待确认提案");
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC); LocalDateTime expires = now.plusHours(request.expiresInHours() == null ? 24 : request.expiresInHours());
        long id = ids.nextId(); String no = "SP" + now.toLocalDate().toString().replace("-", "") + String.format("%06d", Math.floorMod(id, 1_000_000));
        mapper.insertProposal(id, no, ticketId, type, "AGENT", request.refundAmountCent(), amount, request.reasonCode(), request.description(), request.conditions(), user.id(), expires, now);
        audit(user, "PROPOSAL_CREATE", id, "SUCCESS", null, "{\"status\":\"DRAFT\"}"); return mapper.findProposal(id).orElseThrow(ProposalException::notFound);
    }

    public List<ProposalMapper.ProposalView> list(AuthenticatedUser user, long ticketId) {
        var ticket = mapper.lockTicket(ticketId).orElseThrow(ProposalException::notFound); checkTicketAccess(user, ticket); return mapper.listByTicket(ticketId);
    }

    @Transactional public Object publish(AuthenticatedUser user, long proposalId, ProposalRequests.Version request) {
        requireAgent(user); var proposal = mapper.lockProposal(proposalId).orElseThrow(ProposalException::notFound); var ticket = mapper.lockTicket(proposal.ticketId()).orElseThrow(ProposalException::notFound); checkAgentAccess(user, ticket);
        if (!proposal.status().equals("DRAFT")) throw ProposalException.invalid("PROPOSAL_INVALID_TRANSITION", "只有草稿提案可以发布"); LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        if (mapper.publish(proposalId, user.id(), request.version(), now) != 1) throw ProposalException.version(); audit(user, "PROPOSAL_PUBLISH", proposalId, "SUCCESS", proposal.status(), "{\"status\":\"PENDING_CONFIRMATION\"}"); return mapper.findProposal(proposalId).orElseThrow(ProposalException::notFound);
    }

    @Transactional public Object reject(AuthenticatedUser user, long proposalId, ProposalRequests.Version request) {
        var proposal = mapper.lockProposal(proposalId).orElseThrow(ProposalException::notFound); var ticket = mapper.lockTicket(proposal.ticketId()).orElseThrow(ProposalException::notFound); if (user.role() != UserRole.CUSTOMER || ticket.customerId() != user.id()) throw ProposalException.forbidden();
        if (!proposal.status().equals("PENDING_CONFIRMATION")) throw ProposalException.invalid("PROPOSAL_INVALID_TRANSITION", "只有待确认提案可以拒绝"); LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC); if (mapper.reject(proposalId, request.version(), now) != 1) throw ProposalException.version();
        mapper.insertTimeline(ids.nextId(), ticket.id(), "PROPOSAL_REJECTED", "CUSTOMER", user.id(), "用户拒绝提案", null, now); audit(user, "PROPOSAL_REJECT", proposalId, "SUCCESS", proposal.status(), "{\"status\":\"REJECTED\"}"); return mapper.findProposal(proposalId).orElseThrow(ProposalException::notFound);
    }

    @Transactional public Object confirm(AuthenticatedUser user, long proposalId, ProposalRequests.Confirm request, String idempotencyKey) {
        if (user.role() != UserRole.CUSTOMER) throw ProposalException.forbidden(); String key = normalizeKey(idempotencyKey); String hash = hash(proposalId + ":" + request.ticketVersion() + ":" + request.proposalVersion());
        var proposal = mapper.lockProposal(proposalId).orElseThrow(ProposalException::notFound); var ticket = mapper.lockTicket(proposal.ticketId()).orElseThrow(ProposalException::notFound); if (ticket.customerId() != user.id()) throw ProposalException.forbidden();
        var existing = mapper.findExecutionByKey(key); if (existing.isPresent()) { if (!existing.get().requestHash().equals(hash)) throw new ProposalException("IDEMPOTENCY_KEY_REUSED", "相同幂等键不能用于不同请求", org.springframework.http.HttpStatus.CONFLICT); return existing.get(); }
        var proposalExecution = mapper.findExecutionByProposal(proposalId); if (proposalExecution.isPresent()) throw ProposalException.invalid("PROPOSAL_ALREADY_EXECUTED", "该提案已经存在执行记录");
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC); if (now.isAfter(proposal.expiresAt())) { mapper.updateStatus(proposal.id(), "EXPIRED", proposal.version(), now); throw new ProposalException("PROPOSAL_EXPIRED", "提案已过期", org.springframework.http.HttpStatus.CONFLICT); }
        if (!proposal.status().equals("PENDING_CONFIRMATION")) throw ProposalException.invalid("PROPOSAL_INVALID_TRANSITION", "只有待确认提案可以确认"); if (ticket.version() != request.ticketVersion()) throw ProposalException.ticketVersion(); if (proposal.version() != request.proposalVersion()) throw ProposalException.version();
        long executionId = ids.nextId(); try { mapper.insertExecution(executionId, proposalId, key, user.id(), hash, now); } catch (DuplicateKeyException e) { var retry = mapper.findExecutionByKey(key).orElseThrow(() -> ProposalException.invalid("PROPOSAL_ALREADY_EXECUTED", "该提案已经存在执行记录")); if (!retry.requestHash().equals(hash)) throw new ProposalException("IDEMPOTENCY_KEY_REUSED", "相同幂等键不能用于不同请求", org.springframework.http.HttpStatus.CONFLICT); return retry; }
        if (mapper.updateStatus(proposalId, "CONFIRMED", proposal.version(), now) != 1) throw ProposalException.version();
        String result; if (proposal.type().equals("REFUND_ONLY")) result = executeRefund(user, proposal, ticket, request.ticketVersion(), now); else { if (mapper.transitionTicket(ticket.id(), ticket.status(), "WAITING_RETURN", request.ticketVersion(), now) != 1) throw ProposalException.ticketVersion(); result = "{\"status\":\"WAITING_RETURN\"}"; }
        mapper.updateStatus(proposalId, proposal.type().equals("REFUND_ONLY") ? "EXECUTED" : "CONFIRMED", proposal.version() + 1, now); mapper.finishExecution(executionId, "SUCCEEDED", result, null, null, now); mapper.insertTimeline(ids.nextId(), ticket.id(), "PROPOSAL_CONFIRMED", "CUSTOMER", user.id(), "用户确认售后提案", result, now); audit(user, "PROPOSAL_CONFIRM", proposalId, "SUCCESS", proposal.status(), result); return mapper.findProposal(proposalId).orElseThrow(ProposalException::notFound);
    }

    private String executeRefund(AuthenticatedUser user, ProposalMapper.ProposalView proposal, ProposalMapper.TicketContext ticket, int ticketVersion, LocalDateTime now) {
        if (mapper.refundItem(ticket.orderItemId(), proposal.refundAmountCent(), now) != 1) throw new ProposalException("PROPOSAL_AMOUNT_INVALID", "订单项剩余可退款金额不足", org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY);
        if (mapper.transitionTicket(ticket.id(), ticket.status(), "PROCESSING", ticketVersion, now) != 1) throw ProposalException.ticketVersion();
        if (mapper.transitionTicket(ticket.id(), "PROCESSING", "RESOLVED", ticketVersion + 1, now) != 1) throw ProposalException.ticketVersion(); mapper.deleteActiveGuard(ticket.id());
        String refundNo = "RF" + now.toLocalDate().toString().replace("-", "") + String.format("%06d", Math.floorMod(ids.nextId(), 1_000_000)); mapper.insertRefund(ids.nextId(), refundNo, ticket.id(), proposal.id(), ticket.orderItemId(), proposal.refundAmountCent(), now); return "{\"status\":\"SUCCEEDED\",\"refundNo\":\"" + refundNo + "\"}";
    }

    private void checkTicketAccess(AuthenticatedUser user, ProposalMapper.TicketContext ticket) { if (user.role() == UserRole.CUSTOMER && ticket.customerId() != user.id()) throw ProposalException.forbidden(); if (user.role() == UserRole.AGENT && ticket.assignedAgentId() != null && ticket.assignedAgentId() != user.id()) throw ProposalException.forbidden(); }
    private void checkAgentAccess(AuthenticatedUser user, ProposalMapper.TicketContext ticket) { if (user.role() == UserRole.AGENT && ticket.assignedAgentId() != user.id()) throw ProposalException.forbidden(); }
    private void requireAgent(AuthenticatedUser user) { if (user.role() != UserRole.AGENT && user.role() != UserRole.ADMIN) throw ProposalException.forbidden(); }
    private String normalizeKey(String key) { if (key == null || key.isBlank() || key.length() > 64) throw new ProposalException("IDEMPOTENCY_KEY_REQUIRED", "必须提供有效的 Idempotency-Key", org.springframework.http.HttpStatus.BAD_REQUEST); return key.trim(); }
    private String hash(String value) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); } catch (Exception e) { throw new IllegalStateException(e); } }
    private void audit(AuthenticatedUser user, String action, long resourceId, String result, String before, String after) { mapper.insertAudit(ids.nextId(), null, user.role().name(), user.id(), action, "PROPOSAL", resourceId, result, before, after, LocalDateTime.now(ZoneOffset.UTC)); }
}
