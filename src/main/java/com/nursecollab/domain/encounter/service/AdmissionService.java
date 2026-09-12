package com.nursecollab.domain.encounter.service;

import com.nursecollab.domain.department.entity.Department;
import com.nursecollab.domain.encounter.entity.Encounter;
import com.nursecollab.domain.encounter.repository.EncounterRepository;
import com.nursecollab.domain.episode.entity.CareEpisode;
import com.nursecollab.domain.episode.repository.CareEpisodeRepository;
import com.nursecollab.domain.patient.entity.Patient;
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
 * <p>지금은 같은 DB 라 한 트랜잭션으로 묶는다. 진료 쪽을 원내 DB 로 떼어내면
 * 이 자리가 <b>원내 → 클라우드로 보내는 유일한 통로</b>가 된다.
 * 그때 나가는 것은 침대 번호와 병동뿐이고, 이름도 진단명도 이 통로를 지나지 않는다.
 * 통로가 하나여야 무엇이 밖으로 나가는지 한 곳만 보면 알 수 있다.
 */
@Service
@RequiredArgsConstructor
public class AdmissionService {

    private final EncounterRepository encounterRepository;
    private final CareEpisodeRepository careEpisodeRepository;

    @Transactional
    public Encounter admit(Patient patient, Department ward, String roomNo, String bedNo,
                           OffsetDateTime admittedAt, String diagnosis, boolean mobile) {

        Encounter encounter = encounterRepository.save(
                Encounter.admit(patient, ward, roomNo, bedNo, admittedAt, diagnosis, mobile));

        // 업무 쪽이 보는 것은 여기까지다. 진단명과 거동 여부는 넘기지 않는다.
        careEpisodeRepository.save(CareEpisode.of(
                encounter.getSubjectRef(), ward, roomNo, bedNo, admittedAt));

        return encounter;
    }
}
