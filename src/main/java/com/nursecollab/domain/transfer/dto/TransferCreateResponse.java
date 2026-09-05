package com.nursecollab.domain.transfer.dto;

import com.nursecollab.domain.transfer.entity.TransferPriority;
import com.nursecollab.domain.transfer.entity.TransferRequest;
import com.nursecollab.domain.transfer.entity.TransferStatus;

import java.time.OffsetDateTime;

public record TransferCreateResponse(
        Long id,
        String requestNo,
        TransferStatus status,
        TransferPriority priority,
        OffsetDateTime requestedAt,
        Long version
) {
    public static TransferCreateResponse from(TransferRequest request) {
        return new TransferCreateResponse(
                request.getId(),
                request.getRequestNo(),
                request.getStatus(),
                request.getPriority(),
                request.getRequestedAt(),
                request.getVersion());
    }
}
