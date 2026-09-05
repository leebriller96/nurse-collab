package com.nursecollab.domain.transfer.repository;

import com.nursecollab.domain.transfer.entity.TransferRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface TransferRequestRepository extends JpaRepository<TransferRequest, Long> {

    /** 상태 전이는 양쪽 파트와 행위자를 모두 봐야 하므로 함께 가져온다. */
    @Query("""
            select r from TransferRequest r
            join fetch r.fromDepartment
            join fetch r.toDepartment
            where r.id = :id
            """)
    Optional<TransferRequest> findByIdWithDepartments(Long id);
}
