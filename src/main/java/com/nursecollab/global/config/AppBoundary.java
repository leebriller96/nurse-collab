package com.nursecollab.global.config;

import java.util.List;

/**
 * 무엇이 원내 것이고 무엇이 업무 쪽 것인가. <b>이 표 하나만 있다.</b>
 *
 * 역할별로 띄울 때 컴포넌트 스캔, 저장소 스캔, 엔티티 목록이 모두 이 표를 본다.
 * 경계 테스트(PhiBoundaryTest)도 이 표를 읽는다. 표가 둘이면 테스트는 초록인데
 * 실제로는 원내에 업무 쪽 빈이 올라가는 날이 온다 — 한 번 그랬다. 업무 쪽 목록에
 * {@code domain.audit} 이 빠져 있어서 업무 쪽 감사 조회가 환자 저장소를 읽는 동안 초록이었다.
 *
 * <p>패키지로만 판단한다. 어노테이션을 붙이는 방식은 빠뜨린 클래스가 조용히 양쪽에 올라간다.
 * 패키지는 파일을 만드는 순간 정해진다.
 *
 * <p>둘 다 아닌 것({@code global.common}, {@code global.security}, {@code global.error},
 * {@code global.config})은 양쪽이 함께 쓴다.
 */
public final class AppBoundary {

    private AppBoundary() {}

    /** 원내에만 올라가는 코드. 진료정보를 읽고 쓴다. */
    public static final List<String> PHI_PACKAGES = List.of(
            "com.nursecollab.domain.phi",
            "com.nursecollab.domain.encounter",
            "com.nursecollab.domain.patient",
            "com.nursecollab.domain.nursing");

    /** 업무 쪽에만 올라가는 코드. 인증, 업무 요청, 실시간 채널, 업무 쪽 감사. */
    public static final List<String> WORK_PACKAGES = List.of(
            "com.nursecollab.domain.workorder",
            "com.nursecollab.domain.episode",
            "com.nursecollab.domain.staff",
            "com.nursecollab.domain.department",
            "com.nursecollab.domain.notification",
            "com.nursecollab.domain.stats",
            "com.nursecollab.domain.master",
            "com.nursecollab.domain.audit",
            "com.nursecollab.global.audit",
            "com.nursecollab.infra.realtime");

    /** 원내 DB 에만 있어야 하는 테이블. 업무 DB 에서 보이면 기동을 막는다. */
    public static final List<String> PHI_TABLES = List.of(
            "patient", "encounter", "patient_alert", "vital_sign", "nursing_note", "phi_access_log");

    /** 업무 DB 에만 있어야 하는 테이블. 원내 DB 에서 보이면 기동을 막는다. */
    public static final List<String> WORK_TABLES = List.of(
            "department", "staff", "service_item", "work_order", "work_order_event",
            "request_message", "notification", "care_episode", "request_no_sequence", "audit_log");

    public static boolean isPhi(String className) {
        return belongs(className, PHI_PACKAGES);
    }

    public static boolean isWork(String className) {
        return belongs(className, WORK_PACKAGES);
    }

    /** ArchUnit 이 알아듣는 모양("패키지..")으로 */
    public static String[] archPatterns(List<String> packages) {
        return packages.stream().map(p -> p + "..").toArray(String[]::new);
    }

    private static boolean belongs(String className, List<String> packages) {
        // "domain.phi" 가 "domain.phiXxx" 를 삼키지 않게 점까지 붙여 비교한다
        return packages.stream().anyMatch(p -> className.startsWith(p + "."));
    }
}
