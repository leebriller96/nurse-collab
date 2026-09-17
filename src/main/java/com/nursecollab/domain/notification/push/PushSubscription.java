package com.nursecollab.domain.notification.push;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/** 폰 알림을 받을 기기 하나. endpoint 가 기기를 가리킨다. */
@Entity
@Table(name = "push_subscription")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PushSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "staff_id", nullable = false)
    private Long staffId;

    @Column(nullable = false, unique = true)
    private String endpoint;

    @Column(nullable = false, length = 200)
    private String p256dh;

    @Column(nullable = false, length = 50)
    private String auth;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    public static PushSubscription of(Long staffId, String endpoint, String p256dh, String auth) {
        PushSubscription s = new PushSubscription();
        s.staffId = staffId;
        s.endpoint = endpoint;
        s.p256dh = p256dh;
        s.auth = auth;
        s.createdAt = OffsetDateTime.now();
        return s;
    }

    /**
     * 같은 기기로 다른 사람이 로그인해 등록했다. 병동 폰은 돌려 쓴다.
     * 앞사람 것으로 남겨 두면 다음 사람이 앞사람 환자 알림을 받는다.
     * 브라우저가 키를 새로 만들었을 수 있어 키도 함께 바꾼다.
     */
    public void handOver(Long staffId, String p256dh, String auth) {
        this.staffId = staffId;
        this.p256dh = p256dh;
        this.auth = auth;
    }
}
