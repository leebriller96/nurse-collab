package com.nursecollab.domain.encounter.repository;

import com.nursecollab.domain.encounter.entity.Encounter;
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
}
