package com.jarvis.api;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.jarvis.error.Exceptions.PermissionDeniedException;
import com.jarvis.error.GlobalExceptionHandler;
import com.jarvis.explorer.FileNode;
import com.jarvis.explorer.FileSystemService;

class FilesControllerTest {

    private final FileSystemService fileSystem = mock(FileSystemService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(
                        new FilesController(fileSystem, mock(com.jarvis.local.MacActions.class)))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void listsFiles() throws Exception {
        given(fileSystem.list("")).willReturn(List.of(
                new FileNode("Notes", "Notes", true, 0, Instant.EPOCH)));

        mvc.perform(get("/api/files"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Notes"))
                .andExpect(jsonPath("$[0].directory").value(true));
    }

    @Test
    void permissionErrorMapsToForbiddenEnvelope() throws Exception {
        given(fileSystem.readText("secret.txt"))
                .willThrow(new PermissionDeniedException("Safe Mode is read-only"));

        mvc.perform(get("/api/files/content").param("path", "secret.txt"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"))
                .andExpect(jsonPath("$.traceId").exists());
    }
}
