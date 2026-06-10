package com.jarvis.newsletter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import com.jarvis.audit.AuditService;
import com.jarvis.brain.Orchestrator;
import com.jarvis.notification.NotificationService;

/** The newsletter lane: a safe no-op without a topic, and a concrete brief otherwise. */
class NewsletterServiceTest {

    @Test
    void instructionNamesTheTopicAndTheLoop() {
        String i = NewsletterService.buildInstruction("indie SaaS");
        assertThat(i).contains("indie SaaS").contains("write_file").contains("resend");
    }

    @Test
    @SuppressWarnings("unchecked")
    void blankTopicIsANoOp() {
        JarvisNewsletterProperties props = new JarvisNewsletterProperties();   // topic = "" by default
        ObjectProvider<Orchestrator> provider = mock(ObjectProvider.class);
        NewsletterService svc = new NewsletterService(props, provider,
                mock(NotificationService.class), mock(AuditService.class));

        String r = svc.produceIssue();
        assertThat(r).contains("No newsletter topic configured");
        verify(provider, never()).getObject();   // brain never invoked
    }
}
