package com.aftersales.copilot.proposal.api;

import jakarta.validation.constraints.*;

public final class ProposalRequests {
    private ProposalRequests() {}
    public record Create(@NotBlank String type, @NotNull @PositiveOrZero Long refundAmountCent,
                         @Size(max = 40) String reasonCode, @NotBlank String description,
                         String conditions, @Min(1) @Max(168) Integer expiresInHours,
                         @NotNull Integer version) {}
    public record Version(@NotNull Integer version) {}
    public record Confirm(@NotNull Integer ticketVersion, @NotNull Integer proposalVersion) {}
    public record Shipment(@NotBlank String carrierCode, @NotBlank String trackingNo, @NotNull Integer version) {}
    public record Receive(@NotNull Integer version) {}
    public record Inspect(@NotBlank String result, @Size(max=1000) String note, @NotNull Integer version) {}
}
