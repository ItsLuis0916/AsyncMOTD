package dev.luis.asyncmotd.velocity;

import dev.luis.asyncmotd.util.Colors;
import dev.luis.asyncmotd.util.Section;

import com.google.inject.Inject;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.messages.ChannelMessageSink;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import com.velocitypowered.api.util.Favicon;
import org.slf4j.Logger;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

@Plugin(id = "asyncmotd", name = "AsyncMOTD", version = "1.0", authors = {"luis"})
public final class AsyncMOTDVelocity {

    public enum Result { APPLIED, IGNORED, OLDER }

    private static final String REVISION = "revision";
    private static final long MAX_CLOCK_SKEW_MILLIS = 60_000L;
    private static final int MAX_PAYLOAD_BYTES = 32_000;

    private final ProxyServer proxy;
    private final Logger logger;
    private final Path dataDir;
    private volatile Map<String, Favicon> icons = Map.of();
    private volatile Section config = new Section(Map.of());
    private volatile Section presets = new Section(Map.of());
    private volatile String presetsText = "";
    private volatile long revision;
    private MinecraftChannelIdentifier channel;

    @Inject
    public AsyncMOTDVelocity(ProxyServer proxy, Logger logger, @DataDirectory Path dataDir) {
        this.proxy = proxy;
        this.logger = logger;
        this.dataDir = dataDir;
    }

    @Subscribe
    public void onInit(ProxyInitializeEvent event) {
        reload();
        channel = MinecraftChannelIdentifier.from(config.getString("proxy-sync.channel", "asyncmotd:sync"));
        proxy.getChannelRegistrar().register(channel);
        proxy.getEventManager().register(this, new MotdListener(this));
        if (secret().isEmpty()) {
            logger.warn("proxy-sync.secret is empty, presets are not synced with the backends. Set the same secret here and on every backend.");
        }
        CommandManager commands = proxy.getCommandManager();
        String name = config.getString("command.name", "vmotd");
        String permission = config.getString("command.permission", "asyncmotd.admin");
        commands.register(commands.metaBuilder(name).plugin(this).build(), new SimpleCommand() {
            @Override
            public void execute(Invocation invocation) {
                reload();
                if (presets.getSection("presets") == null) {
                    logger.warn("presets.yml is not valid, nothing was synced.");
                    return;
                }
                bumpRevision();
                broadcast(null);
                invocation.source().sendMessage(Colors.text(config.getString("messages.reloaded", "")));
            }

            @Override
            public boolean hasPermission(Invocation invocation) {
                return invocation.source().hasPermission(permission);
            }
        });
    }

    public synchronized void reload() {
        config = new Section(parse(read("config.yml", "velocity/config.yml")));
        String text = read("presets.yml", "presets.yml");
        Section loaded = new Section(parse(text));
        if (loaded.getSection("presets") != null || presetsText.isEmpty()) {
            presetsText = text;
            presets = loaded;
            revision = loaded.getLong(REVISION, 0L);
        }
        loadIcons();
    }

    public synchronized Result replacePresets(String text) {
        Map<?, ?> parsed = parse(text);
        if (parsed.isEmpty()) {
            return Result.IGNORED;
        }
        Section incoming = new Section(parsed);
        long next = incoming.getLong(REVISION, 0L);
        if (incoming.getSection("presets") == null || next > System.currentTimeMillis() + MAX_CLOCK_SKEW_MILLIS) {
            return Result.IGNORED;
        }
        if (next < revision) {
            return Result.OLDER;
        }
        if (next == revision && revision != 0L) {
            return Result.IGNORED;
        }
        if (!write(text)) {
            return Result.IGNORED;
        }
        presetsText = text;
        presets = incoming;
        revision = next;
        return Result.APPLIED;
    }

    private synchronized void bumpRevision() {
        long next = Math.max(System.currentTimeMillis(), revision + 1);
        String stripped = presetsText.replaceAll("(?m)^" + REVISION + ":.*(\\R|$)", "");
        String text = stripped + (stripped.isEmpty() || stripped.endsWith("\n") ? "" : "\n") + REVISION + ": " + next + "\n";
        Map<?, ?> parsed = parse(text);
        if (parsed.isEmpty() || !write(text)) {
            return;
        }
        presetsText = text;
        presets = new Section(parsed);
        revision = next;
    }

    private boolean write(String text) {
        try {
            Path tmp = dataDir.resolve("presets.yml.tmp");
            Files.writeString(tmp, text, StandardCharsets.UTF_8);
            Path target = dataDir.resolve("presets.yml");
            try {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException exception) {
            logger.warn("Could not save presets.yml: {}", exception.getMessage());
            return false;
        }
    }

    private String secret() {
        return config.getString("proxy-sync.secret", "");
    }

    public String unwrap(byte[] data) {
        byte[] prefix = (secret() + "\n").getBytes(StandardCharsets.UTF_8);
        if (secret().isEmpty() || data.length < prefix.length || !MessageDigest.isEqual(prefix, java.util.Arrays.copyOf(data, prefix.length))) {
            return null;
        }
        return new String(data, prefix.length, data.length - prefix.length, StandardCharsets.UTF_8);
    }

    public void send(ChannelMessageSink target) {
        String secret = secret();
        if (secret.isEmpty() || presetsText.isEmpty()) {
            return;
        }
        byte[] payload = (secret + "\n" + presetsText).getBytes(StandardCharsets.UTF_8);
        if (payload.length > MAX_PAYLOAD_BYTES) {
            logger.warn("presets.yml is too large to sync ({} bytes, limit {}).", payload.length, MAX_PAYLOAD_BYTES);
            return;
        }
        target.sendPluginMessage(channel, payload);
    }

    public void broadcast(String exceptServer) {
        proxy.getAllServers().forEach(server -> {
            if (server.getServerInfo().getName().equals(exceptServer) || server.getPlayersConnected().isEmpty()) {
                return;
            }
            send(server);
        });
    }

    public MinecraftChannelIdentifier channel() {
        return channel;
    }

    public Section config() {
        return config;
    }

    public Section presets() {
        return presets;
    }

    public String presetsText() {
        return presetsText;
    }

    public Favicon icon(String name) {
        return name == null || name.isEmpty() ? null : icons.get(name);
    }

    private void loadIcons() {
        Map<String, Favicon> loaded = new HashMap<>();
        Path folder = dataDir.resolve(config.getString("icons.folder", "icons"));
        String extension = config.getString("icons.extension", ".png").toLowerCase(Locale.ROOT);
        try {
            Files.createDirectories(folder);
            try (var files = Files.list(folder)) {
                files.filter(file -> file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(extension)).forEach(file -> {
                    try {
                        loaded.put(file.getFileName().toString(), Favicon.create(file));
                    } catch (Exception exception) {
                        logger.warn("Could not load server icon {}: {}", file.getFileName(), exception.getMessage());
                    }
                });
            }
        } catch (IOException exception) {
            logger.warn("Could not read the icon folder {}: {}", folder, exception.getMessage());
        }
        icons = Map.copyOf(loaded);
    }

    private String read(String name, String resource) {
        Path file = dataDir.resolve(name);
        try {
            if (!Files.exists(file)) {
                Files.createDirectories(dataDir);
                try (InputStream in = AsyncMOTDVelocity.class.getResourceAsStream("/" + resource)) {
                    if (in != null) {
                        Files.copy(in, file);
                    }
                }
            }
            return Files.exists(file) ? Files.readString(file, StandardCharsets.UTF_8) : "";
        } catch (IOException exception) {
            return "";
        }
    }

    private static Map<?, ?> parse(String text) {
        try {
            Object loaded = new Yaml().load(text);
            return loaded instanceof Map<?, ?> map ? map : Map.of();
        } catch (Exception exception) {
            return Map.of();
        }
    }
}
