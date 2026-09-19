package com.nursecollab.domain.notification.push;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PushSubscriptionRepository extends JpaRepository<PushSubscription, Long> {

    Optional<PushSubscription> findByEndpoint(String endpoint);

    List<PushSubscription> findAllByStaffIdIn(Collection<Long> staffIds);

    /** 본인 기기만 뺀다. 남의 기기 주소를 알아도 뺄 수 없다. */
    @Modifying
    @Transactional
    @Query("delete from PushSubscription s where s.endpoint = :endpoint and s.staffId = :staffId")
    int deleteMine(String endpoint, Long staffId);

    /** 푸시 서비스가 죽었다고 한 기기 */
    @Modifying
    @Transactional
    @Query("delete from PushSubscription s where s.endpoint = :endpoint")
    int deleteByEndpoint(String endpoint);
}
