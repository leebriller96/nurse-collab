package com.nursecollab.global.audit;

/**
 * 감사 대상이 어느 환자에 관한 것인지 알아낸다.
 *
 * global 은 domain 을 알면 안 되므로 인터페이스만 여기 두고 구현은 도메인이 가져간다.
 * 환자 식별자를 함께 남겨야 "이 환자를 누가 열어봤는지" 를 한 번에 뽑을 수 있다.
 */
public interface AuditTargetResolver {

    /** 이 해석기가 담당하는 targetType */
    String targetType();

    Long resolvePatientId(Long targetId);
}
