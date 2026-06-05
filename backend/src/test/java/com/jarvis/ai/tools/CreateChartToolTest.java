package com.jarvis.ai.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarvis.document.DocumentService;

class CreateChartToolTest {

    private final DocumentService documents = mock(DocumentService.class);
    private final CreateChartTool tool = new CreateChartTool(documents, new ObjectMapper());

    @Test
    @SuppressWarnings("unchecked")
    void parsesDataArrayAndBuildsChart() {
        when(documents.createChart(any(), any(), any(), any(), any())).thenReturn("Generated/chart.svg");

        String out = tool.execute("{\"type\":\"bar\",\"title\":\"Sales\","
                + "\"data\":[{\"label\":\"A\",\"value\":3},{\"label\":\"B\",\"value\":5}]}");

        assertThat(out).contains("Generated/chart.svg").contains("bar").contains("2 data points");
        ArgumentCaptor<List<String>> labels = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<List<Double>> values = ArgumentCaptor.forClass(List.class);
        verify(documents).createChart(any(), eq("bar"), eq("Sales"), labels.capture(), values.capture());
        assertThat(labels.getValue()).containsExactly("A", "B");
        assertThat(values.getValue()).containsExactly(3.0, 5.0);
    }

    @Test
    void acceptsParallelLabelsAndValuesArrays() {
        when(documents.createChart(any(), any(), any(), any(), any())).thenReturn("Generated/c.svg");
        assertThat(tool.execute("{\"type\":\"pie\",\"labels\":[\"X\",\"Y\"],\"values\":[1,2]}"))
                .contains("pie").contains("2 data points");
    }

    @Test
    void complainsWhenNoData() {
        assertThat(tool.execute("{\"type\":\"bar\"}")).contains("Provide chart data");
    }

    @Test
    void mutatesIsTrue() {
        assertThat(tool.mutates()).isTrue();
    }
}
