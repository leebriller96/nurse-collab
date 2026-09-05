package com.nursecollab.domain.encounter.dto;

/**
 * 재원 상세 응답.
 *
 * 호출자가 어느 파트냐에 따라 아예 다른 모양이 나간다.
 * 마스킹이 아니라 미포함이다. 검사실 응답에는 진단명이 필드째로 없다.
 */
public sealed interface EncounterView permits EncounterFullView, EncounterExamView {
}
