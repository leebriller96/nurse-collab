package com.nursecollab.domain.notification.push;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 구독 주소 허용 판정. 여기가 뚫리면 로그인한 누구든 서버에게 내부망 주소를 두드리게 할 수 있다. */
class PushEndpointsTest {

    private static final List<String> PRODUCTION = List.of(
            "fcm.googleapis.com", "updates.push.services.mozilla.com", "web.push.apple.com",
            "*.push.apple.com", "*.notify.windows.com");

    @Test
    void 알려진_푸시_서비스는_연다() {
        assertThat(PushEndpoints.allowed("https://fcm.googleapis.com/fcm/send/abc", PRODUCTION)).isTrue();
        assertThat(PushEndpoints.allowed("https://web.push.apple.com/QGuQyavXutnMH", PRODUCTION)).isTrue();
        assertThat(PushEndpoints.allowed("https://wns2-par02p.notify.windows.com/w/?token=x", PRODUCTION)).isTrue();
    }

    @Test
    void 이름이_비슷한_다른_호스트는_막는다() {
        // 뒤에 붙이거나 앞에 붙여 허용 목록을 흉내 낸다
        assertThat(PushEndpoints.allowed("https://fcm.googleapis.com.evil.example/x", PRODUCTION)).isFalse();
        assertThat(PushEndpoints.allowed("https://evilfcm.googleapis.com/x", PRODUCTION)).isFalse();
        assertThat(PushEndpoints.allowed("https://notpush.apple.com/x", PRODUCTION)).isFalse();
        // 사용자 정보 칸으로 호스트를 속이는 모양
        assertThat(PushEndpoints.allowed("https://fcm.googleapis.com@10.0.0.5/x", PRODUCTION)).isFalse();
    }

    @Test
    void 평문과_내부_주소는_막는다() {
        assertThat(PushEndpoints.allowed("http://fcm.googleapis.com/x", PRODUCTION)).isFalse();
        assertThat(PushEndpoints.allowed("https://10.0.0.5/x", PRODUCTION)).isFalse();
        assertThat(PushEndpoints.allowed("https://phi-postgres:5432/", PRODUCTION)).isFalse();
        // 운영 목록에는 localhost 가 없다. 테스트 설정만 연다.
        assertThat(PushEndpoints.allowed("http://localhost:8080/api/v1/staff", PRODUCTION)).isFalse();
        assertThat(PushEndpoints.allowed("file:///etc/passwd", PRODUCTION)).isFalse();
        assertThat(PushEndpoints.allowed("not a url", PRODUCTION)).isFalse();
    }
}
