package com.nursecollab.domain.notification.repository;

import com.nursecollab.domain.notification.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    @Query("""
            select n from Notification n
            where n.recipientId = :recipientId
              and (:unreadOnly = false or n.readAt is null)
            order by n.createdAt desc
            """)
    Page<Notification> findForRecipient(Long recipientId, boolean unreadOnly, Pageable pageable);

    @Query("select count(n) from Notification n where n.recipientId = :recipientId and n.readAt is null")
    long countUnread(Long recipientId);

    Optional<Notification> findByIdAndRecipientId(Long id, Long recipientId);

    @Modifying(clearAutomatically = true)
    @Query("update Notification n set n.readAt = :now where n.recipientId = :recipientId and n.readAt is null")
    int markAllRead(Long recipientId, OffsetDateTime now);

    /** 알림은 한 번에 여러 명에게 나가므로 묶어서 넣는다. */
    @Override
    <S extends Notification> List<S> saveAll(Iterable<S> entities);
}
