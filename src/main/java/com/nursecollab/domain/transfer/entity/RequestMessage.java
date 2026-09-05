package com.nursecollab.domain.transfer.entity;

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

/**
 * 요청에 붙는 대화.
 * 자유 채팅이 아니라 요청 단위 스레드다. 맥락에서 떨어진 대화는 기록으로서 가치가 없다.
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
    @JoinColumn(name = "request_id")
    private TransferRequest request;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sender_id")
    private Staff sender;

    @Column(nullable = false, length = 1000)
    private String content;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    public static RequestMessage of(TransferRequest request, Staff sender, String content) {
        RequestMessage message = new RequestMessage();
        message.request = request;
        message.sender = sender;
        message.content = content;
        message.createdAt = OffsetDateTime.now();
        return message;
    }
}
