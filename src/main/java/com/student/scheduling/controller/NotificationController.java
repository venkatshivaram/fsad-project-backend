package com.student.scheduling.controller;

import com.student.scheduling.entity.Notification;
import com.student.scheduling.repository.NotificationRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
@Tag(name = "Notifications", description = "Notification endpoints for authenticated users")
@SecurityRequirement(name = "bearerAuth")
public class NotificationController {

    @Autowired
    private NotificationRepository notificationRepository;

    @GetMapping("/{userId}")
    @Operation(summary = "Get notifications", description = "Returns all notifications for the authenticated user.")
    public ResponseEntity<List<Notification>> getUserNotifications(@PathVariable Long userId) {
        return ResponseEntity.ok(notificationRepository.findByUserIdOrderByCreatedAtDesc(userId));
    }

    @GetMapping("/{userId}/unread")
    @Operation(summary = "Get unread notifications", description = "Returns unread notifications for the authenticated user.")
    public ResponseEntity<List<Notification>> getUnreadNotifications(@PathVariable Long userId) {
        return ResponseEntity.ok(notificationRepository.findByUserIdAndIsReadFalseOrderByCreatedAtDesc(userId));
    }

    @PostMapping("/{notificationId}/read")
    @Operation(summary = "Mark notification as read", description = "Marks a single notification as read.")
    public ResponseEntity<?> markAsRead(@PathVariable Long notificationId) {
        return notificationRepository.findById(notificationId).map(notification -> {
            notification.setIsRead(true);
            notificationRepository.save(notification);
            return ResponseEntity.ok().body(Map.of("message", "Marked as read"));
        }).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{userId}/read-all")
    @Operation(summary = "Mark all notifications as read", description = "Marks all unread notifications for the authenticated user as read.")
    public ResponseEntity<?> markAllAsRead(@PathVariable Long userId) {
        List<Notification> unread = notificationRepository.findByUserIdAndIsReadFalseOrderByCreatedAtDesc(userId);
        unread.forEach(n -> n.setIsRead(true));
        notificationRepository.saveAll(unread);
        return ResponseEntity.ok().body(Map.of("message", "All marked as read"));
    }
}
