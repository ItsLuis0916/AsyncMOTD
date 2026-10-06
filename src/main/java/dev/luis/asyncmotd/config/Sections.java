package dev.luis.asyncmotd.config;

import dev.luis.asyncmotd.util.Section;
import org.bukkit.configuration.ConfigurationSection;

import java.util.LinkedHashMap;
import java.util.Map;

public final class Sections {

    private Sections() {
    }

    public static Section of(ConfigurationSection section) {
        return new Section(section == null ? Map.of() : map(section));
    }

    private static Map<String, Object> map(ConfigurationSection section) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            Object value = section.get(key);
            out.put(key, value instanceof ConfigurationSection child ? map(child) : value);
        }
        return out;
    }
}
