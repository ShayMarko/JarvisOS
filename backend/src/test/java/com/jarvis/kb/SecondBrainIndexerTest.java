package com.jarvis.kb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.jarvis.explorer.FileSystemService;

class SecondBrainIndexerTest {

    @TempDir
    Path root;

    private KnowledgeBaseService kb;
    private FileSystemService fs;
    private SecondBrainIndexer indexer;

    @BeforeEach
    void setUp() throws Exception {
        kb = mock(KnowledgeBaseService.class);
        fs = mock(FileSystemService.class);
        when(fs.getRoot()).thenReturn(root);
        Files.createDirectories(root.resolve("Documents"));
        indexer = new SecondBrainIndexer(kb, fs, new JarvisSecondBrainProperties());
    }

    @Test
    void indexesNewTextFilesAndSkipsBinariesAndDotfiles() throws Exception {
        Files.writeString(root.resolve("Documents/notes.md"), "# meeting notes");
        Files.writeString(root.resolve("Documents/data.csv"), "a,b\n1,2");
        Files.writeString(root.resolve("Documents/photo.png"), "binary");      // not a text ext
        Files.writeString(root.resolve("Documents/.DS_Store"), "junk");        // dotfile
        when(kb.documents()).thenReturn(List.of());

        int[] r = indexer.indexOnce();

        assertThat(r[0]).isEqualTo(2);   // notes.md + data.csv
        verify(kb).indexFile("Documents/notes.md");
        verify(kb).indexFile("Documents/data.csv");
        verify(kb, never()).indexFile("Documents/photo.png");
        verify(kb, never()).indexFile("Documents/.DS_Store");
    }

    @Test
    void skipsFilesAlreadyIndexedAndUnchanged() throws Exception {
        Files.writeString(root.resolve("Documents/notes.md"), "stable");
        // KB indexed it just now — newer than the file's mtime → unchanged.
        KbDocument doc = new KbDocument("doc1", "Documents/notes.md", "notes.md", 1);
        when(kb.documents()).thenReturn(List.of(doc));

        int[] r = indexer.indexOnce();

        assertThat(r[0]).isZero();
        verify(kb, never()).indexFile(anyString());
    }

    @Test
    void reindexesFilesEditedSinceLastIndex() throws Exception {
        Path f = root.resolve("Documents/notes.md");
        Files.writeString(f, "edited");
        KbDocument doc = new KbDocument("doc1", "Documents/notes.md", "notes.md", 1);
        when(kb.documents()).thenReturn(List.of(doc));
        // The file was modified AFTER the KB indexed it.
        Files.setLastModifiedTime(f, FileTime.from(doc.getCreatedAt().plusSeconds(60)));

        int[] r = indexer.indexOnce();

        assertThat(r[0]).isEqualTo(1);
        verify(kb).indexFile("Documents/notes.md");
    }

    @Test
    void prunesDocsWhoseSourceFileVanished() {
        KbDocument gone = new KbDocument("doc1", "Documents/deleted.md", "deleted.md", 1);
        KbDocument manual = new KbDocument("doc2", "manual:pricing", "Pricing", 1);   // not under a watched folder
        when(kb.documents()).thenReturn(List.of(gone, manual));

        int[] r = indexer.indexOnce();

        assertThat(r[1]).isEqualTo(1);
        verify(kb).delete("doc1");
        verify(kb, never()).delete("doc2");   // manual entries are left alone
    }
}
