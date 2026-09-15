package com.nursecollab.domain.phi.dto;

import com.nursecollab.domain.phi.entity.RequestMessageBody;

import java.util.UUID;

/**
 * 메시지 내용 한 건. 누가·언제는 업무 쪽 목록에 있어서 여기 다시 싣지 않는다.
 * 화면이 {@code messageRef} 로 두 목록을 잇는다.
 */
public record MessageBodyResponse(UUID messageRef, String content) {

    public static MessageBodyResponse from(RequestMessageBody body) {
        return new MessageBodyResponse(body.getMessageRef(), body.getContent());
    }
}
