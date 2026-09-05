package com.nursecollab.domain.encounter.repository;

import com.nursecollab.domain.encounter.entity.Encounter;
import com.nursecollab.domain.encounter.entity.EncounterStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface EncounterRepository extends JpaRepository<Encounter, Long> {

    @Query("""
            select e from Encounter e
            join fetch e.patient
            join fetch e.department
            where e.id = :id
            """)
    Optional<Encounter> findByIdWithPatientAndDepartment(Long id);

    /** 환자 보드(W-01). 병실 순으로 보여야 간호사가 동선대로 확인할 수 있다. */
    @Query(value = """
            select e from Encounter e
            join fetch e.patient p
            join fetch e.department
            where e.department.id = :departmentId
              and e.status = :status
              and (:keyword is null or p.name like %:keyword% or p.patientNo like %:keyword%)
            order by e.roomNo asc, e.bedNo asc
            """,
            countQuery = """
            select count(e) from Encounter e
            join e.patient p
            where e.department.id = :departmentId
              and e.status = :status
              and (:keyword is null or p.name like %:keyword% or p.patientNo like %:keyword%)
            """)
    Page<Encounter> search(Long departmentId, EncounterStatus status, String keyword, Pageable pageable);
}
