package com.jarvis.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.jarvis.audit.AuditService;
import com.jarvis.explorer.FileSystemService;

class DocumentServiceTest {

    @TempDir
    Path root;

    private DocumentService documents;

    @BeforeEach
    void setUp() {
        FileSystemService fs = mock(FileSystemService.class);
        when(fs.getRoot()).thenReturn(root);
        documents = new DocumentService(fs, mock(AuditService.class));
    }

    @Test
    void createsARealPdf() throws Exception {
        String rel = documents.createPdf("report", "Quarterly Report", "Line one.\nLine two.");
        assertThat(rel).isEqualTo("Generated/report.pdf");
        Path file = root.resolve(rel);
        assertThat(Files.exists(file)).isTrue();
        byte[] head = Files.readAllBytes(file);
        assertThat(new String(head, 0, 5)).isEqualTo("%PDF-");   // valid PDF magic header
    }

    @Test
    void createsARealDocx() throws Exception {
        String rel = documents.createDocx("notes", "Notes", "Hello world.");
        assertThat(rel).isEqualTo("Generated/notes.docx");
        Path file = root.resolve(rel);
        assertThat(Files.exists(file)).isTrue();
        byte[] head = Files.readAllBytes(file);
        assertThat(head[0]).isEqualTo((byte) 'P');               // .docx is a zip (PK..)
        assertThat(head[1]).isEqualTo((byte) 'K');
    }

    @Test
    void savesMermaidSourceEvenWithoutRenderer() {
        String result = documents.createDiagram("flow", "graph TD; A-->B");
        assertThat(result).contains("Generated/flow");
        assertThat(Files.exists(root.resolve("Generated/flow.mmd"))).isTrue();
    }
}
