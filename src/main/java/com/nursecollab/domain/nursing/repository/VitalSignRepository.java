package com.nursecollab.domain.nursing.repository;

import com.nursecollab.domain.nursing.entity.VitalSign;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.OffsetDateTime;

public interface VitalSignRepository extends JpaRepository<VitalSign, Long> {

    @Query(value = """
            select v from VitalSign v
            join fetch v.recordedBy
            where v.encounter.id = :encounterId
              and (cast(:from as timestamp) is null or v.measuredAt >= :from)
              and (cast(:to as timestamp) is null or v.measuredAt < :to)
            order by v.measuredAt desc
            """,
            countQuery = """
            select count(v) from VitalSign v
            where v.encounter.id = :encounterId
              and (cast(:from as timestamp) is null or v.measuredAt >= :from)
              and (cast(:to as timestamp) is null or v.measuredAt < :to)
            """)
    Page<VitalSign> search(Long encounterId, OffsetDateTime from, OffsetDateTime to, Pageable pageable);
}
