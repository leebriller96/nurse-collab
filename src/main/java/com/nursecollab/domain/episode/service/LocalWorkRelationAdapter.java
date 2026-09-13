package com.nursecollab.domain.episode.service;

import com.nursecollab.domain.phi.port.WorkRelationPort;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;

/**
 * 원내의 질문에 같은 프로세스 안에서 답한다.
 *
 * 업무 쪽 패키지에 둔 이유: 답을 아는 것은 업무 쪽이다. 원내는 질문의 모양만 정하고
 * 누가 어떻게 답하는지는 모른다. 두 서버로 갈라지면 원내 쪽에는 이 구현 대신
 * HTTP 로 묻는 구현이 들어가고, 원내 코드는 한 줄도 바뀌지 않는다.
 *
 * 업무 서버 주소가 설정되면 뜨지 않는다. 답은 {@link WorkRelationService} 한 곳에서 나오므로
 * 이 클래스는 넘겨주기만 한다.
 */
@Component
@ConditionalOnExpression("'${app.work-api.base-url:}'.isEmpty()")
@RequiredArgsConstructor
public class LocalWorkRelationAdapter implements WorkRelationPort {

    private final WorkRelationService relations;

    @Override
    public boolean hasActiveOrderTo(UUID subjectRef, Long departmentId) {
        return relations.hasActiveOrderTo(subjectRef, departmentId);
    }

    @Override
    public Set<UUID> subjectsWithActiveOrdersTo(Collection<UUID> subjectRefs, Long departmentId) {
        return relations.subjectsWithActiveOrdersTo(subjectRefs, departmentId);
    }

    @Override
    public void registerEpisode(UUID subjectRef, Long departmentId, String roomNo, String bedNo,
                                OffsetDateTime admittedAt) {
        relations.registerEpisode(subjectRef, departmentId, roomNo, bedNo, admittedAt);
    }
}
