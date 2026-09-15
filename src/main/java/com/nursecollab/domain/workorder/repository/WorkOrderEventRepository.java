package com.nursecollab.domain.workorder.repository;

import com.nursecollab.domain.workorder.entity.WorkOrderEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface WorkOrderEventRepository extends JpaRepository<WorkOrderEvent, Long> {

    @Query("""
            select e from WorkOrderEvent e
            join fetch e.actor
            join fetch e.actorDept
            where e.request.id = :requestId
            order by e.occurredAt asc, e.id asc
            """)
    List<WorkOrderEvent> findAllByRequestId(Long requestId);
}
