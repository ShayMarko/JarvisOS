package com.jarvis.watchdog;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.jarvis.ai.JarvisAiProperties;
import com.jarvis.notification.NotificationService;
import com.jarvis.system.SystemMonitorService;

class WatchdogServiceTest {

    private NotificationService notifications;
    private WatchdogService watchdog;

    @BeforeEach
    void setUp() {
        notifications = mock(NotificationService.class);
        watchdog = new WatchdogService(new JarvisWatchdogProperties(), new JarvisAiProperties(),
                mock(SystemMonitorService.class), notifications);
    }

    @Test
    void alertsOnceWhenOllamaGoesDownThenAnnouncesRecovery() {
        watchdog.evaluateOllama(false);   // down → alert
        watchdog.evaluateOllama(false);   // still down → no repeat
        verify(notifications, times(1)).notify(eq("critical"), eq("Ollama is down"), anyString(), eq("watchdog"));

        watchdog.evaluateOllama(true);    // back → recovery
        verify(notifications).notify(eq("info"), eq("Ollama recovered"), anyString(), eq("watchdog"));
    }

    @Test
    void noAlertWhileOllamaStaysUp() {
        watchdog.evaluateOllama(true);
        verify(notifications, never()).notify(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void diskAlertFiresOnceAtCriticalAndReArmsAfterRecovery() {
        watchdog.evaluateDisk(96);   // ≥95 → critical alert
        watchdog.evaluateDisk(97);   // still critical → no repeat
        verify(notifications, times(1)).notify(eq("critical"), eq("Disk critically full"), anyString(), eq("watchdog"));

        watchdog.evaluateDisk(80);   // recovered (< 95-3) → re-arm + recovery note
        verify(notifications).notify(eq("info"), eq("Disk recovered"), anyString(), eq("watchdog"));

        watchdog.evaluateDisk(96);   // critical again → alerts again
        verify(notifications, times(2)).notify(eq("critical"), eq("Disk critically full"), anyString(), eq("watchdog"));
    }
}
