package com.nursecollab.domain.phi.repository;

import com.nursecollab.domain.phi.entity.PhiAccessLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.OffsetDateTime;

public interface PhiAccessLogRepository extends JpaRepository<PhiAccessLog, Long> {

    /**
     * 최근 얼마 동안 이 직원이 연 <b>서로 다른</b> 환자 수.
     *
     * 요청 수가 아니라 사람 수를 센다. 같은 환자를 다섯 번 여는 것은 정상 근무이고,
     * 스무 명을 한 번씩 여는 것이 이상한 일이다.
     *
     * 거절된 시도는 세지 않는다. 이미 막힌 것을 다시 세면
     * 한 번 막힌 사람이 영영 못 열게 된다.
     */
    @Query("""
            select count(distinct l.patientId) from PhiAccessLog l
            where l.actorId = :actorId
              and l.granted = true
              and l.occurredAt >= :since
            """)
    long countDistinctPatientsSince(Long actorId, OffsetDateTime since);
}
