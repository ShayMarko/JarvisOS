package com.jarvis.command.handlers;

import lombok.RequiredArgsConstructor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.jarvis.command.CommandCategory;
import com.jarvis.command.CommandContext;
import com.jarvis.command.CommandDefinition;
import com.jarvis.command.CommandHandler;
import com.jarvis.command.CommandResult;
import com.jarvis.plugin.PluginManager;
import com.jarvis.plugin.PluginRegistry;

/**
 * {@code /plugins} — the extension surface and marketplace (spec §5.2, §8).
 * <ul>
 *   <li>{@code /plugins} — show commands/tools/connectors/agents + installed plugins</li>
 *   <li>{@code /plugins catalog} — list installable plugins</li>
 *   <li>{@code /plugins install <id|path>} — install &amp; hot-load a plugin</li>
 *   <li>{@code /plugins uninstall <id>} — remove a plugin</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class PluginsHandler implements CommandHandler {

    private final PluginRegistry plugins;
    private final PluginManager manager;

    @Override
    public CommandDefinition definition() {
        return CommandDefinition.simple("plugins", "/plugins",
                "Show extensions; or 'catalog', 'install <id|path>', 'uninstall <id>'.", CommandCategory.SYSTEM);
    }

    @Override
    public CommandResult handle(CommandContext context) {
        List<String> args = context.args();
        String sub = args.isEmpty() ? "" : args.get(0).toLowerCase();

        return switch (sub) {
            case "catalog" -> {
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("catalog", manager.catalog());
                yield CommandResult.ok("plugins", "Plugin marketplace", data);
            }
            case "install" -> {
                if (args.size() < 2) {
                    yield CommandResult.ok("plugins", "Usage: /plugins install <catalog-id | /path/to.jar>", null);
                }
                String arg = args.get(1);
                var added = arg.endsWith(".jar") ? manager.installFromPath(arg) : manager.installFromCatalog(arg);
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("installed", added);
                yield CommandResult.ok("plugins",
                        "Installed " + added.size() + " plugin(s): " + added.stream().map(p -> p.name()).toList(), data);
            }
            case "uninstall", "remove" -> {
                if (args.size() < 2) {
                    yield CommandResult.ok("plugins", "Usage: /plugins uninstall <id>", null);
                }
                yield CommandResult.ok("plugins", manager.uninstall(args.get(1)), null);
            }
            default -> CommandResult.ok("plugins", "Plugins & Extensions", plugins.surface());
        };
    }
}
