package com.nursecollab.domain.phi.service;

import com.nursecollab.domain.phi.dto.MessageBodyCreated;
import com.nursecollab.domain.phi.dto.MessageBodyRequest;
import com.nursecollab.domain.phi.dto.MessageBodyResponse;
import com.nursecollab.domain.phi.entity.RequestMessageBody;
import com.nursecollab.domain.phi.port.WorkRelationPort;
import com.nursecollab.domain.phi.repository.RequestMessageBodyRepository;
import com.nursecollab.global.security.LoginStaff;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 요청 대화의 내용.
 *
 * <p><b>관여하는 파트인지는 여기서 판정하지 않고 업무 쪽에 묻는다.</b> 요청의 당사자를 아는 곳이
 * 거기다. 원내가 판정을 다시 구현하면 한쪽만 고쳐지는 날 대화를 읽을 수 있는 사람이 달라진다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderMessageService {

    private final RequestMessageBodyRepository bodyRepository;
    private final WorkRelationPort workRelation;

    /**
     * 본문을 저장하고 업무 쪽에 "메시지가 달렸다" 를 알린다.
     *
     * 업무 쪽이 거절하면(관여하지 않는 파트) 예외가 올라와 본문 저장도 되돌려진다.
     * 업무 서버에 닿지 못해도 마찬가지다 — 쌓아 두었다 나중에 보내지 않는다.
     * 늦게 도착한 대화는 그 사이 그 요청을 본 사람이 없는 정보로 판단한 것이 된다.
     */
    @Transactional
    public MessageBodyCreated write(Long orderId, MessageBodyRequest request, LoginStaff loginStaff) {
        RequestMessageBody body = bodyRepository.saveAndFlush(RequestMessageBody.write(
                orderId, loginStaff.staffId(), loginStaff.name(), request.content()));

        workRelation.registerMessage(orderId, body.getMessageRef(), loginStaff.staffId());

        return new MessageBodyCreated(body.getMessageRef(), body.getCreatedAt());
    }

    /**
     * 이 요청에서 이 사람이 읽을 수 있는 메시지의 내용.
     *
     * 화면이 들고 온 열쇠를 믿지 않는다. 읽을 수 있는 목록을 업무 쪽에 물어 그 본문만 돌려준다.
     * 열쇠를 손에 넣는 것만으로 열리면 안 된다.
     */
    public List<MessageBodyResponse> bodies(Long orderId, LoginStaff loginStaff) {
        Set<UUID> readable = workRelation.readableMessageRefs(orderId, loginStaff.staffId());
        if (readable.isEmpty()) return List.of();

        return bodyRepository.findAllById(readable).stream()
                // 다른 요청의 열쇠가 섞여 와도 내보내지 않는다
                .filter(body -> body.getOrderId().equals(orderId))
                .sorted(Comparator.comparing(RequestMessageBody::getCreatedAt))
                .map(MessageBodyResponse::from)
                .toList();
    }
}
