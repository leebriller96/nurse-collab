package com.nursecollab.global.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.OffsetDateTime;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    @Query(value = """
            select a from AuditLog a
            where a.occurredAt >= :from and a.occurredAt < :to
              and (:patientId is null or a.patientId = :patientId)
              and (:actorId is null or a.actorId = :actorId)
            order by a.occurredAt desc
            """,
            countQuery = """
            select count(a) from AuditLog a
            where a.occurredAt >= :from and a.occurredAt < :to
              and (:patientId is null or a.patientId = :patientId)
              and (:actorId is null or a.actorId = :actorId)
            """)
    Page<AuditLog> search(OffsetDateTime from, OffsetDateTime to,
                          Long patientId, Long actorId, Pageable pageable);
}
