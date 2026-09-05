package com.nursecollab.domain.stats.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * 대기시간 통계.
 *
 * 값은 전부 transfer_event 에서 계산한다. 상태를 컬럼에만 두고 이력을 남기지 않았다면
 * "언제 접수됐는지" 를 알 수 없어 이 화면 자체가 만들어지지 않는다.
 */
public record WaitingTimeStats(
        Period period,
        Overall overall,
        List<ByDepartment> byDepartment,
        List<ByHour> byHour
) {
    public record Period(LocalDate from, LocalDate to) {}

    /**
     * @param avgWaitingMinutes 요청 → 접수까지. 검사실이 얼마나 빨리 받아주는가.
     * @param avgTotalMinutes   요청 → 완료까지. 환자가 실제로 겪는 전체 시간.
     */
    public record Overall(
            long totalRequests,
            Integer avgWaitingMinutes,
            Integer avgTotalMinutes
    ) {}

    public record ByDepartment(
            Long departmentId,
            String departmentName,
            long requestCount,
            Integer avgWaitingMinutes,
            long holdCount
    ) {}

    public record ByHour(int hour, long requestCount) {}
}
