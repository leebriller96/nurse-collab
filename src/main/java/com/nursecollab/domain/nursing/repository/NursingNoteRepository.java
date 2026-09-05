package com.nursecollab.domain.nursing.repository;

import com.nursecollab.domain.nursing.entity.NoteType;
import com.nursecollab.domain.nursing.entity.NursingNote;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface NursingNoteRepository extends JpaRepository<NursingNote, Long> {

    @Query(value = """
            select n from NursingNote n
            join fetch n.recordedBy s
            join fetch s.department
            where n.encounter.id = :encounterId
              and (:noteType is null or n.noteType = :noteType)
            order by n.recordedAt desc
            """,
            countQuery = """
            select count(n) from NursingNote n
            where n.encounter.id = :encounterId
              and (:noteType is null or n.noteType = :noteType)
            """)
    Page<NursingNote> search(Long encounterId, NoteType noteType, Pageable pageable);

    @Query("""
            select n from NursingNote n
            join fetch n.recordedBy s
            join fetch s.department
            join fetch n.encounter
            where n.id = :id
            """)
    Optional<NursingNote> findDetailById(Long id);
}
