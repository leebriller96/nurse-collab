package com.nursecollab.domain.nursing.dto;

import com.nursecollab.domain.nursing.entity.NoteType;

import java.time.OffsetDateTime;

/** 내용이 비었는지는 엔티티가 판단한다. 기록 종류마다 채워야 하는 칸이 다르기 때문이다. */
public record NursingNoteRequest(
        NoteType noteType,
        String situation,
        String background,
        String assessment,
        String recommendation,
        String content,
        OffsetDateTime recordedAt
) {}
