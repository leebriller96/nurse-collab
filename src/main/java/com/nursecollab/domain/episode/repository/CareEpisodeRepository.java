package com.nursecollab.domain.episode.repository;

import com.nursecollab.domain.episode.entity.CareEpisode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CareEpisodeRepository extends JpaRepository<CareEpisode, UUID> {

    @Query("select e from CareEpisode e join fetch e.department where e.subjectRef = :subjectRef")
    Optional<CareEpisode> findWithDepartment(UUID subjectRef);

    @Query("""
            select e from CareEpisode e
            join fetch e.department
            where e.department.id = :departmentId
              and e.status = com.nursecollab.domain.encounter.entity.EncounterStatus.ADMITTED
            order by e.roomNo, e.bedNo
            """)
    List<CareEpisode> findAdmittedInDepartment(Long departmentId);

    @Query("select e from CareEpisode e join fetch e.department where e.subjectRef in :refs")
    List<CareEpisode> findAllWithDepartment(Collection<UUID> refs);
}
