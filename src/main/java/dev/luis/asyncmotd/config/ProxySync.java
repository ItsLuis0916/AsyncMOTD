package dev.luis.asyncmotd.config;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public final class ProxySync implements PluginMessageListener {

    private static final int MAX_PAYLOAD_BYTES = 32_000;

    private final Plugin plugin;
    private final PresetStore presets;
    private final String channel;
    private final String secret;

    public ProxySync(Plugin plugin, PresetStore presets, String channel, String secret) {
        this.plugin = plugin;
        this.presets = presets;
        this.channel = channel;
        this.secret = secret;
        Bukkit.getMessenger().registerOutgoingPluginChannel(plugin, channel);
        Bukkit.getMessenger().registerIncomingPluginChannel(plugin, channel, this);
    }

    public void push(String snapshot) {
        Player carrier = Bukkit.getOnlinePlayers().stream().findFirst().orElse(null);
        if (carrier == null) {
            return;
        }
        byte[] payload = wrap(snapshot);
        if (payload.length > MAX_PAYLOAD_BYTES) {
            plugin.getLogger().warning("presets.yml is too large to sync (" + payload.length + " bytes, limit " + MAX_PAYLOAD_BYTES + ").");
            return;
        }
        carrier.getScheduler().run(plugin, task -> carrier.sendPluginMessage(plugin, channel, payload), null);
    }

    @Override
    public void onPluginMessageReceived(String incoming, Player player, byte[] message) {
        if (!channel.equals(incoming)) {
            return;
        }
        byte[] prefix = (secret + "\n").getBytes(StandardCharsets.UTF_8);
        if (message.length < prefix.length || !MessageDigest.isEqual(prefix, java.util.Arrays.copyOf(message, prefix.length))) {
            return;
        }
        String text = new String(message, prefix.length, message.length - prefix.length, StandardCharsets.UTF_8);
        if (presets.replace(text) < 0) {
            byte[] payload = wrap(presets.snapshot());
            if (payload.length > MAX_PAYLOAD_BYTES) {
                return;
            }
            player.getScheduler().run(plugin, task -> player.sendPluginMessage(plugin, channel, payload), null);
        }
    }

    private byte[] wrap(String snapshot) {
        return (secret + "\n" + snapshot).getBytes(StandardCharsets.UTF_8);
    }

    public void close() {
        Bukkit.getMessenger().unregisterOutgoingPluginChannel(plugin, channel);
        Bukkit.getMessenger().unregisterIncomingPluginChannel(plugin, channel, this);
    }
}
