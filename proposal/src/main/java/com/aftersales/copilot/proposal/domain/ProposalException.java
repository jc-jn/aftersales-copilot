package com.aftersales.copilot.proposal.domain;

import org.springframework.http.HttpStatus;

public class ProposalException extends RuntimeException {
    private final String code; private final HttpStatus status; private final Object details;
    public ProposalException(String code, String message, HttpStatus status) { this(code, message, status, null); }
    public ProposalException(String code, String message, HttpStatus status, Object details) { super(message); this.code = code; this.status = status; this.details = details; }
    public String code() { return code; } public HttpStatus status() { return status; } public Object details() { return details; }
    public static ProposalException notFound() { return new ProposalException("PROPOSAL_NOT_FOUND", "提案不存在", HttpStatus.NOT_FOUND); }
    public static ProposalException forbidden() { return new ProposalException("PROPOSAL_FORBIDDEN", "无权操作该提案", HttpStatus.FORBIDDEN); }
    public static ProposalException version() { return new ProposalException("PROPOSAL_VERSION_CONFLICT", "提案已被其他请求修改，请刷新后重试", HttpStatus.CONFLICT); }
    public static ProposalException ticketVersion() { return new ProposalException("TICKET_VERSION_CONFLICT", "工单已被其他请求修改，请刷新后重试", HttpStatus.CONFLICT); }
    public static ProposalException invalid(String code, String message) { return new ProposalException(code, message, HttpStatus.CONFLICT); }
}
