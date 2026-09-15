package com.nursecollab.domain.workorder.repository;

import com.nursecollab.domain.workorder.entity.ServiceItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ServiceItemRepository extends JpaRepository<ServiceItem, Long> {

    @Query("select e from ServiceItem e join fetch e.department "
            + "where e.active = true order by e.code asc")
    List<ServiceItem> findAllActiveWithDepartment();

    @Query("select e from ServiceItem e join fetch e.department "
            + "where e.active = true and e.department.id = :departmentId order by e.code asc")
    List<ServiceItem> findAllActiveByDepartmentWithDepartment(Long departmentId);

    /** 업무 요청 생성 시 수행 파트를 결정해야 하므로 파트를 함께 가져온다. */
    @Query("select e from ServiceItem e join fetch e.department where e.id = :id")
    Optional<ServiceItem> findByIdWithDepartment(Long id);

    boolean existsByCode(String code);
}
