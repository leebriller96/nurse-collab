package com.nursecollab.domain.workorder.repository;

import com.nursecollab.domain.workorder.entity.OrderPriority;
import com.nursecollab.domain.workorder.entity.WorkOrder;
import com.nursecollab.domain.workorder.entity.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkOrderRepository extends JpaRepository<WorkOrder, Long> {

    /** 상태 전이는 양쪽 파트와 행위자를 모두 봐야 하므로 함께 가져온다. */
    @Query("""
            select r from WorkOrder r
            join fetch r.fromDepartment
            join fetch r.toDepartment
            where r.id = :id
            """)
    Optional<WorkOrder> findByIdWithDepartments(Long id);

    @Query("""
            select r from WorkOrder r
            join fetch r.fromDepartment
            join fetch r.toDepartment
            join fetch r.serviceItem
            join fetch r.requestedBy
            left join fetch r.careEpisode
            where r.id = :id
            """)
    Optional<WorkOrder> findDetailById(Long id);

    /**
     * 환자 정보를 볼 자격이 있는지 판단하는 근거.
     * "검사실 소속이니까" 가 아니라 "우리 파트로 온 진행중 요청이 있으니까" 로 본다.
     * 요청이 끝나면 접근 권한도 함께 사라진다.
     *
     * 재원 id 가 아니라 가명으로 묻는다. 판정에 환자가 누구인지는 필요 없다 —
     * 필요한 것은 "이 대상에 우리 파트로 온 요청이 걸려 있는가" 뿐이다.
     */
    @Query("""
            select count(r) > 0 from WorkOrder r
            where r.careEpisode.subjectRef = :subjectRef
              and r.toDepartment.id = :departmentId
              and r.status not in :terminalStatuses
            """)
    boolean existsActiveBySubjectAndToDepartment(UUID subjectRef, Long departmentId,
                                                 Collection<OrderStatus> terminalStatuses);

    @Query("""
            select r from WorkOrder r
            join fetch r.serviceItem
            where r.careEpisode.subjectRef = :subjectRef
              and r.toDepartment.id = :departmentId
              and r.status not in :terminalStatuses
            """)
    List<WorkOrder> findActiveBySubjectAndToDepartment(UUID subjectRef, Long departmentId,
                                                       Collection<OrderStatus> terminalStatuses);

    @Query("""
            select r from WorkOrder r
            join fetch r.serviceItem
            join fetch r.toDepartment
            join fetch r.careEpisode
            where r.careEpisode.subjectRef in :subjectRefs
              and r.status not in :terminalStatuses
            """)
    List<WorkOrder> findActiveBySubjectRefs(Collection<UUID> subjectRefs,
                                            Collection<OrderStatus> terminalStatuses);

    /**
     * 목록 조회. 병동 화면과 검사실 화면, 그리고 지난 요청 검색이 같은 쿼리를 쓴다.
     * 어느 컬럼으로 거를지는 inbound 가 정한다.
     *
     * 상태 집합은 서비스가 항상 채워서 넘긴다. 널 컬렉션을 in 절에 바인딩하면
     * Hibernate 가 조건을 만들지 못한다. subjectRefs 도 같은 이유로 비지 않게 넘긴다.
     *
     * 이름으로 찾는 조건이 여기 없는 것이 핵심이다. 이름은 업무 쪽에 없다.
     * 부르는 쪽이 원내에 이름을 물어 가명 목록을 받아 subjectRefs 로 넘긴다.
     */
    @Query(value = """
            select r from WorkOrder r
            join fetch r.serviceItem
            join fetch r.fromDepartment
            join fetch r.toDepartment
            left join fetch r.careEpisode ce
            where ((:inbound = true and r.toDepartment.id = :departmentId)
                or (:inbound = false and r.fromDepartment.id = :departmentId))
              and r.status in :statuses
              and (:priority is null or r.priority = :priority)
              and r.requestedAt >= :from and r.requestedAt < :to
              and (:keyword is null
                   or r.requestNo like %:keyword%
                   or ce.subjectRef in :subjectRefs)
            order by r.priority desc, r.requestedAt asc
            """,
            countQuery = """
            select count(r) from WorkOrder r
            left join r.careEpisode ce
            where ((:inbound = true and r.toDepartment.id = :departmentId)
                or (:inbound = false and r.fromDepartment.id = :departmentId))
              and r.status in :statuses
              and (:priority is null or r.priority = :priority)
              and r.requestedAt >= :from and r.requestedAt < :to
              and (:keyword is null
                   or r.requestNo like %:keyword%
                   or ce.subjectRef in :subjectRefs)
            """)
    Page<WorkOrder> search(boolean inbound, Long departmentId,
                                 Collection<OrderStatus> statuses, OrderPriority priority,
                                 OffsetDateTime from, OffsetDateTime to, String keyword,
                                 Collection<UUID> subjectRefs, Pageable pageable);
}
