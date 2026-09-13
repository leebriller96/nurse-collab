package com.nursecollab.domain.phi.port;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;

/**
 * 원내가 업무 쪽에 물어야 하는 것 전부.
 *
 * 원내 코드는 업무 쪽 저장소를 직접 주입받지 않는다. 두 서버로 갈라지면
 * 그 저장소는 다른 DB 에 있고, 직접 부르던 자리는 전부 부서진다.
 * 무엇을 묻는지를 이 인터페이스 하나에 모아 두면 갈라질 때 바꿀 곳도 하나다.
 *
 * <p>질문이 이렇게 적은 것이 중요하다. 원내가 업무 쪽에서 알아야 하는 것은
 * "이 대상에 우리 파트로 온 진행중 요청이 있는가" 와 "침대가 찼다" 뿐이다.
 * 여기에 메서드가 늘기 시작하면 경계가 새고 있다는 뜻이다.
 */
public interface WorkRelationPort {

    /** 이 대상에 이 파트로 온 진행중 요청이 있는가. 환자 정보 접근 판정의 근거다. */
    boolean hasActiveOrderTo(UUID subjectRef, Long departmentId);

    /**
     * 여러 대상 중 이 파트로 온 진행중 요청이 있는 것만.
     * 목록 한 화면을 채울 때 한 명씩 물으면 서버가 갈라진 뒤 요청이 사람 수만큼 나간다.
     */
    Set<UUID> subjectsWithActiveOrdersTo(Collection<UUID> subjectRefs, Long departmentId);

    /**
     * 침대가 찼다고 알린다. 원내에서 밖으로 나가는 유일한 쓰기다.
     * 나가는 것은 침대와 병동뿐이고, 이름도 진단명도 이 인자에 없다.
     */
    void registerEpisode(UUID subjectRef, Long departmentId, String roomNo, String bedNo,
                         OffsetDateTime admittedAt);
}
