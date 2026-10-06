package dev.luis.asyncmotd.listener;

import com.destroystokyo.paper.event.server.PaperServerListPingEvent;
import dev.luis.asyncmotd.Main;
import dev.luis.asyncmotd.config.Sections;
import dev.luis.asyncmotd.util.Centering;
import dev.luis.asyncmotd.util.Colors;
import dev.luis.asyncmotd.util.Gradients;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.util.CachedServerIcon;

import java.util.List;
import java.util.UUID;

public final class PingListener implements Listener {

    private static final String FIELD_LINE_ONE = "line-1";
    private static final String FIELD_LINE_TWO = "line-2";
    private static final String FIELD_VERSION = "version";
    private static final String FIELD_ICON = "icon";
    private static final String NEW_LINE = "\n";

    private final Main plugin;

    public PingListener(Main plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPing(PaperServerListPingEvent event) {
        FileConfiguration config = plugin.getConfig();
        if (!config.getBoolean("ping.enabled", true)) {
            return;
        }

        String preset = plugin.presets().active();
        if (preset.isEmpty() || !plugin.presets().exists(preset)) {
            return;
        }

        String online = String.valueOf(event.getNumPlayers());
        String max = String.valueOf(event.getMaxPlayers());

        if (config.getBoolean("ping.apply-motd", true)) {
            String lineOne = center(render(plugin.presets().value(preset, FIELD_LINE_ONE), online, max));
            String lineTwo = center(render(plugin.presets().value(preset, FIELD_LINE_TWO), online, max));
            event.motd(Colors.text(lineOne + NEW_LINE + lineTwo));
        }

        if (config.getBoolean("ping.apply-icon", true)) {
            CachedServerIcon icon = plugin.presets().icon(plugin.presets().value(preset, FIELD_ICON));
            if (icon != null) {
                event.setServerIcon(icon);
            }
        }

        if (config.getBoolean("ping.apply-hover", true)) {
            applyHover(event, preset, online, max);
        }

        if (config.getBoolean("ping.apply-version", true)) {
            String version = plugin.presets().value(preset, FIELD_VERSION);
            if (!version.isEmpty()) {
                event.setVersion(legacy(render(version, online, max)));
                if (config.getBoolean("version.force-display", false)) {
                    event.setProtocolVersion(config.getInt("version.protocol", -1));
                }
            }
        }
    }

    private void applyHover(PaperServerListPingEvent event, String preset, String online, String max) {
        List<String> lines = plugin.presets().hover(preset);
        if (lines.isEmpty()) {
            return;
        }

        List<PaperServerListPingEvent.ListedPlayerInfo> listed = event.getListedPlayers();
        listed.clear();
        for (String line : lines) {
            listed.add(new PaperServerListPingEvent.ListedPlayerInfo(
                    legacy(render(line, online, max)), UUID.randomUUID()));
        }
    }

    private String legacy(String raw) {
        if (!plugin.getConfig().getBoolean("ping.legacy-named-colors", true)) {
            return Colors.legacy(raw);
        }
        return Colors.legacyNamed(raw, Sections.of(plugin.getConfig().getConfigurationSection("ping.legacy-overrides")));
    }

    private String render(String raw, String online, String max) {
        return Gradients.apply(fill(raw, online, max), Sections.of(plugin.getConfig().getConfigurationSection("gradient")));
    }

    private String center(String raw) {
        return Centering.center(raw, Sections.of(plugin.getConfig().getConfigurationSection("center")));
    }

    private String fill(String raw, String online, String max) {
        FileConfiguration config = plugin.getConfig();
        return replace(replace(raw, config.getString("placeholders.online", ""), online), config.getString("placeholders.max", ""), max);
    }

    private static String replace(String text, String key, String value) {
        return key == null || key.isEmpty() ? text : text.replace(key, value);
    }
}
