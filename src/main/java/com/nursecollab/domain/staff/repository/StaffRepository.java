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

    boolean existsByLoginId(String loginId);

    boolean existsByEmployeeNo(String employeeNo);

    @Query("select s from Staff s join fetch s.department order by s.department.code asc, s.name asc")
    List<Staff> findAllWithDepartment();
}
