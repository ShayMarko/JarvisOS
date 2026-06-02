package com.jarvis.brain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarvis.agent.AgentRegistry;
import com.jarvis.agent.AgentSelector;
import com.jarvis.ai.LanguageModel;
import com.jarvis.ai.ModelResponse;

class PlannerTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final AgentSelector selector = new AgentSelector(
            new AgentRegistry(), mock(LanguageModel.class), new com.jarvis.ai.JarvisAiProperties());

    private Planner planner(LanguageModel model) {
        return new Planner(selector, model, mapper, new com.jarvis.ai.JarvisAiProperties());
    }

    private LanguageModel modelReturning(String text) {
        LanguageModel m = mock(LanguageModel.class);
        // Planner calls the 3-arg generate (with a cheap planner-model override).
        when(m.generate(any(), any(), any())).thenReturn(ModelResponse.text(text, 0, 0));
        return m;
    }

    @Test
    void shortSingleIntentIsOneStepWithoutCallingModel() {
        LanguageModel model = mock(LanguageModel.class);   // must never be called
        List<PlanStep> plan = planner(model).plan("what's my cpu usage?");
        assertThat(plan).hasSize(1);
        assertThat(plan.get(0).dependsOn()).isEmpty();
        org.mockito.Mockito.verifyNoInteractions(model);
    }

    @Test
    void llmPlanBuildsDependencyDag() {
        String json = """
            Sure, here is the plan:
            {"steps":[
              {"id":"s1","task":"research the latest on X","dependsOn":[]},
              {"id":"s2","task":"draft a report from the research","dependsOn":["s1"]},
              {"id":"s3","task":"review the draft for errors","dependsOn":["s2"]}
            ]}
            """;
        List<PlanStep> plan = planner(modelReturning(json))
                .plan("Research X, then write a report and review it for accuracy and completeness please");
        assertThat(plan).hasSize(3);
        assertThat(plan.get(0).dependsOn()).isEmpty();
        assertThat(plan.get(1).dependsOn()).containsExactly("s1");
        assertThat(plan.get(2).dependsOn()).containsExactly("s2");
    }

    @Test
    void cyclesAndUnknownDepsAreStripped() {
        // s1 depends on s2 (forward edge) and a bogus id ⇒ both dropped; s2 depends on s1 ⇒ kept.
        String json = """
            {"steps":[
              {"id":"s1","task":"do the first thing","dependsOn":["s2","ghost"]},
              {"id":"s2","task":"do the second thing using the first","dependsOn":["s1"]}
            ]}
            """;
        List<PlanStep> plan = planner(mock(LanguageModel.class)).parsePlan(json);
        assertThat(plan).hasSize(2);
        assertThat(plan.get(0).dependsOn()).isEmpty();           // forward + bogus edges removed
        assertThat(plan.get(1).dependsOn()).containsExactly("s1"); // valid backward edge kept
    }

    @Test
    void fallsBackToHeuristicWhenModelGivesGarbage() {
        List<PlanStep> plan = planner(modelReturning("I cannot help with that, sorry."))
                .plan("summarize the news and then email it to the team");
        // heuristic split on "and then" ⇒ two independent steps
        assertThat(plan).hasSize(2);
        assertThat(plan).allSatisfy(s -> assertThat(s.dependsOn()).isEmpty());
    }
}
