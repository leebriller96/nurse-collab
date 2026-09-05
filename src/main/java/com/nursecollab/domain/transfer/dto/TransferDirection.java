package com.nursecollab.domain.transfer.dto;

/**
 * 목록 조회 방향.
 * 병동 화면(W-04)과 검사실 화면(E-01)이 같은 엔드포인트를 쓰게 해주는 장치다.
 */
public enum TransferDirection {

    /** 우리 파트로 들어온 요청 (검사실 큐) */
    INBOUND,

    /** 우리 파트가 보낸 요청 (병동 현황) */
    OUTBOUND;

    public boolean isInbound() {
        return this == INBOUND;
    }
}
