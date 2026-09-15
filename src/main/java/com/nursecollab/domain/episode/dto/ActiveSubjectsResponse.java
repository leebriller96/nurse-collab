package com.nursecollab.domain.episode.dto;

import java.util.List;
import java.util.UUID;

/** 물어본 가명 중 관계가 있는 것만. 무엇이 걸려 있는지는 싣지 않는다. */
public record ActiveSubjectsResponse(List<UUID> subjectRefs) {}
