package com.nursecollab.domain.staff.repository;

import com.nursecollab.domain.staff.entity.Staff;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface StaffRepository extends JpaRepository<Staff, Long> {

    /** 로그인 직후 소속 파트 정보까지 응답에 담아야 하므로 함께 가져온다. */
    @Query("select s from Staff s join fetch s.department where s.loginId = :loginId")
    Optional<Staff> findByLoginIdWithDepartment(String loginId);

    @Query("select s from Staff s join fetch s.department where s.id = :id")
    Optional<Staff> findByIdWithDepartment(Long id);

    /** 알림 수신자. 비활성 계정에는 보내지 않는다. */
    @Query("select s.id from Staff s where s.department.id in :departmentIds and s.active = true")
    List<Long> findActiveIdsByDepartmentIds(Collection<Long> departmentIds);

    /**
     * 이 요청에 손댄 활성 직원과 그 소속. 요청한 사람, 진행 기록의 행위자, 메시지를 남긴 사람이다.
     * 알림을 누구에게 보낼지 정하는 데 쓴다.
     */
    @Query("""
            select new com.nursecollab.domain.staff.repository.StaffRepository$Involved(s.id, s.department.id)
            from Staff s
            where s.active = true
              and (s.id = (select r.requestedBy.id from WorkOrder r where r.id = :orderId)
                   or s.id in (select e.actor.id from WorkOrderEvent e where e.request.id = :orderId)
                   or s.id in (select m.sender.id from RequestMessage m where m.request.id = :orderId))
            """)
    List<Involved> findInvolvedInOrder(Long orderId);

    record Involved(Long staffId, Long departmentId) {
    }

    boolean existsByLoginId(String loginId);

    boolean existsByEmployeeNo(String employeeNo);

    @Query("select s from Staff s join fetch s.department order by s.department.code asc, s.name asc")
    List<Staff> findAllWithDepartment();
}
