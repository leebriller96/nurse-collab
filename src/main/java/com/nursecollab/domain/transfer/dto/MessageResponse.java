package com.nursecollab.domain.transfer.dto;

import com.nursecollab.domain.transfer.entity.RequestMessage;

import java.time.OffsetDateTime;

public record MessageResponse(
        Long id,
        SenderInfo sender,
        String content,
        OffsetDateTime createdAt
) {
    public record SenderInfo(Long id, String name, String departmentName) {}

    public static MessageResponse from(RequestMessage message) {
        var sender = message.getSender();
        return new MessageResponse(
                message.getId(),
                new SenderInfo(sender.getId(), sender.getName(), sender.getDepartment().getName()),
                message.getContent(),
                message.getCreatedAt());
    }
}
