package com.jarvis.skill;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SkillServiceTest {

    private final SkillRepository repo = mock(SkillRepository.class);
    private SkillService svc;

    @BeforeEach
    void setUp() {
        svc = new SkillService(repo);
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    private Skill skill(String name, String desc, String instr) {
        Skill s = new Skill();
        s.setId("id-" + name); s.setName(name); s.setDescription(desc); s.setInstructions(instr);
        return s;
    }

    @Test
    void learnsANewSkill() {
        when(repo.findByNameIgnoreCase("weekly-report")).thenReturn(Optional.empty());
        Skill s = svc.learn("weekly-report", "summarize the week", "1. read logs 2. write a summary");
        assertThat(s.getId()).isNotBlank();
        assertThat(s.getName()).isEqualTo("weekly-report");
        assertThat(s.getInstructions()).contains("read logs");
    }

    @Test
    void recallsTheBestMatchingSkill_andNullWhenNoneMatch() {
        when(repo.findAllByOrderByUpdatedAtDesc()).thenReturn(List.of(
                skill("weekly-report", "summarize the week", "read the logs then write a summary"),
                skill("convert-invoices", "invoices to csv", "parse invoice pdfs into csv")));
        assertThat(svc.findInstructions("make my weekly summary report")).contains("write a summary");
        assertThat(svc.findInstructions("totally unrelated quantum physics")).isNull();
    }

    @Test
    void rosterListsNameAndDescription() {
        when(repo.findAllByOrderByUpdatedAtDesc()).thenReturn(List.of(skill("x", "does x", "steps")));
        assertThat(svc.roster(10)).contains("x — does x");
    }
}
