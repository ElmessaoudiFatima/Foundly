package com.backend.dto.notification;

import java.util.List;

public record PagedNotificationDto(
        List<NotificationDto> items,
        long totalElements,
        int totalPages,
        int page,
        int size,
        long unreadCount
) {}
