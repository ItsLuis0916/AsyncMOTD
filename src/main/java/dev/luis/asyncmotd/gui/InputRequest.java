package dev.luis.asyncmotd.gui;

import dev.luis.asyncmotd.Main;
import org.bukkit.entity.Player;

public final class InputRequest {

    private static final String KEY_PROMPT = "input-prompt";
    private static final String KEY_PROMPT_CREATE = "input-prompt-create";

    private InputRequest() {
    }

    public static void start(Main plugin, Player player, String preset, String field) {
        plugin.inputs().request(player.getUniqueId(), preset, field,
                plugin.getConfig().getLong("input.timeout-seconds", 60L));

        plugin.messages().send(player, preset.isEmpty() ? KEY_PROMPT_CREATE : KEY_PROMPT,
                "{field}", plugin.messages().label(field),
                "{preset}", preset,
                "{cancel}", plugin.getConfig().getString("input.cancel-word", ""),
                "{clear}", plugin.getConfig().getString("input.clear-word", ""));
    }
}
