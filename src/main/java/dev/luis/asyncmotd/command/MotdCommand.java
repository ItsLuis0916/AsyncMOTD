package dev.luis.asyncmotd.command;

import dev.luis.asyncmotd.Main;
import dev.luis.asyncmotd.gui.PresetsGui;
import org.bukkit.command.Command;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class MotdCommand implements CommandExecutor, TabCompleter {

    private static final String PERMISSION = "asyncmotd.admin";
    private static final String FIELD_LINE_ONE = "line-1";
    private static final String FIELD_LINE_TWO = "line-2";
    private static final String FIELD_VERSION = "version";
    private static final String FIELD_ICON = "icon";
    private static final String SPACE = " ";

    private final Main plugin;

    public MotdCommand(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            plugin.messages().send(sender, "no-permission");
            return true;
        }

        if (args.length == 0) {
            if (sender instanceof Player player) {
                PresetsGui.open(plugin, player);
            } else {
                plugin.messages().send(sender, "player-only");
            }
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        if (sub.equals(sub("reload"))) {
            plugin.reloadAll();
            plugin.messages().send(sender, "config-reloaded");
            return true;
        }

        if (sub.equals(sub("list"))) {
            plugin.messages().send(sender, "list-header");
            for (String id : plugin.presets().ids()) {
                boolean active = id.equals(plugin.presets().active());
                plugin.messages().send(sender, "list-entry",
                        "{preset}", id,
                        "{status}", plugin.messages().raw(active ? "status-active" : "status-inactive"));
            }
            return true;
        }

        if (args.length < 2) {
            plugin.messages().send(sender, "usage");
            return true;
        }

        String id = args[1];

        if (sub.equals(sub("create"))) {
            if (!id.matches(plugin.getConfig().getString("input.id-pattern", ""))) {
                plugin.messages().send(sender, "invalid-id");
                return true;
            }
            if (plugin.presets().exists(id)) {
                plugin.messages().send(sender, "preset-exists", "{preset}", id);
                return true;
            }
            plugin.presets().create(id, plugin.getConfig().getStringList("preset-fields"));
            plugin.messages().send(sender, "preset-created", "{preset}", id);
            return true;
        }

        if (!plugin.presets().exists(id)) {
            plugin.messages().send(sender, "preset-not-found", "{preset}", id);
            return true;
        }

        if (sub.equals(sub("delete"))) {
            if (id.equals(plugin.presets().active())) {
                plugin.messages().send(sender, "preset-active-delete");
                return true;
            }
            plugin.presets().delete(id);
            plugin.messages().send(sender, "preset-deleted", "{preset}", id);
            return true;
        }

        if (sub.equals(sub("activate"))) {
            plugin.presets().setActive(id);
            plugin.messages().send(sender, "preset-activated", "{preset}", id);
            return true;
        }

        if (sub.equals(sub("hover"))) {
            return hover(sender, id, args);
        }

        if (args.length < 3) {
            plugin.messages().send(sender, "usage");
            return true;
        }

        String value = join(args, 2);

        if (sub.equals(sub("icon"))) {
            if (value.equalsIgnoreCase(plugin.getConfig().getString("input.clear-word", ""))) {
                return update(sender, id, FIELD_ICON, "");
            }
            if (!plugin.presets().iconNames().contains(value)) {
                plugin.messages().send(sender, "icon-not-found", "{icon}", value);
                return true;
            }
            return update(sender, id, FIELD_ICON, value);
        }

        if (sub.equals(sub("line1"))) {
            return update(sender, id, FIELD_LINE_ONE, value);
        }

        if (sub.equals(sub("line2"))) {
            return update(sender, id, FIELD_LINE_TWO, value);
        }

        if (sub.equals(sub("version"))) {
            return update(sender, id, FIELD_VERSION, value);
        }

        plugin.messages().send(sender, "usage");
        return true;
    }

    private boolean update(CommandSender sender, String id, String field, String value) {
        plugin.presets().setValue(id, field, value);
        plugin.messages().send(sender, "value-updated",
                "{field}", plugin.messages().label(field),
                "{preset}", id);
        return true;
    }

    private boolean hover(CommandSender sender, String id, String[] args) {
        if (args.length < 3) {
            plugin.messages().send(sender, "usage");
            return true;
        }

        String action = args[2].toLowerCase(Locale.ROOT);

        if (action.equals(action("clear"))) {
            plugin.presets().clearHover(id);
            plugin.messages().send(sender, "hover-cleared", "{preset}", id);
            return true;
        }

        if (args.length < 4) {
            plugin.messages().send(sender, "usage");
            return true;
        }

        if (action.equals(action("add"))) {
            plugin.presets().addHover(id, join(args, 3));
            plugin.messages().send(sender, "hover-added", "{preset}", id);
            return true;
        }

        if (action.equals(action("remove"))) {
            int index;
            try {
                index = Integer.parseInt(args[3]) - 1;
            } catch (NumberFormatException exception) {
                plugin.messages().send(sender, "hover-index-invalid");
                return true;
            }
            if (!plugin.presets().removeHover(id, index)) {
                plugin.messages().send(sender, "hover-index-invalid");
                return true;
            }
            plugin.messages().send(sender, "hover-removed",
                    "{preset}", id, "{index}", String.valueOf(index + 1));
            return true;
        }

        plugin.messages().send(sender, "usage");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            return filter(subcommands(), args[0]);
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        if (args.length == 2 && !sub.equals(sub("create")) && !sub.equals(sub("reload")) && !sub.equals(sub("list"))) {
            return filter(plugin.presets().ids(), args[1]);
        }

        if (args.length == 3 && sub.equals(sub("hover"))) {
            return filter(actions(), args[2]);
        }

        if (args.length == 3 && sub.equals(sub("icon"))) {
            return filter(plugin.presets().iconNames(), args[2]);
        }

        return Collections.emptyList();
    }

    private List<String> subcommands() {
        return values("subcommands");
    }

    private List<String> actions() {
        return values("hover-actions");
    }

    private List<String> values(String path) {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection(path);
        if (section == null) {
            return Collections.emptyList();
        }
        List<String> keys = new ArrayList<>();
        for (String key : section.getKeys(false)) {
            keys.add(section.getString(key, key));
        }
        return keys;
    }

    private List<String> filter(List<String> source, String prefix) {
        List<String> result = new ArrayList<>();
        String lower = prefix.toLowerCase(Locale.ROOT);
        for (String entry : source) {
            if (entry.toLowerCase(Locale.ROOT).startsWith(lower)) {
                result.add(entry);
            }
        }
        return result;
    }

    private String sub(String key) {
        return plugin.getConfig().getString("subcommands." + key, key).toLowerCase(Locale.ROOT);
    }

    private String action(String key) {
        return plugin.getConfig().getString("hover-actions." + key, key).toLowerCase(Locale.ROOT);
    }

    private static String join(String[] args, int from) {
        StringBuilder builder = new StringBuilder();
        for (int i = from; i < args.length; i++) {
            if (builder.length() > 0) {
                builder.append(SPACE);
            }
            builder.append(args[i]);
        }
        return builder.toString();
    }
}
