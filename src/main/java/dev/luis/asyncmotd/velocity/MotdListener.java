package dev.luis.asyncmotd.velocity;

import dev.luis.asyncmotd.util.Centering;
import dev.luis.asyncmotd.util.Colors;
import dev.luis.asyncmotd.util.Gradients;
import dev.luis.asyncmotd.util.Section;

import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.event.player.ServerPostConnectEvent;
import com.velocitypowered.api.event.proxy.ProxyPingEvent;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.server.ServerPing;
import com.velocitypowered.api.util.Favicon;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class MotdListener {

    private final AsyncMOTDVelocity plugin;

    public MotdListener(AsyncMOTDVelocity plugin) {
        this.plugin = plugin;
    }

    @Subscribe(order = PostOrder.LATE)
    public void onPing(ProxyPingEvent event) {
        Section config = plugin.config();
        if (!config.getBoolean("ping.enabled", true)) {
            return;
        }
        String active = plugin.presets().getString("active", "");
        Section all = plugin.presets().getSection("presets");
        Section preset = all == null ? null : all.getSection(active);
        if (preset == null) {
            return;
        }

        ServerPing ping = event.getPing();
        String online = String.valueOf(ping.getPlayers().map(ServerPing.Players::getOnline).orElse(0));
        String max = String.valueOf(ping.getPlayers().map(ServerPing.Players::getMax).orElse(0));
        ServerPing.Builder builder = ping.asBuilder();

        if (config.getBoolean("ping.apply-motd", true)) {
            String lineOne = center(render(preset.getString("line-1", ""), online, max));
            String lineTwo = center(render(preset.getString("line-2", ""), online, max));
            builder.description(Colors.text(lineOne + "\n" + lineTwo));
        }

        if (config.getBoolean("ping.apply-icon", true)) {
            Favicon icon = plugin.icon(preset.getString("icon", ""));
            if (icon != null) {
                builder.favicon(icon);
            }
        }

        if (config.getBoolean("ping.apply-hover", true)) {
            List<ServerPing.SamplePlayer> sample = new ArrayList<>();
            for (String line : preset.getStringList("hover")) {
                sample.add(new ServerPing.SamplePlayer(legacy(render(line, online, max)), UUID.randomUUID()));
            }
            if (!sample.isEmpty()) {
                builder.clearSamplePlayers();
                builder.samplePlayers(sample.toArray(new ServerPing.SamplePlayer[0]));
            }
        }

        if (config.getBoolean("ping.apply-version", true)) {
            String version = preset.getString("version", "");
            if (!version.isEmpty()) {
                int protocol = config.getBoolean("version.force-display", false)
                        ? config.getInt("version.protocol", -1)
                        : ping.getVersion().getProtocol();
                builder.version(new ServerPing.Version(protocol, legacy(render(version, online, max))));
            }
        }

        event.setPing(builder.build());
    }

    @Subscribe
    public void onMessage(PluginMessageEvent event) {
        if (!event.getIdentifier().equals(plugin.channel())) {
            return;
        }
        event.setResult(PluginMessageEvent.ForwardResult.handled());
        if (!(event.getSource() instanceof ServerConnection source)) {
            return;
        }
        String text = plugin.unwrap(event.getData());
        if (text == null) {
            return;
        }
        AsyncMOTDVelocity.Result result = plugin.replacePresets(text);
        if (result == AsyncMOTDVelocity.Result.APPLIED) {
            plugin.broadcast(source.getServerInfo().getName());
        } else if (result == AsyncMOTDVelocity.Result.OLDER) {
            plugin.send(source);
        }
    }

    @Subscribe
    public void onConnect(ServerPostConnectEvent event) {
        event.getPlayer().getCurrentServer().ifPresent(plugin::send);
    }

    private String legacy(String raw) {
        Section config = plugin.config();
        if (!config.getBoolean("ping.legacy-named-colors", true)) {
            return Colors.legacy(raw);
        }
        return Colors.legacyNamed(raw, config.getSection("ping.legacy-overrides"));
    }

    private String render(String raw, String online, String max) {
        Section config = plugin.config();
        String filled = raw;
        String onlineKey = config.getString("placeholders.online", "");
        String maxKey = config.getString("placeholders.max", "");
        if (!onlineKey.isEmpty()) {
            filled = filled.replace(onlineKey, online);
        }
        if (!maxKey.isEmpty()) {
            filled = filled.replace(maxKey, max);
        }
        return Gradients.apply(filled, config.getSection("gradient"));
    }

    private String center(String raw) {
        return Centering.center(raw, plugin.config().getSection("center"));
    }
}
