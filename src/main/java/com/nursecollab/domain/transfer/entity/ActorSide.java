package com.nursecollab.domain.transfer.entity;

/**
 * 상태를 변경하려는 사람이 요청 파트(병동)인지 수행 파트(검사실)인지를 구분한다.
 * 같은 상태라도 누가 누르느냐에 따라 허용 여부가 달라지기 때문에 필요하다.
 */
public enum ActorSide {
    REQUESTER,  // 요청 파트 (병동)
    PERFORMER,  // 수행 파트 (검사실)
    BOTH        // 양쪽 모두 가능
}
