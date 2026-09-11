package com.nursecollab.domain.workorder.repository;

import com.nursecollab.domain.workorder.entity.RequestMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface RequestMessageRepository extends JpaRepository<RequestMessage, Long> {

    @Query("""
            select m from RequestMessage m
            join fetch m.sender s
            join fetch s.department
            where m.request.id = :requestId
            order by m.createdAt asc, m.id asc
            """)
    List<RequestMessage> findAllByRequestId(Long requestId);
}
