package com.jarvis.digest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.jarvis.discord.DiscordService;
import com.jarvis.system.SystemMonitorService;

class BriefingSchedulerTest {

    private BriefingScheduler scheduler(DiscordService discord) {
        DigestService digest = mock(DigestService.class);
        when(digest.build()).thenReturn("🗓️  Jarvis Today\n📅 Calendar\n  (no events today)");
        SystemMonitorService monitor = mock(SystemMonitorService.class);
        when(monitor.snapshot()).thenReturn(Map.of(
                "cpu", Map.of("systemCpuLoad", 0.25),
                "memory", Map.of("usedPhysicalBytes", 8_000_000_000L, "totalPhysicalBytes", 16_000_000_000L),
                "disk", Map.of("totalBytes", 500_000_000_000L, "freeBytes", 100_000_000_000L)));
        return new BriefingScheduler(new JarvisBriefingProperties(), digest, discord, monitor);
    }

    @Test
    void composesTheBriefingWithDigestAndHealth() {
        String text = scheduler(mock(DiscordService.class)).compose();
        assertThat(text).contains("Morning briefing").contains("Jarvis Today").contains("System: CPU 25%");
        assertThat(text).contains("disk 80% used");
    }

    @Test
    void dailyPushesToDiscordWhenEnabled() {
        DiscordService discord = mock(DiscordService.class);
        BriefingScheduler s = scheduler(discord);
        s.daily();
        ArgumentCaptor<String> sent = ArgumentCaptor.forClass(String.class);
        verify(discord).push(sent.capture());
        assertThat(sent.getValue()).contains("Morning briefing");
    }

    @Test
    void disabledDoesNotPush() {
        DiscordService discord = mock(DiscordService.class);
        JarvisBriefingProperties off = new JarvisBriefingProperties();
        off.setEnabled(false);
        DigestService digest = mock(DigestService.class);
        new BriefingScheduler(off, digest, discord, mock(SystemMonitorService.class)).daily();
        verify(discord, org.mockito.Mockito.never()).push(anyString());
    }
}
