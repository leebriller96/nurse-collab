package com.nursecollab.domain.episode.dto;

import java.util.List;
import java.util.UUID;

/** 이 사람이 이 요청에서 읽을 수 있는 메시지의 열쇠. 원내는 이 목록의 본문만 내준다. */
public record MessageRefsResponse(List<UUID> messageRefs) {}
