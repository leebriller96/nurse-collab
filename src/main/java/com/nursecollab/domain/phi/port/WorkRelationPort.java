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
 * "이 대상에 우리 파트로 온 진행중 요청이 있는가", "침대가 찼다", 그리고 대화의
 * "메시지가 달렸다", "누가 무엇을 읽을 수 있는가" 뿐이다. 오가는 값은 가명·요청 id·
 * 메시지 열쇠·직원 id 이고, 이름이나 내용은 없다.
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
     * 침대가 찼다고 알린다. 원내에서 밖으로 나가는 쓰기 둘 중 하나다.
     * 나가는 것은 침대와 병동뿐이고, 이름도 진단명도 이 인자에 없다.
     */
    void registerEpisode(UUID subjectRef, Long departmentId, String roomNo, String bedNo,
                         OffsetDateTime admittedAt);

    /**
     * 이 요청에 메시지가 하나 달렸다고 알린다. 원내에서 밖으로 나가는 쓰기 둘 중 나머지 하나다.
     *
     * 관여하는 파트인지는 업무 쪽이 판정하고, 아니면 예외가 올라온다.
     * 원내는 그 예외로 본문 저장을 되돌린다. 내용은 인자에 없다.
     */
    void registerMessage(Long orderId, UUID messageRef, Long senderId);

    /** 이 사람이 이 요청에서 읽을 수 있는 메시지. 관여하지 않으면 예외가 올라온다. */
    Set<UUID> readableMessageRefs(Long orderId, Long readerId);
}
