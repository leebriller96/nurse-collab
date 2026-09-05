package com.nursecollab.domain.nursing.entity;

import com.nursecollab.domain.encounter.entity.Encounter;
import com.nursecollab.domain.staff.entity.Staff;
import com.nursecollab.global.error.BusinessException;
import com.nursecollab.global.error.ErrorCode;
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

import java.time.Duration;
import java.time.OffsetDateTime;

/**
 * 간호기록.
 *
 * 의료 기록은 법적 증거물이라 지우지 않는다. 삭제 메서드도 두지 않는다.
 * 수정은 본인이 24시간 안에 하는 것만 허용하고, 고치기 전 내용은 감사 로그에 남긴다.
 */
@Entity
@Table(name = "nursing_note")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NursingNote {

    private static final Duration EDIT_WINDOW = Duration.ofHours(24);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "encounter_id")
    private Encounter encounter;

    @Enumerated(EnumType.STRING)
    @Column(name = "note_type", nullable = false, length = 20)
    private NoteType noteType = NoteType.GENERAL;

    @Column(columnDefinition = "text")
    private String situation;

    @Column(columnDefinition = "text")
    private String background;

    @Column(columnDefinition = "text")
    private String assessment;

    @Column(columnDefinition = "text")
    private String recommendation;

    /** 일반 기록일 때 쓴다 */
    @Column(columnDefinition = "text")
    private String content;

    @Column(name = "recorded_at", nullable = false)
    private OffsetDateTime recordedAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recorded_by")
    private Staff recordedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    public static NursingNote write(Encounter encounter, NoteType noteType,
                                    String situation, String background,
                                    String assessment, String recommendation,
                                    String content, OffsetDateTime recordedAt, Staff recordedBy) {
        NursingNote note = new NursingNote();
        note.encounter = encounter;
        note.noteType = (noteType == null) ? NoteType.GENERAL : noteType;
        note.situation = situation;
        note.background = background;
        note.assessment = assessment;
        note.recommendation = recommendation;
        note.content = content;
        note.recordedAt = (recordedAt == null) ? OffsetDateTime.now() : recordedAt;
        note.recordedBy = recordedBy;
        note.createdAt = OffsetDateTime.now();
        note.validate();
        return note;
    }

    /**
     * 수정은 본인이 24시간 안에 하는 것만 허용한다.
     * 시간이 지난 기록은 고치는 대신 새 기록을 추가해 정정한다.
     */
    public void edit(Staff editor, String situation, String background,
                     String assessment, String recommendation, String content) {

        if (!recordedBy.getId().equals(editor.getId())) {
            throw new BusinessException(ErrorCode.NOTE_NOT_EDITABLE);
        }
        if (Duration.between(createdAt, OffsetDateTime.now()).compareTo(EDIT_WINDOW) > 0) {
            throw new BusinessException(ErrorCode.NOTE_EDIT_WINDOW_CLOSED);
        }

        this.situation = situation;
        this.background = background;
        this.assessment = assessment;
        this.recommendation = recommendation;
        this.content = content;
        validate();
    }

    /** 인수인계 기록인데 상황 칸이 비어 있으면 받는 사람이 읽을 것이 없다. */
    private void validate() {
        boolean sbarEmpty = isBlank(situation) && isBlank(background)
                && isBlank(assessment) && isBlank(recommendation);

        if (noteType == NoteType.GENERAL && isBlank(content)) {
            throw new BusinessException(ErrorCode.NOTE_CONTENT_REQUIRED);
        }
        if (noteType != NoteType.GENERAL && sbarEmpty) {
            throw new BusinessException(ErrorCode.NOTE_CONTENT_REQUIRED);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
