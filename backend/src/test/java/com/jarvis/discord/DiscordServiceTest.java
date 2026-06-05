package com.jarvis.discord;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.Test;

class DiscordServiceTest {

    @Test
    void inertAndSafeWithoutAToken() {
        DiscordService d = new DiscordService(new JarvisDiscordProperties());
        assertThat(d.active()).isFalse();
        assertThat(d.pushNotifications()).isFalse();
        // no token → no network, no throw
        assertThatCode(() -> { d.push("hi"); d.send("123", "hi"); }).doesNotThrowAnyException();
    }

    @Test
    void activatesOnlyWithEnabledPlusToken_andPushNeedsAChannel() {
        JarvisDiscordProperties p = new JarvisDiscordProperties();
        p.setBotToken("tok");                       // token but not enabled
        assertThat(new DiscordService(p).active()).isFalse();

        p.setEnabled(true);                         // enabled + token → active
        assertThat(new DiscordService(p).active()).isTrue();
        assertThat(new DiscordService(p).pushNotifications()).isFalse();   // but no channel yet

        p.setChannelId("chan");
        assertThat(new DiscordService(p).pushNotifications()).isTrue();
    }
}
