package com.jarvis.connectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarvis.error.Exceptions.NotFoundException;

class GitHubConnectorTest {

    private final GitHubConnector gh = new GitHubConnector(new ObjectMapper());

    @Test
    void exposesPrAndTriageActions() {
        List<String> ids = gh.actions().stream().map(ConnectorAction::id).toList();
        assertThat(ids).contains("list_prs", "get_pr", "comment_pr", "list_issues", "comment_issue", "label_issue");
    }

    @Test
    void commentRequiresABodyBeforeAnyNetworkCall() throws Exception {
        String r = gh.invoke("comment_pr", "{\"repo\":\"acme/app\",\"number\":7}", "tok");
        assertThat(r).contains("body");
    }

    @Test
    void labelRequiresLabelsBeforeAnyNetworkCall() throws Exception {
        String r = gh.invoke("label_issue", "{\"repo\":\"acme/app\",\"number\":7}", "tok");
        assertThat(r).contains("labels");
    }

    @Test
    void missingRepoIsRejected() {
        assertThatThrownBy(() -> gh.invoke("list_prs", "{}", "tok"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void missingNumberIsRejected() {
        assertThatThrownBy(() -> gh.invoke("comment_pr", "{\"repo\":\"acme/app\",\"body\":\"hi\"}", "tok"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void unknownActionIsRejected() {
        assertThatThrownBy(() -> gh.invoke("nope", "{}", "tok"))
                .isInstanceOf(NotFoundException.class);
    }
}
