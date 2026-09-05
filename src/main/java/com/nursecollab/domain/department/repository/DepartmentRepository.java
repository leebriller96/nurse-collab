package com.nursecollab.domain.department.repository;

import com.nursecollab.domain.department.entity.Department;
import com.nursecollab.domain.department.entity.DeptType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DepartmentRepository extends JpaRepository<Department, Long> {

    List<Department> findAllByActiveTrueOrderByCodeAsc();

    List<Department> findAllByDeptTypeAndActiveTrueOrderByCodeAsc(DeptType deptType);
}
