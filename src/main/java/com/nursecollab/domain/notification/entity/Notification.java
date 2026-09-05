package com.nursecollab.domain.notification.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * 개인 알림함.
 *
 * 화면을 안 보고 있는 사이에 벌어진 일을 나중에 확인하기 위한 것이다.
 * 실시간 알림은 그 순간 화면을 보고 있는 사람에게만 닿는다.
 */
@Entity
@Table(name = "notification")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "recipient_id", nullable = false)
    private Long recipientId;

    @Enumerated(EnumType.STRING)
    @Column(name = "noti_type", nullable = false, length = 30)
    private NotiType notiType;

    @Column(name = "ref_type", nullable = false, length = 30)
    private String refType;

    @Column(name = "ref_id", nullable = false)
    private Long refId;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(length = 300)
    private String body;

    /** null 이면 아직 안 읽은 것이다 */
    @Column(name = "read_at")
    private OffsetDateTime readAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    public static Notification of(Long recipientId, NotiType notiType, String refType,
                                  Long refId, String title, String body) {
        Notification noti = new Notification();
        noti.recipientId = recipientId;
        noti.notiType = notiType;
        noti.refType = refType;
        noti.refId = refId;
        noti.title = title;
        noti.body = body;
        noti.createdAt = OffsetDateTime.now();
        return noti;
    }

    /** 이미 읽은 것을 다시 읽음 처리해도 최초 시각을 덮어쓰지 않는다. */
    public void markRead() {
        if (readAt == null) {
            this.readAt = OffsetDateTime.now();
        }
    }
}
