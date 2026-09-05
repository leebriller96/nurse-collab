package com.nursecollab.domain.patient.repository;

import com.nursecollab.domain.patient.entity.PatientAlert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;

public interface PatientAlertRepository extends JpaRepository<PatientAlert, Long> {

    @Query("""
            select a from PatientAlert a
            where a.patient.id = :patientId and a.active = true
            order by a.createdAt desc
            """)
    List<PatientAlert> findActiveByPatientIdUnsorted(Long patientId);

    /** 목록 화면에서 환자 수만큼 쿼리가 나가지 않도록 한 번에 가져온다 */
    @Query("""
            select a from PatientAlert a
            where a.patient.id in :patientIds and a.active = true
            order by a.createdAt desc
            """)
    List<PatientAlert> findActiveByPatientIdsUnsorted(Collection<Long> patientIds);

    default List<PatientAlert> findActiveByPatientId(Long patientId) {
        return sortBySeverity(findActiveByPatientIdUnsorted(patientId));
    }

    default List<PatientAlert> findActiveByPatientIds(Collection<Long> patientIds) {
        return sortBySeverity(findActiveByPatientIdsUnsorted(patientIds));
    }

    /**
     * severity 는 문자열로 저장되므로 DB 에서 정렬하면 알파벳순(WARN > INFO > CRITICAL)이 된다.
     * 화면에는 중대한 것이 먼저 보여야 하므로 enum 선언 순서로 다시 세운다.
     */
    private static List<PatientAlert> sortBySeverity(List<PatientAlert> alerts) {
        return alerts.stream()
                .sorted(Comparator.comparing(PatientAlert::getSeverity).reversed())
                .toList();
    }
}
