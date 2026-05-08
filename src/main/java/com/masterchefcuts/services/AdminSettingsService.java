package com.masterchefcuts.services;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Holds runtime-togglable admin settings.
 * Values default to application-property defaults and can be flipped
 * via the /api/admin/settings endpoints without a restart.
 */
@Service
public class AdminSettingsService {

    private volatile boolean adminOrderNotificationsEnabled;

    public AdminSettingsService(
            @Value("${admin.order-notifications.enabled:true}") boolean defaultEnabled) {
        this.adminOrderNotificationsEnabled = defaultEnabled;
    }

    public boolean isAdminOrderNotificationsEnabled() {
        return adminOrderNotificationsEnabled;
    }

    public boolean toggleAdminOrderNotifications() {
        adminOrderNotificationsEnabled = !adminOrderNotificationsEnabled;
        return adminOrderNotificationsEnabled;
    }

    public Map<String, Object> toMap() {
        return Map.of("adminOrderNotificationsEnabled", adminOrderNotificationsEnabled);
    }
}
