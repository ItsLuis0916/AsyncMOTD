package dev.luis.asyncmotd.config;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.CachedServerIcon;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

public final class PresetStore {

    private static final String ROOT = "presets";
    private static final String ACTIVE = "active";
    private static final String FIELD_HOVER = "hover";
    private static final String REVISION = "revision";
    private static final long MAX_CLOCK_SKEW_MILLIS = 60_000L;

    private final Plugin plugin;
    private final String path;
    private final File file;
    private volatile Map<String, CachedServerIcon> icons = Map.of();
    private volatile Consumer<String> onSave = snapshot -> { };

    private YamlConfiguration data;
    private File iconFolder;
    private String iconExtension;

    public PresetStore(Plugin plugin, String path) {
        this.plugin = plugin;
        this.path = path;
        this.file = new File(plugin.getDataFolder(), path);
        reload();
    }

    public synchronized void reload() {
        if (!file.exists()) {
            plugin.saveResource(path, false);
        }
        data = YamlConfiguration.loadConfiguration(file);

        iconFolder = new File(plugin.getDataFolder(), plugin.getConfig().getString("icons.folder", ""));
        iconExtension = plugin.getConfig().getString("icons.extension", "");
        if (!iconFolder.exists()) {
            iconFolder.mkdirs();
        }
        loadIcons();
    }

    public void onSave(Consumer<String> listener) {
        onSave = listener == null ? snapshot -> { } : listener;
    }

    public synchronized int replace(String snapshot) {
        YamlConfiguration incoming = new YamlConfiguration();
        try {
            incoming.loadFromString(snapshot);
        } catch (Exception exception) {
            return 0;
        }
        if (!incoming.isConfigurationSection(ROOT)) {
            return 0;
        }
        long next = incoming.getLong(REVISION, 0L);
        long current = data.getLong(REVISION, 0L);
        if (next > System.currentTimeMillis() + MAX_CLOCK_SKEW_MILLIS) {
            return 0;
        }
        if (next < current) {
            return -1;
        }
        if (next == current && current != 0L) {
            return 0;
        }
        data = incoming;
        write(snapshot);
        return 1;
    }

    public synchronized String snapshot() {
        return data.saveToString();
    }

    public synchronized List<String> ids() {
        ConfigurationSection section = data.getConfigurationSection(ROOT);
        if (section == null) {
            return Collections.emptyList();
        }
        return new ArrayList<>(section.getKeys(false));
    }

    public synchronized boolean exists(String id) {
        return data.isConfigurationSection(ROOT + "." + id);
    }

    public synchronized String active() {
        return data.getString(ACTIVE, "");
    }

    public synchronized void setActive(String id) {
        data.set(ACTIVE, id);
        save();
    }

    public synchronized String value(String id, String field) {
        return data.getString(ROOT + "." + id + "." + field, "");
    }

    public synchronized void setValue(String id, String field, String value) {
        data.set(ROOT + "." + id + "." + field, value);
        save();
    }

    public synchronized List<String> hover(String id) {
        return data.getStringList(ROOT + "." + id + "." + FIELD_HOVER);
    }

    public synchronized void addHover(String id, String line) {
        List<String> lines = hover(id);
        lines.add(line);
        data.set(ROOT + "." + id + "." + FIELD_HOVER, lines);
        save();
    }

    public synchronized boolean removeHover(String id, int index) {
        List<String> lines = hover(id);
        if (index < 0 || index >= lines.size()) {
            return false;
        }
        lines.remove(index);
        data.set(ROOT + "." + id + "." + FIELD_HOVER, lines);
        save();
        return true;
    }

    public synchronized void clearHover(String id) {
        data.set(ROOT + "." + id + "." + FIELD_HOVER, new ArrayList<String>());
        save();
    }

    public synchronized void create(String id, List<String> fields) {
        for (String field : fields) {
            data.set(ROOT + "." + id + "." + field, "");
        }
        data.set(ROOT + "." + id + "." + FIELD_HOVER, new ArrayList<String>());
        save();
    }

    public synchronized void delete(String id) {
        data.set(ROOT + "." + id, null);
        save();
    }

    public List<String> iconNames() {
        List<String> names = new ArrayList<>(icons.keySet());
        Collections.sort(names);
        return names;
    }

    public CachedServerIcon icon(String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        return icons.get(name);
    }

    public String cycleIcon(String current) {
        List<String> names = iconNames();
        if (names.isEmpty()) {
            return null;
        }
        int index = names.indexOf(current);
        if (index + 1 >= names.size()) {
            return current == null || current.isEmpty() ? names.get(0) : "";
        }
        return names.get(index + 1);
    }

    public String hoverField() {
        return FIELD_HOVER;
    }

    private void loadIcons() {
        Bukkit.getAsyncScheduler().runNow(plugin, task -> {
            Map<String, CachedServerIcon> loaded = new HashMap<>();
            File[] files = iconFolder.listFiles();
            if (files != null) {
                for (File entry : files) {
                    if (!entry.isFile() || !entry.getName().toLowerCase(Locale.ROOT).endsWith(iconExtension.toLowerCase(Locale.ROOT))) {
                        continue;
                    }
                    try {
                        loaded.put(entry.getName(), Bukkit.loadServerIcon(entry));
                    } catch (Exception exception) {
                        plugin.getLogger().warning("Could not load server icon " + entry.getName() + ": " + exception.getMessage());
                    }
                }
            }
            icons = Map.copyOf(loaded);
        });
    }

    private void save() {
        data.set(REVISION, Math.max(System.currentTimeMillis(), data.getLong(REVISION, 0L) + 1));
        String snapshot = data.saveToString();
        write(snapshot);
        onSave.accept(snapshot);
    }

    private void write(String snapshot) {
        Path target = file.toPath();
        Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
        try {
            Files.writeString(tmp, snapshot, StandardCharsets.UTF_8);
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception exception) {
            plugin.getLogger().warning("Could not save " + path + ": " + exception.getMessage());
        }
    }
}
