package com.nursecollab.domain.episode.entity;

import com.nursecollab.domain.department.entity.Department;
import com.nursecollab.domain.encounter.entity.EncounterStatus;
import com.nursecollab.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
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
 * 업무 흐름이 보는 재원 정보.
 *
 * <b>사람을 가리키는 것은 여기 없다.</b> 이름도, 등록번호도, 진단명도 없다.
 * 있는 것은 "어느 병동 몇 호 몇 번 침대가 언제부터 차 있는가" 뿐이다.
 *
 * 병실·병상을 진료 쪽에 두지 않은 이유: 그것까지 원내로 보내면
 * 원내망 밖에서 병동 보드가 통째로 비어 업무 자체가 돌아가지 않는다.
 * 침대 번호는 사람을 가리키지 않는다.
 *
 * 거동 가능 여부와 진단명은 여기 없다. 그건 그 사람의 건강 상태다 —
 * 이송 준비물을 정하는 데 쓰이더라도 업무정보로 내려 보지 않는다.
 * 기준을 한 번 느슨하게 하면 다음 회색지대는 더 쉽게 넘어간다.
 */
@Entity
@Table(name = "care_episode")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CareEpisode extends BaseTimeEntity {

    /** 재원 건의 가명. 사람으로 되돌리려면 원내에 물어야 한다. */
    @Id
    @Column(name = "subject_ref", updatable = false)
    private UUID subjectRef;

    /** 현재 입원 중인 병동 */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "department_id")
    private Department department;

    @Column(name = "room_no", length = 10)
    private String roomNo;

    @Column(name = "bed_no", length = 10)
    private String bedNo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EncounterStatus status = EncounterStatus.ADMITTED;

    @Column(name = "admitted_at", nullable = false)
    private OffsetDateTime admittedAt;

    @Column(name = "discharged_at")
    private OffsetDateTime dischargedAt;

    public static CareEpisode of(UUID subjectRef, Department department, String roomNo,
                                 String bedNo, OffsetDateTime admittedAt) {
        CareEpisode episode = new CareEpisode();
        episode.subjectRef = subjectRef;
        episode.department = department;
        episode.roomNo = roomNo;
        episode.bedNo = bedNo;
        episode.admittedAt = admittedAt;
        episode.status = EncounterStatus.ADMITTED;
        return episode;
    }

    public boolean isAdmitted() {
        return status == EncounterStatus.ADMITTED;
    }

    /**
     * 침대가 비었다. 퇴원 시각은 원내가 알려 준다.
     * 지우지 않는 이유는 재원과 같다 — 이 침대에 걸려 있던 지난 요청이 이 행을 참조한다.
     */
    public void discharge(OffsetDateTime dischargedAt) {
        this.status = EncounterStatus.DISCHARGED;
        this.dischargedAt = dischargedAt;
    }

    public void moveTo(Department department, String roomNo, String bedNo) {
        this.department = department;
        this.roomNo = roomNo;
        this.bedNo = bedNo;
    }
}
