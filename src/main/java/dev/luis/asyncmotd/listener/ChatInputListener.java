package dev.luis.asyncmotd.listener;

import dev.luis.asyncmotd.Main;
import dev.luis.asyncmotd.gui.PresetEditGui;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public final class ChatInputListener implements Listener {

    private final Main plugin;

    public ChatInputListener(Main plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        InputManager.Session session = plugin.inputs().consume(player.getUniqueId());
        if (session == null) {
            return;
        }
        event.setCancelled(true);
        if (System.currentTimeMillis() > session.expiresAt()) {
            plugin.messages().send(player, "input-expired");
            return;
        }
        String raw = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
        player.getScheduler().run(plugin, task -> handle(player, session, raw), null);
    }

    private void handle(Player player, InputManager.Session session, String raw) {

        String cancelWord = plugin.getConfig().getString("input.cancel-word", "");
        String clearWord = plugin.getConfig().getString("input.clear-word", "");

        if (raw.equalsIgnoreCase(cancelWord)) {
            plugin.messages().send(player, "input-cancelled");
            reopen(player, session.preset());
            return;
        }

        String value = raw.equalsIgnoreCase(clearWord) ? "" : raw;
        String createField = plugin.getConfig().getString("input.create-field", "");

        if (session.field().equals(createField)) {
            handleCreate(player, value);
            return;
        }

        if (!plugin.presets().exists(session.preset())) {
            plugin.messages().send(player, "preset-not-found", "{preset}", session.preset());
            return;
        }

        if (session.field().equals(plugin.presets().hoverField())) {
            if (value.isEmpty()) {
                plugin.presets().clearHover(session.preset());
                plugin.messages().send(player, "hover-cleared", "{preset}", session.preset());
            } else {
                plugin.presets().addHover(session.preset(), value);
                plugin.messages().send(player, "hover-added", "{preset}", session.preset());
            }
        } else {
            plugin.presets().setValue(session.preset(), session.field(), value);
            plugin.messages().send(player, "value-updated",
                    "{field}", plugin.messages().label(session.field()),
                    "{preset}", session.preset());
        }

        reopen(player, session.preset());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.inputs().clear(event.getPlayer().getUniqueId());
    }

    private void handleCreate(Player player, String id) {
        if (!id.matches(plugin.getConfig().getString("input.id-pattern", ""))) {
            plugin.messages().send(player, "invalid-id");
            return;
        }
        if (plugin.presets().exists(id)) {
            plugin.messages().send(player, "preset-exists", "{preset}", id);
            return;
        }
        plugin.presets().create(id, plugin.getConfig().getStringList("preset-fields"));
        plugin.messages().send(player, "preset-created", "{preset}", id);
        PresetEditGui.open(plugin, player, id);
    }

    private void reopen(Player player, String preset) {
        if (preset.isEmpty() || !plugin.presets().exists(preset)) {
            return;
        }
        PresetEditGui.open(plugin, player, preset);
    }
}
