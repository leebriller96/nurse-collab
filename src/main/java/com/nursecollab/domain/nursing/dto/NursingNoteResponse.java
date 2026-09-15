package com.nursecollab.domain.nursing.dto;

import com.nursecollab.domain.nursing.entity.NoteType;
import com.nursecollab.domain.nursing.entity.NursingNote;

import java.time.Duration;
import java.time.OffsetDateTime;

public record NursingNoteResponse(
        Long id,
        NoteType noteType,
        String situation,
        String background,
        String assessment,
        String recommendation,
        String content,
        OffsetDateTime recordedAt,
        RecorderInfo recordedBy,
        OffsetDateTime createdAt,
        boolean editable
) {
    /**
     * 기록한 사람. 소속은 담지 않는다 — 업무 쪽에 있고, 기록에 필요한 것은 누가 썼는가다.
     * 이름은 쓸 때 굳혀 둔 값이다. 그때 그 사람의 이름이어야 하기 때문이다.
     */
    public record RecorderInfo(Long id, String name) {}

    /**
     * editable 을 서버가 계산해서 내려준다.
     * 24시간 규칙을 화면에서 다시 구현하면 서버와 어긋나는 순간이 온다.
     */
    public static NursingNoteResponse of(NursingNote note, Long viewerStaffId) {
        boolean mine = note.getRecordedById().equals(viewerStaffId);
        boolean inWindow = Duration.between(note.getCreatedAt(), OffsetDateTime.now())
                .compareTo(Duration.ofHours(24)) <= 0;

        return new NursingNoteResponse(
                note.getId(),
                note.getNoteType(),
                note.getSituation(),
                note.getBackground(),
                note.getAssessment(),
                note.getRecommendation(),
                note.getContent(),
                note.getRecordedAt(),
                new RecorderInfo(note.getRecordedById(), note.getRecordedByName()),
                note.getCreatedAt(),
                mine && inWindow);
    }
}
