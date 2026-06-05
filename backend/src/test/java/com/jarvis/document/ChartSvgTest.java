package com.jarvis.document;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class ChartSvgTest {

    @Test
    void barChartEmitsRects() {
        String svg = ChartSvg.render("bar", "My Chart", List.of("A", "B"), List.of(3.0, 5.0));
        assertThat(svg).startsWith("<svg").endsWith("</svg>")
                .contains("My Chart").contains("<rect").contains("A").contains("B");
    }

    @Test
    void lineChartEmitsPolyline() {
        assertThat(ChartSvg.render("line", "Trend", List.of("Mon", "Tue"), List.of(1.0, 4.0)))
                .contains("<polyline");
    }

    @Test
    void pieChartEmitsPaths() {
        assertThat(ChartSvg.render("pie", "Split", List.of("X", "Y"), List.of(2.0, 2.0)))
                .contains("<path");
    }

    @Test
    void emptyDataIsHandledGracefully() {
        assertThat(ChartSvg.render("bar", "Empty", List.of(), List.of())).contains("No data");
    }

    @Test
    void escapesTitleToPreventSvgInjection() {
        assertThat(ChartSvg.render("bar", "<script>", List.of("A"), List.of(1.0)))
                .doesNotContain("<script>").contains("&lt;script&gt;");
    }
}
