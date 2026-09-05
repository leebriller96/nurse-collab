package com.nursecollab.infra.realtime;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RealtimeNotifier {

    private static final String DEPARTMENT_TOPIC = "/topic/department/";

    private final SimpMessagingTemplate messagingTemplate;

    /**
     * 요청 파트와 수행 파트 양쪽 채널로 동시에 쏜다.
     * 한쪽만 보내면 상대 화면이 갱신되지 않아 서로 다른 상태를 보게 된다.
     */
    public void broadcast(Long fromDepartmentId, Long toDepartmentId, RealtimeEvent event) {
        send(fromDepartmentId, event);
        if (!fromDepartmentId.equals(toDepartmentId)) {
            send(toDepartmentId, event);
        }
    }

    private void send(Long departmentId, RealtimeEvent event) {
        try {
            messagingTemplate.convertAndSend(DEPARTMENT_TOPIC + departmentId, event);
        } catch (RuntimeException e) {
            // 알림 실패가 이미 커밋된 업무 처리를 되돌릴 수는 없다. 남기고 넘어간다.
            log.warn("실시간 알림 발송 실패. departmentId={}, requestId={}",
                    departmentId, event.requestId(), e);
        }
    }
}
