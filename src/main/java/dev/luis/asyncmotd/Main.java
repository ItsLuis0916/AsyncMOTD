package dev.luis.asyncmotd;

import dev.luis.asyncmotd.command.MotdCommand;
import dev.luis.asyncmotd.config.MessageManager;
import dev.luis.asyncmotd.config.PresetStore;
import dev.luis.asyncmotd.config.ProxySync;
import dev.luis.asyncmotd.config.Sections;
import dev.luis.asyncmotd.gui.MenuListener;
import dev.luis.asyncmotd.listener.ChatInputListener;
import dev.luis.asyncmotd.listener.InputManager;
import dev.luis.asyncmotd.listener.PingListener;
import dev.luis.asyncmotd.util.Gradients;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public final class Main extends JavaPlugin {

    private static final String MESSAGES_PATH = "messages/motd.yml";
    private static final String PRESETS_PATH = "presets.yml";
    private static final String GUI_PRESETS_PATH = "gui/presets.yml";
    private static final String GUI_EDIT_PATH = "gui/preset-edit.yml";

    private MessageManager messages;
    private PresetStore presets;
    private InputManager inputs;
    private YamlConfiguration presetsGui;
    private YamlConfiguration editGui;
    private ProxySync sync;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        messages = new MessageManager(this, MESSAGES_PATH);
        presets = new PresetStore(this, PRESETS_PATH);
        inputs = new InputManager();
        presetsGui = loadFile(GUI_PRESETS_PATH);
        editGui = loadFile(GUI_EDIT_PATH);

        MotdCommand command = new MotdCommand(this);
        getCommand("motd").setExecutor(command);
        getCommand("motd").setTabCompleter(command);

        getServer().getPluginManager().registerEvents(new PingListener(this), this);
        getServer().getPluginManager().registerEvents(new ChatInputListener(this), this);
        getServer().getPluginManager().registerEvents(new MenuListener(), this);
        startSync();
    }

    @Override
    public void onDisable() {
        stopSync();
    }

    private void startSync() {
        if (!getConfig().getBoolean("proxy-sync.enabled", false)) {
            return;
        }
        String secret = getConfig().getString("proxy-sync.secret", "");
        if (secret.isEmpty()) {
            getLogger().warning("proxy-sync is enabled but proxy-sync.secret is empty, sync stays off. Set the same secret here and on the proxy.");
            return;
        }
        sync = new ProxySync(this, presets, getConfig().getString("proxy-sync.channel", "asyncmotd:sync"), secret);
        presets.onSave(sync::push);
    }

    private void stopSync() {
        if (sync == null) {
            return;
        }
        presets.onSave(null);
        sync.close();
        sync = null;
    }

    public void reloadAll() {
        reloadConfig();
        messages.reload();
        presets.reload();
        stopSync();
        startSync();
        presetsGui = loadFile(GUI_PRESETS_PATH);
        editGui = loadFile(GUI_EDIT_PATH);
    }

    public String preview(String raw) {
        return Gradients.apply(raw, Sections.of(getConfig().getConfigurationSection("gradient")));
    }

    public MessageManager messages() {
        return messages;
    }

    public PresetStore presets() {
        return presets;
    }

    public InputManager inputs() {
        return inputs;
    }

    public YamlConfiguration presetsGui() {
        return presetsGui;
    }

    public YamlConfiguration editGui() {
        return editGui;
    }

    private YamlConfiguration loadFile(String path) {
        File file = new File(getDataFolder(), path);
        if (!file.exists()) {
            saveResource(path, false);
        }
        return YamlConfiguration.loadConfiguration(file);
    }
}
