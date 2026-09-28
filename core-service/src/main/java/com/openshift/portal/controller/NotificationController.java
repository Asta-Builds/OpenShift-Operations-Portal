package com.openshift.portal.controller;

import com.openshift.portal.dto.NotificationDto;
import com.openshift.portal.notification.NotificationService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@Tag(name = "Notifications", description = "Every scheduled report email and alert the portal sent or could not send")
public class NotificationController {

    private static final int MAX_LIMIT = 200;

    private final NotificationService notifications;

    /** Newest first. */
    @GetMapping("/notifications")
    public ResponseEntity<List<NotificationDto>> recent(@RequestParam(defaultValue = "50") int limit) {
        return ResponseEntity.ok(notifications.recent(Math.max(1, Math.min(limit, MAX_LIMIT))));
    }
}
