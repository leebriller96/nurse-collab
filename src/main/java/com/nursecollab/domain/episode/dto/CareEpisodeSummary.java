package com.nursecollab.domain.episode.dto;

import com.nursecollab.domain.episode.entity.CareEpisode;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 병동 보드의 카드 한 장 중 <b>업무 쪽이 아는 부분</b>.
 *
 * 침대가 찼는지, 진행 중인 요청이 몇 건인지까지가 여기서 나온다.
 * 누가 누워 있는지는 화면이 {@code /phi/subjects/brief} 로 따로 물어 채운다.
 *
 * 이름 없이도 이 목록만으로 병동이 돌아간다는 것이 중요하다.
 * 원내망 밖에서는 "302-1에 누군가 있고 요청 2건이 걸려 있다" 까지는 보인다.
 */
public record CareEpisodeSummary(
        UUID subjectRef,
        String roomNo,
        String bedNo,
        OffsetDateTime admittedAt,
        int activeRequestCount
) {
    /**
     * 침대 하나의 상세. 목록과 달리 진행중 요청을 펼쳐서 준다.
     * 여기에도 사람은 없다.
     */
    public record Detail(
            UUID subjectRef,
            String roomNo,
            String bedNo,
            OffsetDateTime admittedAt,
            List<ActiveRequest> activeRequests
    ) {}

    public record ActiveRequest(Long id, String requestNo, String itemName,
                                String status, String statusLabel,
                                OffsetDateTime scheduledAt) {}

    public static CareEpisodeSummary of(CareEpisode episode, int activeRequestCount) {
        return new CareEpisodeSummary(
                episode.getSubjectRef(),
                episode.getRoomNo(),
                episode.getBedNo(),
                episode.getAdmittedAt(),
                activeRequestCount);
    }
}
