package com.nursecollab.domain.encounter.service;

import com.nursecollab.domain.encounter.entity.Encounter;
import com.nursecollab.domain.encounter.repository.EncounterRepository;
import com.nursecollab.domain.patient.entity.Patient;
import com.nursecollab.domain.phi.port.WorkRelationPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

/**
 * 입원 등록.
 *
 * 한 번의 입원이 <b>두 곳에</b> 기록된다.
 * <ul>
 *   <li>{@code encounter} — 누가 입원했고 진단명이 무엇인지. 진료정보다.</li>
 *   <li>{@code care_episode} — 어느 병동 몇 번 침대가 찼는지. 업무정보다.</li>
 * </ul>
 * 둘을 잇는 것은 {@code subject_ref} 하나뿐이고, 그 열쇠를 사람으로 되돌리는
 * 대응표는 {@code encounter} 에만 있다.
 *
 * <p>업무 쪽에는 {@link WorkRelationPort#registerEpisode} 로만 알린다.
 * 원내에서 밖으로 나가는 유일한 쓰기이고, 나가는 것은 침대와 병동뿐이다.
 *
 * <p><b>원내를 먼저 쓴다.</b> 두 서버로 갈라지면 한 트랜잭션으로 묶을 수 없어서
 * 한쪽만 성공하는 경우가 생긴다. 업무 쪽을 못 쓰면 침대가 비어 보여 요청을 못 걸 뿐이지만,
 * 반대로 하면 누구인지 모르는 침대에 요청이 걸리고 환자 기록은 없다.
 * 앞의 실패는 눈에 띄고 안전하며, 뒤의 실패는 조용하고 위험하다.
 */
@Service
@RequiredArgsConstructor
public class AdmissionService {

    private final EncounterRepository encounterRepository;
    private final WorkRelationPort workRelation;

    @Transactional
    public Encounter admit(Patient patient, Long wardId, String roomNo, String bedNo,
                           OffsetDateTime admittedAt, String diagnosis, boolean mobile) {

        Encounter encounter = encounterRepository.save(
                Encounter.admit(patient, wardId, roomNo, bedNo, admittedAt, diagnosis, mobile));

        // 진단명과 거동 여부는 넘기지 않는다
        workRelation.registerEpisode(encounter.getSubjectRef(), wardId, roomNo, bedNo, admittedAt);

        return encounter;
    }
}
