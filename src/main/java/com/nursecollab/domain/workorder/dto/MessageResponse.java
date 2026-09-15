package com.nursecollab.domain.workorder.dto;

import com.nursecollab.domain.workorder.entity.RequestMessage;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 대화 한 건의 누가·언제. 내용은 없다.
 *
 * @param messageRef 화면이 이 열쇠로 원내 본문을 받아 붙인다
 */
public record MessageResponse(
        Long id,
        UUID messageRef,
        SenderInfo sender,
        OffsetDateTime createdAt
) {
    public record SenderInfo(Long id, String name, String departmentName) {}

    public static MessageResponse from(RequestMessage message) {
        var sender = message.getSender();
        return new MessageResponse(
                message.getId(),
                message.getMessageRef(),
                new SenderInfo(sender.getId(), sender.getName(), sender.getDepartment().getName()),
                message.getCreatedAt());
    }
}
