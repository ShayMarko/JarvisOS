package com.jarvis.explorer;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.jarvis.config.JarvisFileSystemProperties;
import com.jarvis.config.JarvisLimitsProperties;
import com.jarvis.config.JarvisSecurityProperties;
import com.jarvis.security.PermissionGuard;
import com.jarvis.security.PermissionMode;
import com.jarvis.undo.UndoJournal;

/** End-to-end: file mutations through FileSystemService become reversible via the UndoJournal. */
class UndoJournalTest {

    @TempDir
    Path root;

    private FileSystemService fs;
    private UndoJournal undo;

    @BeforeEach
    void setUp() {
        JarvisFileSystemProperties fsProps = new JarvisFileSystemProperties();
        fsProps.setJarvisRoot(root.toString());
        fsProps.setExplorerFolders(List.of("Notes"));
        JarvisSecurityProperties secProps = new JarvisSecurityProperties();
        secProps.setPermissionMode(PermissionMode.ASSISTED);
        PermissionGuard guard = new PermissionGuard(fsProps, secProps);
        guard.init();
        undo = new UndoJournal();
        fs = new FileSystemService(fsProps, guard, new JarvisLimitsProperties(), undo);
        fs.init();
    }

    @Test
    void undoRemovesAFileThatWasCreated() {
        fs.writeText("Notes/new.md", "hello");
        assertThat(Files.exists(root.resolve("Notes/new.md"))).isTrue();

        String result = undo.undoLast();

        assertThat(result).contains("Created Notes/new.md");
        assertThat(Files.exists(root.resolve("Notes/new.md"))).isFalse();
    }

    @Test
    void undoRestoresThePreviousContentsOfAnEditedFile() {
        fs.writeText("Notes/doc.md", "original");
        fs.writeText("Notes/doc.md", "changed");      // edit
        assertThat(fs.readText("Notes/doc.md").content()).isEqualTo("changed");

        undo.undoLast();   // reverse the edit

        assertThat(fs.readText("Notes/doc.md").content()).isEqualTo("original");
    }

    @Test
    void undoRestoresADeletedFile() {
        fs.writeText("Notes/keep.md", "precious");
        undo.undoLast();   // first undo the create... so re-create for the delete scenario
        fs.writeText("Notes/keep.md", "precious");

        fs.delete("Notes/keep.md", true);
        assertThat(Files.exists(root.resolve("Notes/keep.md"))).isFalse();

        String result = undo.undoLast();

        assertThat(result).contains("Deleted Notes/keep.md");
        assertThat(fs.readText("Notes/keep.md").content()).isEqualTo("precious");
    }

    @Test
    void nothingToUndoIsReportedClearly() {
        assertThat(new UndoJournal().undoLast()).contains("nothing to undo");
    }

    @Test
    void recentListsNewestFirst() {
        fs.writeText("Notes/a.md", "1");
        fs.writeText("Notes/b.md", "2");
        List<String> recent = undo.recent(10);
        assertThat(recent.get(0)).contains("Notes/b.md");
        assertThat(recent.get(1)).contains("Notes/a.md");
    }
}
