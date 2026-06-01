package com.jarvis.explorer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.jarvis.config.JarvisFileSystemProperties;
import com.jarvis.config.JarvisSecurityProperties;
import com.jarvis.error.Exceptions.ConflictException;
import com.jarvis.error.Exceptions.NotFoundException;
import com.jarvis.error.Exceptions.PathBlockedException;
import com.jarvis.security.PermissionGuard;
import com.jarvis.security.PermissionMode;

class FileSystemServiceTest {

    @TempDir
    Path root;

    private FileSystemService service;

    @BeforeEach
    void setUp() {
        JarvisFileSystemProperties fsProps = new JarvisFileSystemProperties();
        fsProps.setJarvisRoot(root.toString());
        fsProps.setExplorerFolders(List.of("Notes"));
        JarvisSecurityProperties secProps = new JarvisSecurityProperties();
        secProps.setPermissionMode(PermissionMode.ASSISTED);

        PermissionGuard guard = new PermissionGuard(fsProps, secProps);
        guard.init();
        service = new FileSystemService(fsProps, guard, new com.jarvis.config.JarvisLimitsProperties());
        service.init();
    }

    @Test
    void listsExplorerFolders() {
        assertThat(service.list("")).extracting(FileNode::name).contains("Notes");
    }

    @Test
    void writesAndReadsBackText() {
        service.writeText("Notes/todo.txt", "buy milk");
        FileContent content = service.readText("Notes/todo.txt");
        assertThat(content.content()).isEqualTo("buy milk");
        assertThat(content.path()).isEqualTo("Notes/todo.txt");
    }

    @Test
    void createsDirectoryAndRejectsDuplicate() {
        service.createDirectory("Projects");
        assertThat(service.list("")).extracting(FileNode::name).contains("Projects");
        assertThatThrownBy(() -> service.createDirectory("Projects")).isInstanceOf(ConflictException.class);
    }

    @Test
    void deletesFileButRefusesNonEmptyDirectory() {
        service.writeText("Notes/a.txt", "x");
        service.delete("Notes/a.txt", true); // confirmed
        assertThatThrownBy(() -> service.readText("Notes/a.txt")).isInstanceOf(NotFoundException.class);

        service.writeText("Notes/keep.txt", "y");
        assertThatThrownBy(() -> service.delete("Notes", true)).isInstanceOf(ConflictException.class);
    }

    @Test
    void blocksTraversalOutsideRoot() {
        assertThatThrownBy(() -> service.list("../..")).isInstanceOf(PathBlockedException.class);
    }

    @Test
    void searchFindsByName() {
        service.writeText("Notes/report-2026.md", "data");
        assertThat(service.search("report", "")).extracting(FileNode::name).contains("report-2026.md");
    }
}
