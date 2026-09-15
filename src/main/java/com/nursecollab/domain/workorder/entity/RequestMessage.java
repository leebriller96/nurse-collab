package com.nursecollab.domain.workorder.entity;

import com.nursecollab.domain.staff.entity.Staff;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 요청에 붙는 대화의 <b>누가·언제</b>.
 * 자유 채팅이 아니라 요청 단위 스레드다. 맥락에서 떨어진 대화는 기록으로서 가치가 없다.
 *
 * <p>내용이 여기 없다. "환자분 열이 38.5도라 미뤄주세요" 같은 말이 섞여서 원내에 둔다
 * (docs/06 9장 1). 남는 것은 원내 본문을 가리키는 {@link #messageRef} 다.
 * 그래야 원내망 밖에서도 "대화가 3건 있는데 여기서는 못 읽는다" 를 말할 수 있다.
 */
@Entity
@Table(name = "request_message")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RequestMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id")
    private WorkOrder request;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sender_id")
    private Staff sender;

    /** 원내 request_message_body 를 가리킨다. 원내가 만들어 알려 온다. */
    @Column(name = "message_ref", nullable = false, unique = true, updatable = false)
    private UUID messageRef;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    public static RequestMessage of(WorkOrder request, Staff sender, UUID messageRef) {
        RequestMessage message = new RequestMessage();
        message.request = request;
        message.sender = sender;
        message.messageRef = messageRef;
        message.createdAt = OffsetDateTime.now();
        return message;
    }
}
