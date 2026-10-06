package dev.luis.asyncmotd.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Gradients {

    private static final char LEGACY = '&';
    private static final char HASH = '#';
    private static final int RADIX = 16;
    private static final int CHANNEL = 255;
    private static final String HEX_FORMAT = "%02x";

    private Gradients() {
    }

    public static String apply(String raw, Section section) {
        if (raw == null || raw.isEmpty() || section == null || !section.getBoolean("enabled", false)) {
            return raw;
        }

        Pattern pattern;
        try {
            pattern = Pattern.compile(section.getString("regex", ""));
        } catch (Exception exception) {
            return raw;
        }

        String colorFormat = section.getString("color-format", "");
        String keepCodes = section.getString("keep-codes", "");
        String separator = section.getString("color-separator", "");

        Matcher matcher = pattern.matcher(raw);
        if (matcher.groupCount() < 2) {
            return raw;
        }
        StringBuilder result = new StringBuilder();
        int last = 0;

        while (matcher.find()) {
            result.append(raw, last, matcher.start());
            result.append(expand(matcher.group(2), stops(matcher.group(1), separator), colorFormat, keepCodes));
            last = matcher.end();
        }
        result.append(raw.substring(last));
        return result.toString();
    }

    private static List<Integer> stops(String raw, String separator) {
        List<Integer> stops = new ArrayList<>();
        for (String entry : raw.split(Pattern.quote(separator))) {
            String hex = entry.startsWith(String.valueOf(HASH)) ? entry.substring(1) : entry;
            if (hex.isEmpty()) {
                continue;
            }
            try {
                stops.add(Integer.parseInt(hex, RADIX));
            } catch (NumberFormatException ignored) {
            }
        }
        return stops;
    }

    private static String expand(String text, List<Integer> stops, String colorFormat, String keepCodes) {
        if (stops.isEmpty()) {
            return text;
        }

        List<Integer> points = new ArrayList<>();
        List<String> formats = new ArrayList<>();
        StringBuilder active = new StringBuilder();

        int index = 0;
        while (index < text.length()) {
            char current = text.charAt(index);
            if (current == LEGACY && index + 1 < text.length()) {
                char code = Character.toLowerCase(text.charAt(index + 1));
                if (keepCodes.indexOf(code) >= 0) {
                    active.append(LEGACY).append(code);
                } else {
                    active.setLength(0);
                }
                index += 2;
                continue;
            }
            int codePoint = text.codePointAt(index);
            index += Character.charCount(codePoint);
            points.add(codePoint);
            formats.add(active.toString());
        }

        StringBuilder out = new StringBuilder();
        for (int i = 0; i < points.size(); i++) {
            float ratio = points.size() <= 1 ? 0.0F : (float) i / (points.size() - 1);
            out.append(String.format(Locale.ROOT, colorFormat, sample(stops, ratio)));
            out.append(formats.get(i));
            out.appendCodePoint(points.get(i));
        }
        return out.toString();
    }

    private static String sample(List<Integer> stops, float ratio) {
        if (stops.size() == 1) {
            return hex(stops.get(0), stops.get(0), 0.0F);
        }
        float scaled = ratio * (stops.size() - 1);
        int index = Math.min((int) scaled, stops.size() - 2);
        return hex(stops.get(index), stops.get(index + 1), scaled - index);
    }

    private static String hex(int from, int to, float ratio) {
        StringBuilder hex = new StringBuilder();
        for (int shift = 16; shift >= 0; shift -= 8) {
            int a = (from >> shift) & CHANNEL;
            int b = (to >> shift) & CHANNEL;
            hex.append(String.format(Locale.ROOT, HEX_FORMAT, Math.round(a + (b - a) * ratio)));
        }
        return hex.toString();
    }
}
