package dev.luis.asyncmotd.gui;

import dev.luis.asyncmotd.Main;
import dev.luis.asyncmotd.config.MessageManager;
import dev.luis.asyncmotd.util.Colors;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public final class PresetsGui implements Menu {

    private static final String FIELD_LINE_ONE = "line-1";
    private static final String FIELD_LINE_TWO = "line-2";
    private static final String FIELD_ICON = "icon";

    private final Main plugin;
    private final Player player;
    private final YamlConfiguration gui;
    private final Inventory inventory;
    private final List<Integer> area = new ArrayList<>();
    private final List<String> ids = new ArrayList<>();

    private int page;

    private PresetsGui(Main plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
        this.gui = plugin.presetsGui();
        this.inventory = Bukkit.createInventory(this, gui.getInt("size", 54), Colors.text(gui.getString("title", "")));
        GuiItems.parseRange(gui.getString("preset-area", ""), area);
    }

    public static void open(Main plugin, Player player) {
        player.getScheduler().run(plugin, task -> {
            PresetsGui menu = new PresetsGui(plugin, player);
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
        ids.clear();
        ids.addAll(plugin.presets().ids());

        inventory.clear();
        GuiItems.fill(inventory, gui.getConfigurationSection("filler"));

        int perPage = Math.max(1, area.size());
        int maxPage = Math.max(1, (ids.size() + perPage - 1) / perPage);
        if (page >= maxPage) {
            page = maxPage - 1;
        }

        for (int i = 0; i < area.size(); i++) {
            int index = page * perPage + i;
            if (index >= ids.size()) {
                break;
            }
            inventory.setItem(area.get(i), presetItem(ids.get(index)));
        }

        put("create");
        put("close");
        put("previous");
        put("next");

        ConfigurationSection indicator = gui.getConfigurationSection("page-indicator");
        if (indicator != null) {
            inventory.setItem(indicator.getInt("slot"), GuiItems.build(indicator,
                    "{page}", String.valueOf(page + 1),
                    "{max-page}", String.valueOf(maxPage),
                    "{amount}", String.valueOf(ids.size())));
        }
    }

    private void put(String key) {
        ConfigurationSection section = gui.getConfigurationSection(key);
        if (section == null) {
            return;
        }
        inventory.setItem(section.getInt("slot"), GuiItems.build(section));
    }

    private ItemStack presetItem(String id) {
        ConfigurationSection section = gui.getConfigurationSection("preset-item");
        if (section == null) {
            return null;
        }
        boolean active = id.equals(plugin.presets().active());
        MessageManager messages = plugin.messages();
        String empty = messages.raw("empty-value");

        Material material = GuiItems.material(section.getString(active ? "active-material" : "material", ""));
        ItemStack item = GuiItems.build(section, material, active,
                "{preset}", id,
                "{line-1}", GuiItems.orEmpty(plugin.preview(plugin.presets().value(id, FIELD_LINE_ONE)), empty),
                "{line-2}", GuiItems.orEmpty(plugin.preview(plugin.presets().value(id, FIELD_LINE_TWO)), empty),
                "{icon}", GuiItems.orEmpty(plugin.presets().value(id, FIELD_ICON), empty),
                "{status}", messages.raw(active ? "status-active" : "status-inactive"));
        return item;
    }

    @Override
    public void click(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player clicker) || !clicker.getUniqueId().equals(player.getUniqueId())) {
            return;
        }

        int slot = event.getRawSlot();
        MessageManager.playSound(clicker, gui.getString("click-sound", ""),
                (float) gui.getDouble("click-sound-volume", 1.0D),
                (float) gui.getDouble("click-sound-pitch", 1.0D));

        if (slot == slotOf("close")) {
            clicker.closeInventory();
            return;
        }
        if (slot == slotOf("create")) {
            clicker.closeInventory();
            InputRequest.start(plugin, clicker, "", plugin.getConfig().getString("input.create-field", ""));
            return;
        }
        if (slot == slotOf("previous")) {
            if (page > 0) {
                page--;
                render();
            }
            return;
        }
        if (slot == slotOf("next")) {
            page++;
            render();
            return;
        }

        int index = area.indexOf(slot);
        if (index < 0) {
            return;
        }
        int position = page * Math.max(1, area.size()) + index;
        if (position >= ids.size()) {
            return;
        }

        String id = ids.get(position);
        if (!plugin.presets().exists(id)) {
            render();
            return;
        }
        if (event.isRightClick()) {
            PresetEditGui.open(plugin, clicker, id);
            return;
        }
        plugin.presets().setActive(id);
        plugin.messages().send(clicker, "preset-activated", "{preset}", id);
        render();
    }

    private int slotOf(String key) {
        ConfigurationSection section = gui.getConfigurationSection(key);
        return section == null ? -1 : section.getInt("slot");
    }
}
