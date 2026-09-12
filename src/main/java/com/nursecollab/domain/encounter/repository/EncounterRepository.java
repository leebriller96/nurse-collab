package com.nursecollab.domain.encounter.repository;

import com.nursecollab.domain.encounter.entity.Encounter;
import com.nursecollab.domain.encounter.entity.EncounterStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EncounterRepository extends JpaRepository<Encounter, Long> {

    /** 업무 쪽이 들고 있는 가명으로 사람을 되찾는다. 이 대응은 여기에만 있다. */
    @Query("""
            select e from Encounter e
            join fetch e.patient
            where e.subjectRef = :subjectRef
            """)
    Optional<Encounter> findBySubjectRef(UUID subjectRef);

    @Query("""
            select e from Encounter e
            join fetch e.patient
            where e.subjectRef in :subjectRefs
            """)
    List<Encounter> findAllBySubjectRefs(Collection<UUID> subjectRefs);

    /**
     * 이름 일부로 재원 중인 건을 찾는다.
     * 업무 쪽 목록에서 이름으로 검색하려면 먼저 여기서 가명을 받아야 한다.
     * 이름은 원내 밖으로 나가지 않고, 나가는 것은 가명뿐이다.
     */
    @Query("""
            select e from Encounter e
            join fetch e.patient p
            where e.status = com.nursecollab.domain.encounter.entity.EncounterStatus.ADMITTED
              and p.name like %:namePart%
            """)
    List<Encounter> findAdmittedByPatientNameLike(String namePart);

    @Query("""
            select e from Encounter e
            join fetch e.patient
            where e.id = :id
            """)
    Optional<Encounter> findByIdWithPatientAndDepartment(Long id);

    /**
     * 환자의 재원 중인 건.
     *
     * 주의사항은 환자에 붙지만(퇴원한다고 인공관절이 사라지지 않는다)
     * 접근 판정은 재원 기준이다. 그 연결을 여기서 만든다.
     */
    @Query("""
            select e from Encounter e
            join fetch e.patient
            where e.patient.id = :patientId
              and e.status = com.nursecollab.domain.encounter.entity.EncounterStatus.ADMITTED
            """)
    Optional<Encounter> findAdmittedByPatientId(Long patientId);

    /** 환자 보드(W-01). 병실 순으로 보여야 간호사가 동선대로 확인할 수 있다. */
    @Query(value = """
            select e from Encounter e
            join fetch e.patient p
            where e.departmentId = :departmentId
              and e.status = :status
              and (:keyword is null or p.name like %:keyword% or p.patientNo like %:keyword%)
            order by e.roomNo asc, e.bedNo asc
            """,
            countQuery = """
            select count(e) from Encounter e
            join e.patient p
            where e.departmentId = :departmentId
              and e.status = :status
              and (:keyword is null or p.name like %:keyword% or p.patientNo like %:keyword%)
            """)
    Page<Encounter> search(Long departmentId, EncounterStatus status, String keyword, Pageable pageable);
}
