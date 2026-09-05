package com.delvin.loan.dto.response.notification;

import com.delvin.loan.model.Notification;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationResponse {

    private Long notificationId;
    private String type;
    private String title;
    private String body;
    private String referenceId;
    private boolean read;
    private LocalDateTime createdAt;

    public static NotificationResponse from(Notification notification) {

        if (notification == null) {
            return null;
        }

        return NotificationResponse.builder()
                .notificationId(notification.getNotificationId())
                .type(notification.getType())
                .title(notification.getTitle())
                .body(notification.getBody())
                .referenceId(notification.getReferenceId())
                .read(notification.isRead())
                .createdAt(notification.getCreatedAt())
                .build();
    }
}
