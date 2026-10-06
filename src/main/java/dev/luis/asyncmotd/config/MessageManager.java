package dev.luis.asyncmotd.config;

import dev.luis.asyncmotd.util.Colors;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class MessageManager {

    private static final Map<String, Optional<Key>> SOUND_KEYS = new ConcurrentHashMap<>();

    private final Plugin plugin;
    private final String path;
    private final File file;
    private YamlConfiguration config;

    public MessageManager(Plugin plugin, String path) {
        this.plugin = plugin;
        this.path = path;
        this.file = new File(plugin.getDataFolder(), path);
        reload();
    }

    public void reload() {
        if (!file.exists()) {
            plugin.saveResource(path, false);
        }
        config = YamlConfiguration.loadConfiguration(file);
    }

    public String raw(String path) {
        return config.getString(path, "");
    }

    public String label(String field) {
        return config.getString("field-labels." + field, field);
    }

    public String apply(String raw, String... placeholders) {
        String result = raw.replace("{prefix}", config.getString("prefix", ""));
        for (int i = 0; i + 1 < placeholders.length; i += 2) {
            result = result.replace(placeholders[i], placeholders[i + 1]);
        }
        return result;
    }

    public void send(CommandSender sender, String key, String... placeholders) {
        ConfigurationSection section = config.getConfigurationSection(key);
        if (section == null || !section.getBoolean("overall-enabled", true)) {
            return;
        }

        if (section.getBoolean("messages-enabled", false)) {
            List<String> lines = section.getStringList("messages");
            for (String line : lines) {
                sender.sendMessage(Colors.text(apply(line, placeholders)));
            }
        }

        if (!(sender instanceof Player player)) {
            return;
        }

        if (section.getBoolean("actionbar-enabled", false)) {
            player.sendActionBar(Colors.text(apply(section.getString("actionbar", ""), placeholders)));
        }

        if (section.getBoolean("title-enabled", false)) {
            Component title = Colors.text(apply(section.getString("title", ""), placeholders));
            Component subtitle = Colors.text(apply(section.getString("subtitle", ""), placeholders));
            Title.Times times = Title.Times.times(
                    ticks(section.getInt("title-fadein", 10)),
                    ticks(section.getInt("title-stay", 70)),
                    ticks(section.getInt("title-fadeout", 20)));
            player.showTitle(Title.title(title, subtitle, times));
        }

        if (section.getBoolean("sound-enabled", false)) {
            playSound(player,
                    section.getString("sound", ""),
                    (float) section.getDouble("sound-volume", 1.0D),
                    (float) section.getDouble("sound-pitch", 1.0D));
        }
    }

    public static void playSound(Player player, String name, float volume, float pitch) {
        if (name == null || name.isEmpty()) {
            return;
        }
        Optional<Key> key = SOUND_KEYS.computeIfAbsent(name.toUpperCase(Locale.ROOT), MessageManager::resolveSound);
        key.ifPresent(value -> player.playSound(Sound.sound(value, Sound.Source.MASTER, volume, pitch)));
    }

    private static Optional<Key> resolveSound(String name) {
        try {
            return Optional.of(((org.bukkit.Sound) org.bukkit.Sound.class.getField(name).get(null)).key());
        } catch (ReflectiveOperationException | ClassCastException exception) {
            return Optional.empty();
        }
    }

    private static Duration ticks(int amount) {
        return Duration.ofMillis(amount * 50L);
    }
}
