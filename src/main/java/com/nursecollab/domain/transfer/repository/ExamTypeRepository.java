package com.nursecollab.domain.transfer.repository;

import com.nursecollab.domain.transfer.entity.ExamType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ExamTypeRepository extends JpaRepository<ExamType, Long> {

    @Query("select e from ExamType e join fetch e.department "
            + "where e.active = true order by e.code asc")
    List<ExamType> findAllActiveWithDepartment();

    @Query("select e from ExamType e join fetch e.department "
            + "where e.active = true and e.department.id = :departmentId order by e.code asc")
    List<ExamType> findAllActiveByDepartmentWithDepartment(Long departmentId);

    /** 이송 요청 생성 시 수행 파트를 결정해야 하므로 파트를 함께 가져온다. */
    @Query("select e from ExamType e join fetch e.department where e.id = :id")
    Optional<ExamType> findByIdWithDepartment(Long id);

    boolean existsByCode(String code);
}
