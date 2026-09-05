package com.nursecollab.domain.transfer.dto;

import com.nursecollab.domain.staff.entity.Staff;
import com.nursecollab.domain.transfer.entity.TransferRequest;
import com.nursecollab.domain.transfer.entity.TransferStatus;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 상태 전이 결과.
 *
 * availableTransitions 를 함께 내려주는 이유: 프론트가 전이 규칙을 중복 구현하지 않게 하기 위해서다.
 * 화면은 이 배열만 보고 버튼을 그린다.
 */
public record TransitionResponse(
        Long id,
        TransferStatus status,
        OffsetDateTime scheduledAt,
        List<TransferStatus> availableTransitions,
        Long version
) {
    public static TransitionResponse of(TransferRequest request, Staff actor) {
        return new TransitionResponse(
                request.getId(),
                request.getStatus(),
                request.getScheduledAt(),
                List.copyOf(request.availableTransitions(actor)),
                request.getVersion());
    }
}
