package com.jarvis.document;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ArticleHtmlTest {

    @Test
    void rendersSeoTitleMetaAndHeadings() {
        String html = ArticleHtml.render("Best Standing Desks 2026", "Our picks for the best standing desks.",
                "Intro paragraph.\n\n## Top picks\n- Desk A\n- Desk B\n\nClosing thoughts.",
                "See price", "https://example.com/aff");
        assertThat(html).contains("<title>Best Standing Desks 2026</title>")
                .contains("<meta name=\"description\" content=\"Our picks for the best standing desks.\">")
                .contains("<h1>Best Standing Desks 2026</h1>")
                .contains("<h2>Top picks</h2>").contains("<li>Desk A</li>")
                .contains("rel=\"sponsored nofollow\"").contains("affiliate links");
    }

    @Test
    void escapesContent() {
        assertThat(ArticleHtml.render("<x>", "d", "<b>hi</b>", "", ""))
                .doesNotContain("<b>hi</b>").contains("&lt;b&gt;hi&lt;/b&gt;");
    }
}
