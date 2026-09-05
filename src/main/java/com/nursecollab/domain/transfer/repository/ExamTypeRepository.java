package com.nursecollab.domain.transfer.repository;

import com.nursecollab.domain.transfer.entity.ExamType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ExamTypeRepository extends JpaRepository<ExamType, Long> {

    @Query("select e from ExamType e join fetch e.department "
            + "where e.active = true order by e.code asc")
    List<ExamType> findAllActiveWithDepartment();

    @Query("select e from ExamType e join fetch e.department "
            + "where e.active = true and e.department.id = :departmentId order by e.code asc")
    List<ExamType> findAllActiveByDepartmentWithDepartment(Long departmentId);
}
