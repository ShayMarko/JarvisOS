package com.jarvis.ai.tools;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

class InstallAppToolTest {

    private final InstallAppTool tool = new InstallAppTool(new ObjectMapper());

    @Test
    void declaresItselfAsAMutatingCapability() {
        assertThat(tool.spec().name()).isEqualTo("install_app");
        assertThat(tool.mutates()).isTrue();
    }

    @Test
    void rejectsMissingPackage() {
        // On macOS this hits the "no package" guard; off macOS the OS guard — both are errors and
        // crucially neither shells out.
        assertThat(tool.execute("{}")).contains("Error");
    }

    @Test
    void rejectsInjectionInPackageName() {
        String r = tool.execute("{\"package\":\"foo; rm -rf /\",\"manager\":\"brew\"}");
        assertThat(r).contains("Error");      // never runs — fails validation (or OS guard)
    }
}
