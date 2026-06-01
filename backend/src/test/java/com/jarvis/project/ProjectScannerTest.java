package com.jarvis.project;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProjectScannerTest {

    @TempDir
    Path root;

    private ProjectScanner scanner;

    @BeforeEach
    void setUp() throws IOException {
        Files.createDirectories(root.resolve("api-service"));
        Files.writeString(root.resolve("api-service/pom.xml"), "<project/>");
        Files.createDirectories(root.resolve("web-client"));
        Files.writeString(root.resolve("web-client/package.json"), "{}");
        Files.createDirectories(root.resolve("not-a-project"));

        JarvisProjectsProperties props = new JarvisProjectsProperties();
        props.setScanRoots(List.of(root.toString()));
        scanner = new ProjectScanner(props);
    }

    @Test
    void detectsProjectTypesByMarkerFile() {
        List<ProjectInfo> projects = scanner.scan();
        assertThat(projects).extracting(ProjectInfo::name)
                .contains("api-service", "web-client")
                .doesNotContain("not-a-project");
        assertThat(projects).filteredOn(p -> p.name().equals("api-service"))
                .singleElement().satisfies(p -> assertThat(p.type()).isEqualTo("maven"));
    }

    @Test
    void resolvesIdeForType() {
        assertThat(scanner.ideFor("maven")).isEqualTo("IntelliJ IDEA");
        assertThat(scanner.ideFor("node")).isEqualTo("Visual Studio Code");
    }

    @Test
    void findsProjectByFuzzyName() {
        assertThat(scanner.findByName("api")).isPresent()
                .get().satisfies(p -> assertThat(p.name()).isEqualTo("api-service"));
        assertThat(scanner.findByName("nonexistent")).isEmpty();
    }
}
