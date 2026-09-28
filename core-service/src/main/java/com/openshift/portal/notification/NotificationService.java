package com.openshift.portal.notification;

import com.openshift.portal.domain.entity.Notification;
import com.openshift.portal.domain.enums.NotificationKind;
import com.openshift.portal.domain.enums.NotificationStatus;
import com.openshift.portal.dto.NotificationDto;
import com.openshift.portal.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * The log of every email: each is recorded before it is handed on, in its own transaction, so a RabbitMQ consumer on
 * another thread finds the row to update.
 */
@Service
@RequiredArgsConstructor
public class NotificationService {

    static final int MAX_DETAIL_LENGTH = 2000;

    private final NotificationRepository repository;

    /**
     * @param dedupeKey for alerts, unique per kind and day; a second one with the same key fails with a
     *                  {@link org.springframework.dao.DataIntegrityViolationException}
     */
    @Transactional
    public Notification queued(NotificationKind kind, UUID reportId, String subject, List<String> recipients,
                               String dedupeKey) {
        return repository.save(build(kind, reportId, subject, recipients, NotificationStatus.QUEUED, null, dedupeKey));
    }

    /** Records an email that could not even be prepared, e.g. because the report failed to generate. */
    @Transactional
    public Notification failed(NotificationKind kind, UUID reportId, String subject, String recipients, String detail) {
        Notification notification = build(kind, reportId, subject, List.of(), NotificationStatus.FAILED, detail, null);
        notification.setRecipients(recipients != null ? recipients : "");
        return repository.save(notification);
    }

    @Transactional
    public void markSent(long id) {
        repository.findById(id).ifPresent(n -> {
            n.setStatus(NotificationStatus.SENT);
            n.setSentAt(LocalDateTime.now());
            n.setDetail(null);
        });
    }

    @Transactional
    public void markNotSent(long id, String reason) {
        repository.findById(id).ifPresent(n -> {
            n.setStatus(NotificationStatus.NOT_SENT);
            n.setDetail(truncate(reason));
        });
    }

    @Transactional
    public void markFailed(long id, String error) {
        repository.findById(id).ifPresent(n -> {
            n.setStatus(NotificationStatus.FAILED);
            n.setDetail(truncate(error));
        });
    }

    @Transactional(readOnly = true)
    public boolean exists(String dedupeKey) {
        return repository.existsByDedupeKey(dedupeKey);
    }

    @Transactional(readOnly = true)
    public List<NotificationDto> recent(int limit) {
        return repository.findAllByOrderByCreatedAtDescIdDesc(PageRequest.of(0, limit)).stream()
                .map(NotificationDto::of)
                .toList();
    }

    private static Notification build(NotificationKind kind, UUID reportId, String subject, List<String> recipients,
                                       NotificationStatus status, String detail, String dedupeKey) {
        return Notification.builder()
                .kind(kind)
                .reportId(reportId)
                .subject(subject.length() > 255 ? subject.substring(0, 255) : subject)
                .recipients(String.join(", ", recipients))
                .status(status)
                .detail(truncate(detail))
                .dedupeKey(dedupeKey)
                .createdAt(LocalDateTime.now())
                .build();
    }

    private static String truncate(String text) {
        return text == null || text.length() <= MAX_DETAIL_LENGTH ? text : text.substring(0, MAX_DETAIL_LENGTH);
    }
}
