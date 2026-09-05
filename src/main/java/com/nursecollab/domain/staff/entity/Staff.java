package com.nursecollab.domain.staff.entity;

import com.nursecollab.domain.department.entity.Department;
import com.nursecollab.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Entity
@Table(name = "staff")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Staff extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "login_id", nullable = false, unique = true, length = 50)
    private String loginId;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "employee_no", nullable = false, unique = true, length = 30)
    private String employeeNo;

    @Column(nullable = false, length = 50)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StaffRole role;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "department_id")
    private Department department;

    @Column(length = 30)
    private String phone;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "last_login_at")
    private OffsetDateTime lastLoginAt;

    public static Staff create(String loginId, String passwordHash, String employeeNo,
                               String name, StaffRole role, Department department, String phone) {
        Staff staff = new Staff();
        staff.loginId = loginId;
        staff.passwordHash = passwordHash;
        staff.employeeNo = employeeNo;
        staff.name = name;
        staff.role = role;
        staff.department = department;
        staff.phone = phone;
        staff.active = true;
        return staff;
    }

    public void recordLogin() {
        this.lastLoginAt = OffsetDateTime.now();
    }

    public void changePassword(String newPasswordHash) {
        this.passwordHash = newPasswordHash;
    }

    public void updateProfile(String name, StaffRole role, Department department, String phone) {
        this.name = name;
        this.role = role;
        this.department = department;
        this.phone = phone;
    }

    /** 퇴사해도 계정을 지우지 않는다. 이 사람이 남긴 기록과 이력이 그대로 남아 있다. */
    public void deactivate() {
        this.active = false;
    }

    public boolean isAdmin() {
        return role == StaffRole.ADMIN;
    }
}
