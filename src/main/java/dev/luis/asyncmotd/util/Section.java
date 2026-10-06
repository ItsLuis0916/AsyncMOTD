package dev.luis.asyncmotd.util;

import java.util.List;
import java.util.Map;
import java.util.Set;

public final class Section {

    private final Map<?, ?> values;

    public Section(Map<?, ?> values) {
        this.values = values == null ? Map.of() : values;
    }

    public Set<String> getKeys() {
        return values.keySet().stream().map(String::valueOf).collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
    }

    public Section getSection(String key) {
        return get(key) instanceof Map<?, ?> map ? new Section(map) : null;
    }

    public String getString(String key, String fallback) {
        Object value = get(key);
        return value == null ? fallback : String.valueOf(value);
    }

    public int getInt(String key, int fallback) {
        return get(key) instanceof Number number ? number.intValue() : fallback;
    }

    public long getLong(String key, long fallback) {
        return get(key) instanceof Number number ? number.longValue() : fallback;
    }

    public boolean getBoolean(String key, boolean fallback) {
        return get(key) instanceof Boolean flag ? flag : fallback;
    }

    public List<String> getStringList(String key) {
        return get(key) instanceof List<?> list ? list.stream().map(String::valueOf).toList() : List.of();
    }

    private Object get(String key) {
        Object direct = values.get(key);
        if (direct != null) {
            return direct;
        }
        for (Map.Entry<?, ?> entry : values.entrySet()) {
            if (String.valueOf(entry.getKey()).equals(key)) {
                return entry.getValue();
            }
        }
        int dot = key.indexOf('.');
        if (dot > 0 && get(key.substring(0, dot)) instanceof Map<?, ?> child) {
            return new Section(child).get(key.substring(dot + 1));
        }
        return null;
    }
}
