package com.jarvis.telegram;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

class TelegramServiceTest {

    private TelegramService svc(JarvisTelegramProperties p) {
        return new TelegramService(p, new ObjectMapper());
    }

    @Test
    void inertWithoutAToken() {
        JarvisTelegramProperties p = new JarvisTelegramProperties();
        p.setEnabled(true);                 // enabled but no token → still inert
        TelegramService s = svc(p);
        assertThat(s.active()).isFalse();
        assertThat(s.pollingEnabled()).isFalse();
        assertThat(s.poll(0)).isEmpty();    // never touches the network
        assertThatCode(() -> { s.send("123", "hi"); s.push("hi"); }).doesNotThrowAnyException();
    }

    @Test
    void activeOnlyWhenEnabledAndTokenPresent() {
        JarvisTelegramProperties p = new JarvisTelegramProperties();
        p.setBotToken("123:abc");
        assertThat(svc(p).active()).isFalse();   // token but not enabled
        p.setEnabled(true);
        assertThat(svc(p).active()).isTrue();
    }
}
