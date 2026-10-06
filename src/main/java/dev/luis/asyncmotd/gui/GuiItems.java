package dev.luis.asyncmotd.gui;

import dev.luis.asyncmotd.util.Colors;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public final class GuiItems {

    private static final String RANGE_SEPARATOR = "-";

    private GuiItems() {
    }

    public static void parseRange(String raw, List<Integer> out) {
        out.clear();
        if (raw == null || raw.isEmpty()) {
            return;
        }
        String[] parts = raw.split(RANGE_SEPARATOR);
        if (parts.length != 2) {
            return;
        }
        try {
            int from = Integer.parseInt(parts[0].trim());
            int to = Integer.parseInt(parts[1].trim());
            for (int slot = from; slot <= to; slot++) {
                out.add(slot);
            }
        } catch (NumberFormatException ignored) {
        }
    }

    public static Material material(String name) {
        if (name == null || name.isEmpty()) {
            return Material.AIR;
        }
        Material material = Material.matchMaterial(name);
        return material == null ? Material.AIR : material;
    }

    public static String orEmpty(String value, String fallback) {
        return value == null || value.isEmpty() ? fallback : value;
    }

    public static void fill(Inventory inventory, ConfigurationSection section) {
        if (section == null) {
            return;
        }
        ItemStack filler = build(section);
        for (int slot : section.getIntegerList("slots")) {
            if (slot >= 0 && slot < inventory.getSize()) {
                inventory.setItem(slot, filler);
            }
        }
    }

    public static ItemStack build(ConfigurationSection section, String... placeholders) {
        return build(section, material(section.getString("material", "")), section.getBoolean("glow", false), placeholders);
    }

    public static ItemStack build(ConfigurationSection section, Material material, boolean glow, String... placeholders) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }

        meta.displayName(Colors.item(replace(section.getString("name", ""), placeholders)));

        List<Component> lore = new ArrayList<>();
        for (String line : section.getStringList("lore")) {
            lore.add(Colors.item(replace(line, placeholders)));
        }
        meta.lore(lore);

        if (glow) {
            meta.setEnchantmentGlintOverride(true);
        }

        item.setItemMeta(meta);
        return item;
    }

    private static String replace(String raw, String... placeholders) {
        String result = raw == null ? "" : raw;
        for (int i = 0; i + 1 < placeholders.length; i += 2) {
            result = result.replace(placeholders[i], placeholders[i + 1]);
        }
        return result;
    }
}
