package com.nursecollab.domain.department.entity;

import com.nursecollab.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "department")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Department extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "dept_type", nullable = false, length = 20)
    private DeptType deptType;

    @Column(length = 100)
    private String location;

    @Column(length = 30)
    private String phone;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    public static Department create(String code, String name, DeptType deptType,
                                    String location, String phone) {
        Department department = new Department();
        department.code = code;
        department.name = name;
        department.deptType = deptType;
        department.location = location;
        department.phone = phone;
        department.active = true;
        return department;
    }

    public void update(String code, String name, DeptType deptType,
                       String location, String phone) {
        this.code = code;
        this.name = name;
        this.deptType = deptType;
        this.location = location;
        this.phone = phone;
    }

    /** 지우지 않는다. 지난 요청 이력이 이 부서를 참조하고 있다. */
    public void deactivate() {
        this.active = false;
    }

    public boolean isWard() {
        return deptType == DeptType.WARD;
    }

    public boolean isExam() {
        return deptType == DeptType.EXAM;
    }
}
