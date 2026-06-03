package com.jarvis.proactive;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.jarvis.config.JarvisFileSystemProperties;
import com.jarvis.notification.NotificationService;
import com.jarvis.system.SystemMonitorService;

class ProactiveWatcherServiceTest {

    private Map<String, Object> diskAt(long freeOf100) {
        return Map.of("disk", Map.of("totalBytes", 100L, "freeBytes", freeOf100));
    }

    @Test
    void nudgesOncePerThresholdCrossing_notEveryTick() {
        SystemMonitorService monitor = mock(SystemMonitorService.class);
        NotificationService notifications = mock(NotificationService.class);
        when(monitor.snapshot()).thenReturn(
                diskAt(5),    // 95% → alert
                diskAt(5),    // 95% → must NOT re-alert
                diskAt(20),   // 80% (< 90-5) → re-arm
                diskAt(5));   // 95% → alert again
        ProactiveWatcherService s = new ProactiveWatcherService(
                monitor, notifications, new JarvisFileSystemProperties(), new JarvisProactiveProperties());

        s.checkDisk();
        s.checkDisk();
        s.checkDisk();
        s.checkDisk();

        verify(notifications, times(2)).notify(eq("warning"), eq("Disk almost full"), anyString(), eq("proactive"));
    }
}
