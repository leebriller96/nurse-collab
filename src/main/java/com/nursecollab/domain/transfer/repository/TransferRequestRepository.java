package com.nursecollab.domain.transfer.repository;

import com.nursecollab.domain.transfer.entity.TransferPriority;
import com.nursecollab.domain.transfer.entity.TransferRequest;
import com.nursecollab.domain.transfer.entity.TransferStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
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

    @Query("""
            select r from TransferRequest r
            join fetch r.fromDepartment
            join fetch r.toDepartment
            join fetch r.examType
            join fetch r.requestedBy
            join fetch r.encounter e
            join fetch e.patient
            where r.id = :id
            """)
    Optional<TransferRequest> findDetailById(Long id);

    /**
     * 환자 정보를 볼 자격이 있는지 판단하는 근거.
     * "검사실 소속이니까" 가 아니라 "우리 파트로 온 진행중 요청이 있으니까" 로 본다.
     * 요청이 끝나면 접근 권한도 함께 사라진다.
     */
    @Query("""
            select count(r) > 0 from TransferRequest r
            where r.encounter.id = :encounterId
              and r.toDepartment.id = :departmentId
              and r.status not in :terminalStatuses
            """)
    boolean existsActiveByEncounterAndToDepartment(Long encounterId, Long departmentId,
                                                   Collection<TransferStatus> terminalStatuses);

    @Query("""
            select r from TransferRequest r
            join fetch r.examType
            where r.encounter.id = :encounterId
              and r.toDepartment.id = :departmentId
              and r.status not in :terminalStatuses
            """)
    List<TransferRequest> findActiveByEncounterAndToDepartment(Long encounterId, Long departmentId,
                                                               Collection<TransferStatus> terminalStatuses);

    @Query("""
            select r from TransferRequest r
            join fetch r.examType
            join fetch r.toDepartment
            where r.encounter.id in :encounterIds
              and r.status not in :terminalStatuses
            """)
    List<TransferRequest> findActiveByEncounterIds(Collection<Long> encounterIds,
                                                   Collection<TransferStatus> terminalStatuses);

    /**
     * 목록 조회. 병동 화면과 검사실 화면, 그리고 지난 요청 검색이 같은 쿼리를 쓴다.
     * 어느 컬럼으로 거를지는 inbound 가 정한다.
     *
     * 상태 집합은 서비스가 항상 채워서 넘긴다. 널 컬렉션을 in 절에 바인딩하면
     * Hibernate 가 조건을 만들지 못한다.
     */
    @Query(value = """
            select r from TransferRequest r
            join fetch r.examType
            join fetch r.fromDepartment
            join fetch r.toDepartment
            join fetch r.encounter e
            join fetch e.patient p
            where ((:inbound = true and r.toDepartment.id = :departmentId)
                or (:inbound = false and r.fromDepartment.id = :departmentId))
              and r.status in :statuses
              and (:priority is null or r.priority = :priority)
              and r.requestedAt >= :from and r.requestedAt < :to
              and (:keyword is null or p.name like %:keyword% or r.requestNo like %:keyword%)
            order by r.priority desc, r.requestedAt asc
            """,
            countQuery = """
            select count(r) from TransferRequest r
            join r.encounter e
            join e.patient p
            where ((:inbound = true and r.toDepartment.id = :departmentId)
                or (:inbound = false and r.fromDepartment.id = :departmentId))
              and r.status in :statuses
              and (:priority is null or r.priority = :priority)
              and r.requestedAt >= :from and r.requestedAt < :to
              and (:keyword is null or p.name like %:keyword% or r.requestNo like %:keyword%)
            """)
    Page<TransferRequest> search(boolean inbound, Long departmentId,
                                 Collection<TransferStatus> statuses, TransferPriority priority,
                                 OffsetDateTime from, OffsetDateTime to, String keyword,
                                 Pageable pageable);
}
