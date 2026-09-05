package com.nursecollab.domain.transfer.service;

import com.nursecollab.domain.department.entity.Department;
import com.nursecollab.domain.encounter.entity.Encounter;
import com.nursecollab.domain.encounter.repository.EncounterRepository;
import com.nursecollab.domain.patient.entity.Patient;
import com.nursecollab.domain.patient.entity.Sex;
import com.nursecollab.domain.patient.repository.PatientRepository;
import com.nursecollab.domain.staff.entity.Staff;
import com.nursecollab.domain.staff.repository.StaffRepository;
import com.nursecollab.domain.transfer.dto.TransferCreateRequest;
import com.nursecollab.domain.transfer.dto.TransferCreateResponse;
import com.nursecollab.domain.transfer.dto.TransitionRequest;
import com.nursecollab.domain.transfer.dto.TransitionResponse;
import com.nursecollab.domain.transfer.entity.ExamType;
import com.nursecollab.domain.transfer.entity.TransferPriority;
import com.nursecollab.domain.transfer.entity.TransferStatus;
import com.nursecollab.domain.transfer.repository.ExamTypeRepository;
import com.nursecollab.domain.transfer.repository.TransferEventRepository;
import com.nursecollab.global.error.BusinessException;
import com.nursecollab.global.error.ErrorCode;
import com.nursecollab.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.OptimisticLockingFailureException;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class TransferRequestServiceTest extends IntegrationTest {

    /** 환자 등록번호가 겹치지 않게 테스트마다 새로 딴다 */
    private static final AtomicInteger PATIENT_SEQ = new AtomicInteger(1);

    @Autowired private TransferRequestService transferRequestService;
    @Autowired private StaffRepository staffRepository;
    @Autowired private PatientRepository patientRepository;
    @Autowired private EncounterRepository encounterRepository;
    @Autowired private ExamTypeRepository examTypeRepository;
    @Autowired private TransferEventRepository eventRepository;

    private Staff wardNurse;
    private Staff mriNurse;
    private Encounter encounter;
    private ExamType brainMri;

    @BeforeEach
    void setUp() {
        // V3 시드로 들어간 마스터를 그대로 쓴다
        wardNurse = staffRepository.findByLoginIdWithDepartment("ward01").orElseThrow();
        mriNurse = staffRepository.findByLoginIdWithDepartment("mri01").orElseThrow();
        brainMri = examTypeRepository.findAllActiveWithDepartment().stream()
                .filter(e -> e.getCode().equals("MRI_BRAIN"))
                .findFirst().orElseThrow();

        Department ward = wardNurse.getDepartment();
        Patient patient = patientRepository.save(Patient.create(
                "P%07d".formatted(PATIENT_SEQ.getAndIncrement()), "김OO",
                LocalDate.of(1958, 3, 11), Sex.M, null, null));
        encounter = encounterRepository.save(Encounter.admit(patient, ward, "302", "1",
                OffsetDateTime.now().minusDays(4), "뇌경색", false));
    }

    @Test
    void 요청을_만들면_최초_이력이_함께_쌓인다() {
        TransferCreateResponse created = createRequest();

        assertThat(created.requestNo()).startsWith("TR");
        assertThat(created.status()).isEqualTo(TransferStatus.REQUESTED);
        assertThat(created.version()).isZero();

        var events = eventRepository.findAllByRequestId(created.id());
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getFromStatus()).isNull();
        assertThat(events.get(0).getToStatus()).isEqualTo(TransferStatus.REQUESTED);
        assertThat(events.get(0).getActor().getId()).isEqualTo(wardNurse.getId());
    }

    @Test
    void 검사실이_접수하면_상태와_이력과_버전이_함께_바뀐다() {
        TransferCreateResponse created = createRequest();
        OffsetDateTime scheduled = OffsetDateTime.now().plusHours(1);

        TransitionResponse response = transferRequestService.transition(
                created.id(),
                new TransitionRequest("ACCEPTED", null, scheduled, created.version()),
                mriNurse.getId());

        assertThat(response.status()).isEqualTo(TransferStatus.ACCEPTED);
        assertThat(response.version()).isGreaterThan(created.version());
        assertThat(response.availableTransitions())
                .containsExactlyInAnyOrder(TransferStatus.READY,
                        TransferStatus.ON_HOLD, TransferStatus.CANCELLED);
        assertThat(eventRepository.findAllByRequestId(created.id())).hasSize(2);
    }

    @Test
    void 요청번호는_동시에_만들어도_겹치지_않는다() throws Exception {
        int threads = 8;
        List<String> requestNos = runConcurrently(threads, () -> createRequest().requestNo());

        assertThat(requestNos).hasSize(threads).doesNotHaveDuplicates();
    }

    @Test
    void 두_명이_동시에_접수하면_한_명은_실패한다() throws Exception {
        TransferCreateResponse created = createRequest();
        OffsetDateTime scheduled = OffsetDateTime.now().plusHours(1);
        // 두 화면 모두 같은 버전을 보고 있는 상황을 재현한다
        TransitionRequest sameVersionRequest =
                new TransitionRequest("ACCEPTED", null, scheduled, created.version());

        List<Throwable> failures = new CopyOnWriteArrayList<>();
        List<TransitionResponse> successes = new CopyOnWriteArrayList<>();

        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            for (int i = 0; i < 2; i++) {
                pool.submit(() -> {
                    try {
                        start.await();
                        successes.add(transferRequestService.transition(
                                created.id(), sameVersionRequest, mriNurse.getId()));
                    } catch (Throwable t) {
                        failures.add(t);
                    } finally {
                        done.countDown();
                    }
                });
            }
            start.countDown();
            assertThat(done.await(20, TimeUnit.SECONDS)).isTrue();
        } finally {
            pool.shutdownNow();
        }

        assertThat(successes).hasSize(1);
        assertThat(failures).hasSize(1);

        Throwable failure = failures.get(0);
        boolean rejected = failure instanceof OptimisticLockingFailureException
                || (failure instanceof BusinessException e
                        && e.getErrorCode() == ErrorCode.VERSION_CONFLICT);

        assertThat(rejected)
                .as("진 쪽은 낙관적 락 충돌로 걸러져야 한다. 실제: %s", failure)
                .isTrue();

        // 이력은 성공한 한 건만 쌓여야 한다
        assertThat(eventRepository.findAllByRequestId(created.id())).hasSize(2);
    }

    private TransferCreateResponse createRequest() {
        return transferRequestService.create(
                new TransferCreateRequest(encounter.getId(), brainMri.getId(),
                        TransferPriority.URGENT, null, "휠체어 이송 필요"),
                wardNurse.getId());
    }

    private <T> List<T> runConcurrently(int threads, java.util.function.Supplier<T> action)
            throws Exception {
        List<T> results = new CopyOnWriteArrayList<>();
        List<Throwable> errors = new CopyOnWriteArrayList<>();
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            for (int i = 0; i < threads; i++) {
                pool.submit(() -> {
                    try {
                        start.await();
                        results.add(action.get());
                    } catch (Throwable t) {
                        errors.add(t);
                    } finally {
                        done.countDown();
                    }
                });
            }
            start.countDown();
            assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
        } finally {
            pool.shutdownNow();
        }
        assertThat(errors).isEmpty();
        return results;
    }
}
