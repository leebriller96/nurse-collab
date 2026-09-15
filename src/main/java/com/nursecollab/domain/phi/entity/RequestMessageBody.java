package com.nursecollab.domain.phi.entity;

import com.nursecollab.global.error.BusinessException;
import com.nursecollab.global.error.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 요청에 붙는 대화의 내용.
 *
 * "환자분 열이 38.5도라 검사 미뤄주세요" 같은 말이 섞인다. 그래서 원내에 둔다.
 * 업무 쪽 request_message 에는 누가·언제와 {@link #messageRef} 만 있다.
 *
 * <p>요청과 보낸 사람을 외래키가 아니라 id 로만 든다. 둘 다 업무 쪽 DB 에 있다.
 * 보낸 사람 이름은 쓸 때 굳힌다 — 기록에 찍힌 이름은 그때 그 사람이어야 한다.
 */
@Entity
@Table(name = "request_message_body")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RequestMessageBody {

    private static final int MAX_LENGTH = 1000;

    /** 업무 쪽 메시지가 이 값으로 본문을 가리킨다. 원내가 만든다. */
    @Id
    @Column(name = "message_ref", updatable = false)
    private UUID messageRef;

    @Column(name = "order_id", nullable = false, updatable = false)
    private Long orderId;

    @Column(name = "sender_id", nullable = false, updatable = false)
    private Long senderId;

    @Column(name = "sender_name", nullable = false, length = 50, updatable = false)
    private String senderName;

    @Column(nullable = false, length = MAX_LENGTH, updatable = false)
    private String content;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    public static RequestMessageBody write(Long orderId, Long senderId, String senderName, String content) {
        if (content == null || content.isBlank() || content.length() > MAX_LENGTH) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        RequestMessageBody body = new RequestMessageBody();
        body.messageRef = UUID.randomUUID();
        body.orderId = orderId;
        body.senderId = senderId;
        body.senderName = senderName;
        body.content = content.trim();
        body.createdAt = OffsetDateTime.now();
        return body;
    }
}
