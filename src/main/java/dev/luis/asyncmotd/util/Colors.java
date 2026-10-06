package dev.luis.asyncmotd.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class Colors {

    private static final char HASH = '#';
    private static final int RADIX = 16;
    private static final int CHANNEL = 255;
    private static final int HALF = 256;

    private static final LegacyComponentSerializer AMPERSAND = LegacyComponentSerializer.builder()
            .character('&')
            .hexColors()
            .useUnusualXRepeatedCharacterHexFormat()
            .build();

    private static final LegacyComponentSerializer SECTION = LegacyComponentSerializer.builder()
            .character('§')
            .hexColors()
            .useUnusualXRepeatedCharacterHexFormat()
            .build();

    private static final LegacyComponentSerializer SECTION_NAMED = LegacyComponentSerializer.builder()
            .character('§')
            .build();

    private Colors() {
    }

    public static Component text(String raw) {
        return AMPERSAND.deserialize(raw == null ? "" : raw);
    }

    public static Component item(String raw) {
        return text(raw).decoration(TextDecoration.ITALIC, false);
    }

    public static String legacy(String raw) {
        return SECTION.serialize(text(raw));
    }

    public static String legacyNamed(String raw, Section overrides) {
        return SECTION_NAMED.serialize(downsample(text(raw), map(overrides)));
    }

    private static Map<Integer, NamedTextColor> map(Section section) {
        Map<Integer, NamedTextColor> result = new HashMap<>();
        if (section == null) {
            return result;
        }
        for (String key : section.getKeys()) {
            NamedTextColor named = NamedTextColor.NAMES.value(section.getString(key, "").toLowerCase(Locale.ROOT));
            if (named == null) {
                continue;
            }
            String hex = key.startsWith(String.valueOf(HASH)) ? key.substring(1) : key;
            try {
                result.put(Integer.parseInt(hex, RADIX), named);
            } catch (NumberFormatException ignored) {
            }
        }
        return result;
    }

    private static Component downsample(Component component, Map<Integer, NamedTextColor> overrides) {
        Component result = component;
        TextColor color = component.color();
        if (color != null) {
            result = result.color(nearest(color, overrides));
        }
        List<Component> children = new ArrayList<>();
        for (Component child : component.children()) {
            children.add(downsample(child, overrides));
        }
        return result.children(children);
    }

    private static NamedTextColor nearest(TextColor color, Map<Integer, NamedTextColor> overrides) {
        NamedTextColor override = overrides.get(color.value());
        if (override != null) {
            return override;
        }

        NamedTextColor best = NamedTextColor.WHITE;
        double bestDistance = Double.MAX_VALUE;
        for (NamedTextColor candidate : NamedTextColor.NAMES.values()) {
            double distance = distance(color, candidate);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = candidate;
            }
        }
        return best;
    }

    private static double distance(TextColor from, TextColor to) {
        int mean = (from.red() + to.red()) / 2;
        int dr = from.red() - to.red();
        int dg = from.green() - to.green();
        int db = from.blue() - to.blue();
        return (2.0D + mean / (double) HALF) * dr * dr
                + 4.0D * dg * dg
                + (2.0D + (CHANNEL - mean) / (double) HALF) * db * db;
    }
}
