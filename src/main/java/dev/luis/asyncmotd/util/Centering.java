package dev.luis.asyncmotd.util;

import java.util.HashMap;
import java.util.Map;

public final class Centering {

    private static final char LEGACY = '&';
    private static final char SECTION = '§';
    private static final char HEX = '#';
    private static final char BOLD = 'l';
    private static final char RESET = 'r';
    private static final String COLOR_CODES = "0123456789abcdef";
    private static final String KEEP_CODES = "kmno";
    private static final int HEX_LENGTH = 6;
    private static final String SPACE = " ";

    private Centering() {
    }

    public static String center(String raw, Section section) {
        if (raw == null || raw.isEmpty() || section == null || !section.getBoolean("enabled", false)) {
            return raw;
        }

        String text = raw.strip();
        String reset = section.getString("reset-code", "");
        int spaceWidth = Math.max(1, section.getInt("space-width", 4));
        int offset = section.getInt("offset", 0);
        int pad = Math.round((section.getInt("width", 0) - pixels(text, section) + 2 * offset) / (2.0F * spaceWidth));
        if (pad <= 0) {
            return reset + text;
        }
        return reset + SPACE.repeat(pad) + text;
    }

    private static int pixels(String text, Section section) {
        Map<Integer, Integer> widths = widths(section);
        int defaultWidth = section.getInt("default-width", 6);
        int spaceWidth = section.getInt("space-width", 4);
        int boldExtra = section.getInt("bold-extra", 1);

        int total = 0;
        boolean bold = false;
        int index = 0;

        while (index < text.length()) {
            char current = text.charAt(index);

            if ((current == LEGACY || current == SECTION) && index + 1 < text.length()) {
                char code = Character.toLowerCase(text.charAt(index + 1));

                if (code == HEX && index + 1 + HEX_LENGTH < text.length()) {
                    bold = false;
                    index += 2 + HEX_LENGTH;
                    continue;
                }
                if (code == BOLD) {
                    bold = true;
                    index += 2;
                    continue;
                }
                if (code == RESET || COLOR_CODES.indexOf(code) >= 0) {
                    bold = false;
                    index += 2;
                    continue;
                }
                if (KEEP_CODES.indexOf(code) >= 0) {
                    index += 2;
                    continue;
                }
            }

            int codePoint = text.codePointAt(index);
            index += Character.charCount(codePoint);

            if (codePoint == ' ') {
                total += spaceWidth + (bold ? boldExtra : 0);
                continue;
            }

            total += widths.getOrDefault(codePoint, defaultWidth) + (bold ? boldExtra : 0);
        }

        return total;
    }

    private static Map<Integer, Integer> widths(Section section) {
        Map<Integer, Integer> widths = new HashMap<>();
        Section table = section.getSection("char-widths");
        if (table == null) {
            return widths;
        }
        for (String key : table.getKeys()) {
            int width;
            try {
                width = Integer.parseInt(key);
            } catch (NumberFormatException exception) {
                continue;
            }
            for (int codePoint : table.getString(key, "").codePoints().toArray()) {
                widths.put(codePoint, width);
            }
        }
        return widths;
    }
}
