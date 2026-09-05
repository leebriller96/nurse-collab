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
    public record RecorderInfo(Long id, String name, String departmentName) {}

    /**
     * editable 을 서버가 계산해서 내려준다.
     * 24시간 규칙을 화면에서 다시 구현하면 서버와 어긋나는 순간이 온다.
     */
    public static NursingNoteResponse of(NursingNote note, Long viewerStaffId) {
        boolean mine = note.getRecordedBy().getId().equals(viewerStaffId);
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
                new RecorderInfo(note.getRecordedBy().getId(), note.getRecordedBy().getName(),
                        note.getRecordedBy().getDepartment().getName()),
                note.getCreatedAt(),
                mine && inWindow);
    }
}
