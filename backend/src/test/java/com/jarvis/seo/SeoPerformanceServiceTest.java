package com.jarvis.seo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import com.jarvis.audit.AuditService;
import com.jarvis.brain.Orchestrator;
import com.jarvis.connectors.ConnectorRegistry;
import com.jarvis.notification.NotificationService;

/** The SEO loop: a safe no-op when unconfigured/disconnected, and a concrete double-down brief otherwise. */
class SeoPerformanceServiceTest {

    @Test
    void instructionContainsDataAndDoubleDownIntent() {
        String i = SeoPerformanceService.buildInstruction("site.com", "STATS", "TOPPAGES", 5);
        assertThat(i).contains("site.com").contains("STATS").contains("TOPPAGES")
                .contains("DOUBLE DOWN").contains("create_article_page");
    }

    @Test
    @SuppressWarnings("unchecked")
    void blankSiteIsANoOp() {
        JarvisSeoPerformanceProperties props = new JarvisSeoPerformanceProperties();   // site = "" by default
        ConnectorRegistry connectors = mock(ConnectorRegistry.class);
        ObjectProvider<Orchestrator> provider = mock(ObjectProvider.class);
        SeoPerformanceService svc = new SeoPerformanceService(props, connectors, provider,
                mock(NotificationService.class), mock(AuditService.class));

        String r = svc.review();
        assertThat(r).contains("No site configured");
        verify(connectors, never()).invoke(any(), any(), any());   // never touched Plausible
    }

    @Test
    @SuppressWarnings("unchecked")
    void plausibleUnavailableIsHandledGracefully() {
        JarvisSeoPerformanceProperties props = new JarvisSeoPerformanceProperties();
        props.setSite("site.com");
        ConnectorRegistry connectors = mock(ConnectorRegistry.class);
        when(connectors.invoke(any(), any(), any())).thenThrow(new RuntimeException("not connected"));
        ObjectProvider<Orchestrator> provider = mock(ObjectProvider.class);
        SeoPerformanceService svc = new SeoPerformanceService(props, connectors, provider,
                mock(NotificationService.class), mock(AuditService.class));

        String r = svc.review();
        assertThat(r).contains("Plausible isn't connected");
        verify(provider, never()).getObject();   // brain never invoked when there's no data
    }
}
