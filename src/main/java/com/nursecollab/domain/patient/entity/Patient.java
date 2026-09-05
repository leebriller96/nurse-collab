package com.nursecollab.domain.patient.entity;

import com.nursecollab.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.Period;

/** 환자 기본정보. 재입원해도 이 행은 하나로 유지된다. */
@Entity
@Table(name = "patient")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Patient extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "patient_no", nullable = false, unique = true, length = 20)
    private String patientNo;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(name = "birth_date", nullable = false)
    private LocalDate birthDate;

    // DDL 이 CHAR(1) 인데 enum 의 기본 매핑은 VARCHAR 라 스키마 검증에서 걸린다.
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 1)
    private Sex sex;

    @Column(length = 30)
    private String phone;

    @Column(name = "guardian_phone", length = 30)
    private String guardianPhone;

    public static Patient create(String patientNo, String name, LocalDate birthDate,
                                 Sex sex, String phone, String guardianPhone) {
        Patient patient = new Patient();
        patient.patientNo = patientNo;
        patient.name = name;
        patient.birthDate = birthDate;
        patient.sex = sex;
        patient.phone = phone;
        patient.guardianPhone = guardianPhone;
        return patient;
    }

    /** 만 나이. 화면에 "68M" 처럼 표시하는 데 쓴다. */
    public int age() {
        return Period.between(birthDate, LocalDate.now()).getYears();
    }
}
