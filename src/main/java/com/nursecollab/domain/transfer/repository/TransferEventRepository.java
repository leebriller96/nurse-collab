package com.nursecollab.domain.transfer.repository;

import com.nursecollab.domain.transfer.entity.TransferEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface TransferEventRepository extends JpaRepository<TransferEvent, Long> {

    @Query("""
            select e from TransferEvent e
            join fetch e.actor
            join fetch e.actorDept
            where e.request.id = :requestId
            order by e.occurredAt asc, e.id asc
            """)
    List<TransferEvent> findAllByRequestId(Long requestId);
}
