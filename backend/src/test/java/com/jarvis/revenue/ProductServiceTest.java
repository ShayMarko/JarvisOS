package com.jarvis.revenue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.jarvis.explorer.FileSystemService;

class ProductServiceTest {

    @Test
    void packagesFolderToZipAndLogsAsset(@TempDir Path root) throws IOException {
        Path proj = Files.createDirectories(root.resolve("Projects/demo"));
        Files.writeString(proj.resolve("README.md"), "# Demo");
        Files.createDirectories(proj.resolve("src"));
        Files.writeString(proj.resolve("src/Main.java"), "class Main {}");

        FileSystemService fs = mock(FileSystemService.class);
        when(fs.resolveExisting("Projects/demo")).thenReturn(proj);
        when(fs.getRoot()).thenReturn(root);
        RevenueService revenue = mock(RevenueService.class);

        String out = new ProductService(fs, revenue).packageProduct("Projects/demo", "Demo Kit");

        assertThat(out).contains("Demo Kit.zip").contains("2 files");
        assertThat(Files.exists(root.resolve("Products/Demo Kit.zip"))).isTrue();
        verify(revenue).logAsset(contains("Demo Kit.zip"));
    }

    @Test
    void rejectsANonFolderTarget(@TempDir Path root) throws IOException {
        Path file = Files.writeString(root.resolve("note.txt"), "hi");
        FileSystemService fs = mock(FileSystemService.class);
        when(fs.resolveExisting("note.txt")).thenReturn(file);

        String out = new ProductService(fs, mock(RevenueService.class)).packageProduct("note.txt", null);
        assertThat(out).contains("not a folder");
    }
}
