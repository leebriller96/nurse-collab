package com.nursecollab.domain.encounter.entity;

import com.nursecollab.domain.patient.entity.Patient;
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
import java.util.UUID;

/**
 * 재원(입원) 건.
 * 같은 환자가 몇 년 뒤 다시 입원하면 이 행이 새로 생긴다.
 * 활력징후·간호기록·이송요청은 전부 이 건을 기준으로 붙는다.
 */
@Entity
@Table(name = "encounter")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Encounter extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 업무 쪽이 이 재원 건을 가리키는 불투명 열쇠.
     *
     * 사람이 아니라 <b>재원 건</b>에 붙는다. 같은 사람이 3년 뒤 다시 입원하면 다른 값을 받는다.
     * 사람에 붙이면 업무 데이터만 보고도 "이 사람이 네 번 입원했다" 를 알 수 있고,
     * 그건 가명정보라고 부르기 어렵다.
     *
     * 이 열쇠를 사람으로 되돌리는 대응표는 이 테이블에만 있다.
     */
    @Column(name = "subject_ref", nullable = false, unique = true, updatable = false)
    private UUID subjectRef;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id")
    private Patient patient;

    /**
     * 현재 입원 중인 병동.
     *
     * 업무 쪽 department 를 가리키지만 관계로 잇지 않는다. 두 DB 로 갈라지기 때문이다.
     * 이름이 필요한 곳은 화면이 부서 목록에서 찾아 채운다 —
     * 부서는 몇 개 안 되고, 화면은 이미 그 목록을 들고 있다.
     */
    @Column(name = "department_id", nullable = false)
    private Long departmentId;

    @Column(name = "room_no", length = 10)
    private String roomNo;

    @Column(name = "bed_no", length = 10)
    private String bedNo;

    @Column(name = "admitted_at", nullable = false)
    private OffsetDateTime admittedAt;

    @Column(name = "discharged_at")
    private OffsetDateTime dischargedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EncounterStatus status = EncounterStatus.ADMITTED;

    @Column(length = 200)
    private String diagnosis;

    /** 자가 거동 가능 여부. 휠체어·침대 이송이 필요한지 판단하는 근거다. */
    @Column(name = "is_mobile", nullable = false)
    private boolean mobile = true;

    public static Encounter admit(Patient patient, Long departmentId, String roomNo,
                                  String bedNo, OffsetDateTime admittedAt,
                                  String diagnosis, boolean mobile) {
        Encounter encounter = new Encounter();
        encounter.subjectRef = UUID.randomUUID();
        encounter.patient = patient;
        encounter.departmentId = departmentId;
        encounter.roomNo = roomNo;
        encounter.bedNo = bedNo;
        encounter.admittedAt = admittedAt;
        encounter.diagnosis = diagnosis;
        encounter.mobile = mobile;
        encounter.status = EncounterStatus.ADMITTED;
        return encounter;
    }

    public void discharge() {
        this.status = EncounterStatus.DISCHARGED;
        this.dischargedAt = OffsetDateTime.now();
    }

    public boolean isAdmitted() {
        return status == EncounterStatus.ADMITTED;
    }
}
