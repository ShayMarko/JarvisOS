package com.jarvis.document;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class LandingPageHtmlTest {

    @Test
    void rendersHeroFeaturesAndPrice() {
        String html = LandingPageHtml.render("Spring SaaS Starter", "Ship in a weekend",
                List.of("Auth built in", "Stripe ready", "Dockerised"), "$39", "Buy now");
        assertThat(html).startsWith("<!doctype html>").contains("</html>")
                .contains("Spring SaaS Starter").contains("Ship in a weekend")
                .contains("Auth built in").contains("Stripe ready")
                .contains("$39").contains("Buy now");
    }

    @Test
    void escapesTextToPreventInjection() {
        assertThat(LandingPageHtml.render("<script>alert(1)</script>", "", List.of(), "", ""))
                .doesNotContain("<script>alert").contains("&lt;script&gt;");
    }
}
