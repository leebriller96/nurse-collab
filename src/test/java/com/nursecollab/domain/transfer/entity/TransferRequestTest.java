package com.nursecollab.domain.transfer.entity;

import com.nursecollab.domain.department.entity.Department;
import com.nursecollab.domain.department.entity.DeptType;
import com.nursecollab.domain.encounter.entity.Encounter;
import com.nursecollab.domain.patient.entity.Patient;
import com.nursecollab.domain.patient.entity.AlertType;
import com.nursecollab.domain.patient.entity.Sex;
import com.nursecollab.domain.staff.entity.Staff;
import com.nursecollab.domain.staff.entity.StaffRole;
import com.nursecollab.global.error.BusinessException;
import com.nursecollab.global.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TransferRequestTest {

    private Department ward;      // 3병동
    private Department mri;       // MRI실
    private Department ct;        // CT실 (관계없는 파트)
    private Staff wardNurse;
    private Staff mriNurse;
    private Staff ctNurse;
    private Encounter encounter;
    private ExamType brainMri;

    @BeforeEach
    void setUp() {
        ward = department(1L, "W03", "3병동", DeptType.WARD);
        mri  = department(3L, "MRI", "MRI실", DeptType.EXAM);
        ct   = department(4L, "CT",  "CT실",  DeptType.EXAM);

        wardNurse = staff(11L, "ward01", "김간호", ward);
        mriNurse  = staff(21L, "mri01",  "박간호", mri);
        ctNurse   = staff(31L, "ct01",   "최간호", ct);

        Patient patient = Patient.create("P0001234", "김OO",
                LocalDate.of(1958, 3, 11), Sex.M, null, null);
        encounter = Encounter.admit(patient, ward, "302", "1",
                OffsetDateTime.now().minusDays(4), "뇌경색", false);
        ReflectionTestUtils.setField(encounter, "id", 501L);

        brainMri = ExamType.create("MRI_BRAIN", "뇌 MRI", mri, 40,
                "검사 4시간 전부터 금식", List.of(AlertType.METAL_IMPLANT));
    }

    // ------------------------------------------------------------------
    // 생성
    // ------------------------------------------------------------------

    @Test
    void 생성하면_요청됨_상태이고_수행_파트는_검사_종류가_결정한다() {
        TransferRequest request = newRequest();

        assertThat(request.getStatus()).isEqualTo(TransferStatus.REQUESTED);
        assertThat(request.getFromDepartment()).isEqualTo(ward);
        assertThat(request.getToDepartment()).isEqualTo(mri);
        assertThat(request.getRequestedAt()).isNotNull();
    }

    @Test
    void 퇴원한_환자로는_요청을_만들_수_없다() {
        encounter.discharge();

        assertThat(errorOf(this::newRequest)).isEqualTo(ErrorCode.DISCHARGED_ENCOUNTER);
    }

    @Test
    void 다른_병동의_환자로는_요청을_만들_수_없다() {
        Staff otherWardNurse = staff(12L, "ward02", "이간호",
                department(2L, "W05", "5병동", DeptType.WARD));

        assertThat(errorOf(() -> TransferRequest.create("TR20260905-0001", encounter, brainMri,
                otherWardNurse, TransferPriority.ROUTINE, null, null)))
                .isEqualTo(ErrorCode.NOT_RELATED_DEPARTMENT);
    }

    // ------------------------------------------------------------------
    // 행위자 판정
    // ------------------------------------------------------------------

    @Test
    void 병동은_요청측_검사실은_수행측으로_판정된다() {
        TransferRequest request = newRequest();

        assertThat(request.resolveActorSide(wardNurse)).isEqualTo(ActorSide.REQUESTER);
        assertThat(request.resolveActorSide(mriNurse)).isEqualTo(ActorSide.PERFORMER);
    }

    @Test
    void 관계없는_파트는_행위자로_인정되지_않는다() {
        TransferRequest request = newRequest();

        assertThat(errorOf(() -> request.resolveActorSide(ctNurse)))
                .isEqualTo(ErrorCode.NOT_RELATED_DEPARTMENT);
    }

    @Test
    void 관계없는_파트는_누를_수_있는_버튼_목록도_볼_수_없다() {
        TransferRequest request = newRequest();

        assertThat(errorOf(() -> request.availableTransitions(ctNurse)))
                .isEqualTo(ErrorCode.NOT_RELATED_DEPARTMENT);
    }

    // ------------------------------------------------------------------
    // 접수
    // ------------------------------------------------------------------

    @Test
    void 검사실이_접수하면_예정시각이_기록된다() {
        TransferRequest request = newRequest();
        OffsetDateTime scheduled = OffsetDateTime.now().plusHours(1);

        request.transitionTo(TransferStatus.ACCEPTED, ActorSide.PERFORMER, null, scheduled);

        assertThat(request.getStatus()).isEqualTo(TransferStatus.ACCEPTED);
        assertThat(request.getScheduledAt()).isEqualTo(scheduled);
    }

    @Test
    void 접수할_때_예정시각이_없으면_실패한다() {
        TransferRequest request = newRequest();

        assertThat(errorOf(() -> request.transitionTo(
                TransferStatus.ACCEPTED, ActorSide.PERFORMER, null, null)))
                .isEqualTo(ErrorCode.SCHEDULE_REQUIRED);
        assertThat(request.getStatus()).isEqualTo(TransferStatus.REQUESTED);
    }

    @Test
    void 병동은_접수할_수_없다() {
        TransferRequest request = newRequest();

        assertThat(errorOf(() -> request.transitionTo(TransferStatus.ACCEPTED,
                ActorSide.REQUESTER, null, OffsetDateTime.now())))
                .isEqualTo(ErrorCode.NOT_ALLOWED_ACTOR);
        assertThat(request.getStatus()).isEqualTo(TransferStatus.REQUESTED);
    }

    @Test
    void 규칙표에_없는_전이는_거부된다() {
        TransferRequest request = newRequest();

        assertThat(errorOf(() -> request.transitionTo(
                TransferStatus.IN_PROGRESS, ActorSide.PERFORMER, null, null)))
                .isEqualTo(ErrorCode.INVALID_TRANSITION);
    }

    // ------------------------------------------------------------------
    // 보류와 복귀
    // ------------------------------------------------------------------

    @Test
    void 보류하면_직전_상태를_기억한다() {
        TransferRequest request = accepted();

        request.transitionTo(TransferStatus.ON_HOLD, ActorSide.PERFORMER, "장비 점검", null);

        assertThat(request.getStatus()).isEqualTo(TransferStatus.ON_HOLD);
        assertThat(request.getHoldFromStatus()).isEqualTo(TransferStatus.ACCEPTED);
        assertThat(request.getHoldReason()).isEqualTo("장비 점검");
    }

    @Test
    void 보류할_때_사유가_없으면_실패한다() {
        TransferRequest request = accepted();

        assertThat(errorOf(() -> request.transitionTo(
                TransferStatus.ON_HOLD, ActorSide.PERFORMER, "   ", null)))
                .isEqualTo(ErrorCode.REASON_REQUIRED);
        assertThat(request.getStatus()).isEqualTo(TransferStatus.ACCEPTED);
    }

    @Test
    void 보류를_해제하면_직전_상태로_돌아오고_사유가_지워진다() {
        TransferRequest request = accepted();
        request.transitionTo(TransferStatus.ON_HOLD, ActorSide.PERFORMER, "응급 환자 우선", null);

        request.transitionTo(TransferStatus.ACCEPTED, ActorSide.PERFORMER, null, null);

        assertThat(request.getStatus()).isEqualTo(TransferStatus.ACCEPTED);
        assertThat(request.getHoldFromStatus()).isNull();
        assertThat(request.getHoldReason()).isNull();
    }

    @Test
    void 보류_해제_시_직전_상태가_아닌_곳으로는_갈_수_없다() {
        TransferRequest request = accepted();
        request.transitionTo(TransferStatus.ON_HOLD, ActorSide.PERFORMER, "응급 환자 우선", null);

        assertThat(errorOf(() -> request.transitionTo(
                TransferStatus.READY, ActorSide.PERFORMER, null, null)))
                .isEqualTo(ErrorCode.INVALID_TRANSITION);
        assertThat(request.getStatus()).isEqualTo(TransferStatus.ON_HOLD);
    }

    @Test
    void 보류_상태에서는_복귀와_취소만_보인다() {
        TransferRequest request = accepted();
        request.transitionTo(TransferStatus.ON_HOLD, ActorSide.PERFORMER, "장비 점검", null);

        assertThat(request.availableTransitions(mriNurse))
                .containsExactlyInAnyOrder(TransferStatus.ACCEPTED, TransferStatus.CANCELLED);
    }

    @Test
    void 보류_상태에서도_취소는_가능하다() {
        TransferRequest request = accepted();
        request.transitionTo(TransferStatus.ON_HOLD, ActorSide.PERFORMER, "장비 점검", null);

        request.transitionTo(TransferStatus.CANCELLED, ActorSide.PERFORMER, "검사 취소", null);

        assertThat(request.getStatus()).isEqualTo(TransferStatus.CANCELLED);
    }

    // ------------------------------------------------------------------
    // 종료
    // ------------------------------------------------------------------

    @Test
    void 종료된_요청은_더_이상_바꿀_수_없다() {
        TransferRequest request = newRequest();
        request.transitionTo(TransferStatus.CANCELLED, ActorSide.REQUESTER, "환자 거부", null);

        assertThat(errorOf(() -> request.transitionTo(
                TransferStatus.ACCEPTED, ActorSide.PERFORMER, null, OffsetDateTime.now())))
                .isEqualTo(ErrorCode.ALREADY_FINISHED);
    }

    @Test
    void 종료_상태에서는_누를_수_있는_버튼이_없다() {
        TransferRequest request = newRequest();
        request.transitionTo(TransferStatus.CANCELLED, ActorSide.REQUESTER, "환자 거부", null);

        assertThat(request.availableTransitions(wardNurse)).isEmpty();
    }

    // ------------------------------------------------------------------
    // 전체 흐름
    // ------------------------------------------------------------------

    @Test
    void 요청부터_완료까지_전체_흐름을_통과한다() {
        TransferRequest request = newRequest();
        OffsetDateTime scheduled = OffsetDateTime.now().plusHours(1);

        request.transitionTo(TransferStatus.ACCEPTED,    ActorSide.PERFORMER, null, scheduled);
        request.transitionTo(TransferStatus.READY,       ActorSide.PERFORMER, null, null);
        request.transitionTo(TransferStatus.IN_TRANSIT,  ActorSide.REQUESTER, null, null);
        request.transitionTo(TransferStatus.IN_PROGRESS, ActorSide.PERFORMER, null, null);
        request.transitionTo(TransferStatus.RETURNED,    ActorSide.PERFORMER, null, null);
        request.transitionTo(TransferStatus.COMPLETED,   ActorSide.REQUESTER, null, null);

        assertThat(request.getStatus()).isEqualTo(TransferStatus.COMPLETED);
        assertThat(request.getScheduledAt()).isEqualTo(scheduled);
        assertThat(request.getStartedAt()).isNotNull();
        assertThat(request.getCompletedAt()).isNotNull();
    }

    // ------------------------------------------------------------------
    // 픽스처
    // ------------------------------------------------------------------

    private TransferRequest newRequest() {
        return TransferRequest.create("TR20260905-0001", encounter, brainMri, wardNurse,
                TransferPriority.URGENT, null, "휠체어 이송 필요");
    }

    private TransferRequest accepted() {
        TransferRequest request = newRequest();
        request.transitionTo(TransferStatus.ACCEPTED, ActorSide.PERFORMER,
                null, OffsetDateTime.now().plusHours(1));
        return request;
    }

    private ErrorCode errorOf(Runnable action) {
        try {
            action.run();
        } catch (BusinessException e) {
            return e.getErrorCode();
        }
        throw new AssertionError("BusinessException 이 발생하지 않았습니다.");
    }

    private Department department(Long id, String code, String name, DeptType type) {
        Department department = Department.create(code, name, type, null, null);
        ReflectionTestUtils.setField(department, "id", id);
        return department;
    }

    private Staff staff(Long id, String loginId, String name, Department department) {
        Staff created = Staff.create(loginId, "hash", "E" + id, name,
                StaffRole.NURSE, department, null);
        ReflectionTestUtils.setField(created, "id", id);
        return created;
    }
}
