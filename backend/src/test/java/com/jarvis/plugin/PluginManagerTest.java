package com.jarvis.plugin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarvis.agent.AgentRegistry;
import com.jarvis.ai.tools.Tool;
import com.jarvis.ai.tools.ToolRegistry;
import com.jarvis.audit.AuditService;
import com.jarvis.explorer.FileSystemService;
import com.jarvis.plugin.PluginManager.PluginInfo;

/**
 * Loads a REAL external plugin JAR (built outside Jarvis, in test resources) and
 * verifies dynamic loading end-to-end: the tool is registered, executable, granted
 * to the General agent, and fully removed on uninstall.
 */
class PluginManagerTest {

    private ToolRegistry tools;
    private AgentRegistry agents;

    private PluginManager managerFor(Path dir) {
        JarvisPluginProperties props = new JarvisPluginProperties();
        props.setEnabled(true);
        props.setDir(dir.toString());
        tools = new ToolRegistry(List.of());
        agents = new AgentRegistry();
        return new PluginManager(props, mock(FileSystemService.class), tools, agents,
                mock(AuditService.class), new ObjectMapper());
    }

    private void copyJar(Path dir) throws Exception {
        Files.createDirectories(dir);
        try (var in = getClass().getResourceAsStream("/plugins/hello-plugin.jar")) {
            assertThat(in).as("test plugin jar present").isNotNull();
            Files.copy(in, dir.resolve("hello-plugin.jar"));
        }
    }

    @Test
    void loadsExternalJarRegistersAndExecutesTool(@TempDir Path dir) throws Exception {
        copyJar(dir);
        PluginManager mgr = managerFor(dir);
        mgr.init();

        List<PluginInfo> installed = mgr.installed();
        assertThat(installed).hasSize(1);
        assertThat(installed.get(0).id()).isEqualTo("com.example.hello");
        assertThat(installed.get(0).tools()).containsExactly("greet");

        // The contributed tool is live in the registry and actually runs.
        Tool greet = tools.find("greet").orElseThrow();
        assertThat(greet.execute("{\"name\":\"Shay\"}"))
                .isEqualTo("Hello, Shay! - from the Jarvis sample plugin.");

        // ...and it was granted to the General agent so routing can reach it.
        assertThat(agents.general().toolNames()).contains("greet");
    }

    @Test
    void uninstallRemovesToolAndGrant(@TempDir Path dir) throws Exception {
        copyJar(dir);
        PluginManager mgr = managerFor(dir);
        mgr.init();
        assertThat(tools.has("greet")).isTrue();

        String msg = mgr.uninstall("com.example.hello");
        assertThat(msg).contains("Uninstalled");
        assertThat(tools.has("greet")).isFalse();
        assertThat(mgr.installed()).isEmpty();
        assertThat(agents.general().toolNames()).doesNotContain("greet");
    }
}
