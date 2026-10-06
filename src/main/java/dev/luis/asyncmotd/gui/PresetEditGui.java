package dev.luis.asyncmotd.gui;

import dev.luis.asyncmotd.Main;
import dev.luis.asyncmotd.config.MessageManager;
import dev.luis.asyncmotd.util.Colors;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

import java.util.List;

public final class PresetEditGui implements Menu {

    private static final String KEY_ICON = "icon";
    private static final String KEY_HOVER = "hover";
    private static final String KEY_INFO = "info";
    private static final String KEY_ACTIVATE = "activate";
    private static final String KEY_DELETE = "delete";
    private static final String KEY_BACK = "back";
    private static final List<String> TEXT_KEYS = List.of("line-1", "line-2", "version");

    private final Main plugin;
    private final Player player;
    private final String preset;
    private final YamlConfiguration gui;
    private final Inventory inventory;

    private PresetEditGui(Main plugin, Player player, String preset) {
        this.plugin = plugin;
        this.player = player;
        this.preset = preset;
        this.gui = plugin.editGui();
        this.inventory = Bukkit.createInventory(this, gui.getInt("size", 27),
                Colors.text(gui.getString("title", "").replace("{preset}", preset)));
    }

    public static void open(Main plugin, Player player, String preset) {
        player.getScheduler().run(plugin, task -> {
            PresetEditGui menu = new PresetEditGui(plugin, player, preset);
            menu.render();
            player.openInventory(menu.inventory);
            MessageManager.playSound(player, menu.gui.getString("open-sound", ""),
                    (float) menu.gui.getDouble("open-sound-volume", 1.0D),
                    (float) menu.gui.getDouble("open-sound-pitch", 1.0D));
        }, null);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    private void render() {
        inventory.clear();
        GuiItems.fill(inventory, gui.getConfigurationSection("filler"));

        for (String key : TEXT_KEYS) {
            put(key);
        }
        put(KEY_ICON);
        put(KEY_HOVER);
        put(KEY_INFO);
        put(KEY_ACTIVATE);
        put(KEY_DELETE);
        put(KEY_BACK);
    }

    private void put(String key) {
        ConfigurationSection section = gui.getConfigurationSection(key);
        if (section == null) {
            return;
        }
        MessageManager messages = plugin.messages();
        String empty = messages.raw("empty-value");
        boolean active = preset.equals(plugin.presets().active());
        String field = section.getString("field", key);

        inventory.setItem(section.getInt("slot"), GuiItems.build(section,
                "{preset}", preset,
                "{value}", GuiItems.orEmpty(plugin.preview(plugin.presets().value(preset, field)), empty),
                "{icon}", GuiItems.orEmpty(plugin.presets().value(preset, KEY_ICON), empty),
                "{icon-amount}", String.valueOf(plugin.presets().iconNames().size()),
                "{hover-lines}", String.valueOf(plugin.presets().hover(preset).size()),
                "{status}", messages.raw(active ? "status-active" : "status-inactive")));
    }

    @Override
    public void click(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player clicker) || !clicker.getUniqueId().equals(player.getUniqueId())) {
            return;
        }

        MessageManager.playSound(clicker, gui.getString("click-sound", ""),
                (float) gui.getDouble("click-sound-volume", 1.0D),
                (float) gui.getDouble("click-sound-pitch", 1.0D));

        int slot = event.getRawSlot();
        if (!plugin.presets().exists(preset)) {
            plugin.messages().send(clicker, "preset-not-found", "{preset}", preset);
            PresetsGui.open(plugin, clicker);
            return;
        }

        for (String key : TEXT_KEYS) {
            if (slot == slotOf(key)) {
                clicker.closeInventory();
                InputRequest.start(plugin, clicker, preset, fieldOf(key));
                return;
            }
        }

        if (slot == slotOf(KEY_HOVER)) {
            if (event.isShiftClick()) {
                plugin.presets().clearHover(preset);
                plugin.messages().send(clicker, "hover-cleared", "{preset}", preset);
                render();
                return;
            }
            clicker.closeInventory();
            InputRequest.start(plugin, clicker, preset, fieldOf(KEY_HOVER));
            return;
        }

        if (slot == slotOf(KEY_ICON)) {
            String next = plugin.presets().cycleIcon(plugin.presets().value(preset, KEY_ICON));
            if (next == null) {
                plugin.messages().send(clicker, "no-icons");
                return;
            }
            plugin.presets().setValue(preset, fieldOf(KEY_ICON), next);
            plugin.messages().send(clicker, "value-updated",
                    "{field}", plugin.messages().label(KEY_ICON), "{preset}", preset);
            render();
            return;
        }

        if (slot == slotOf(KEY_ACTIVATE)) {
            plugin.presets().setActive(preset);
            plugin.messages().send(clicker, "preset-activated", "{preset}", preset);
            render();
            return;
        }

        if (slot == slotOf(KEY_DELETE)) {
            if (!event.isShiftClick()) {
                return;
            }
            if (preset.equals(plugin.presets().active())) {
                plugin.messages().send(clicker, "preset-active-delete");
                return;
            }
            plugin.presets().delete(preset);
            plugin.messages().send(clicker, "preset-deleted", "{preset}", preset);
            PresetsGui.open(plugin, clicker);
            return;
        }

        if (slot == slotOf(KEY_BACK)) {
            PresetsGui.open(plugin, clicker);
        }
    }

    private String fieldOf(String key) {
        ConfigurationSection section = gui.getConfigurationSection(key);
        return section == null ? key : section.getString("field", key);
    }

    private int slotOf(String key) {
        ConfigurationSection section = gui.getConfigurationSection(key);
        return section == null ? -1 : section.getInt("slot");
    }
}
