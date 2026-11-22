package dev.luis.asyncmotd.license;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.plugin.java.JavaPlugin;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class LicenseVerifier {

    private static final long CHECK_INTERVAL_SECONDS = 1L;
    private static final int MAX_CONSECUTIVE_FAILURES = 3;
    private static final String AUTHOR = "Async Studios";

    private static final String GRACE_SECRET = "JusttheSameBetter-ItsluisundQDHShamiro-secret";

    private enum CheckResult { VALID, INVALID, NETWORK_ERROR }

    private final JavaPlugin plugin;
    private final String productName;

    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private final AtomicBoolean inFlight         = new AtomicBoolean(false);
    private final AtomicBoolean disabled         = new AtomicBoolean(false);
    private final AtomicBoolean onGracePeriod    = new AtomicBoolean(false);
    private final AtomicBoolean graceEntryLogged = new AtomicBoolean(false);

    private volatile ScheduledTask watchdogTask;
    private volatile CheckResult lastResult = null;
    private volatile long graceExpiresAt = 0L;

    private final ExecutorService httpExecutor = Executors.newSingleThreadExecutor(r -> {
        final Thread t = new Thread(r, "LicenseVerifier-HTTP");
        t.setDaemon(true);
        return t;
    });

    public LicenseVerifier(final JavaPlugin plugin) {
        this.plugin = plugin;
        this.productName = plugin.getDescription() != null ? plugin.getDescription().getName() : "";
    }

    private String getVerifyUrl() { return "http://23.167.232.146:25640/verify"; }
    private String getAuth()      { return "Bearer ItsluisundQDHShamiro-secret"; }
    private Path   getCacheFile() { return plugin.getDataFolder().toPath().resolve(".license-cache"); }

    public void verifyAsync() {
        sendBanner();

        final String key = plugin.getConfig().getString("license-key", "").trim();
        if (key.isEmpty()) {
            printResult("§c🗡 §fDenied", "The license key in config.yml is empty.");
            logDisable("Missing license key");
            Bukkit.getGlobalRegionScheduler().run(plugin, t -> Bukkit.getPluginManager().disablePlugin(plugin));
            return;
        }

        final Future<CheckResult> future = httpExecutor.submit(() -> performCheck(key, true));

        Bukkit.getAsyncScheduler().runNow(plugin, t -> {
            try {
                final CheckResult result = future.get(25, TimeUnit.SECONDS);
                lastResult = result;
                switch (result) {
                    case VALID -> {
                        printResult("§a🛡 §fAccepted", "License accepted — Guardian active.");
                        startWatchdog();
                        registerPluginEventListener();
                    }
                    case INVALID -> {
                        logDisable("License invalid");
                        Bukkit.getGlobalRegionScheduler().run(plugin, tt -> Bukkit.getPluginManager().disablePlugin(plugin));
                    }
                    case NETWORK_ERROR -> {
                        if (verifyGraceCache(key)) {
                            onGracePeriod.set(true);
                            graceEntryLogged.set(true);
                            printResult("§e🛡 §fGrace Mode", "Backend unreachable — running on cached grace token.");
                            logGraceInfo();
                            startWatchdog();
                            registerPluginEventListener();
                        } else {
                            logDisable("Connection failed and no valid grace token cached");
                            Bukkit.getGlobalRegionScheduler().run(plugin, tt -> Bukkit.getPluginManager().disablePlugin(plugin));
                        }
                    }
                }
            } catch (Exception e) {
                if (verifyGraceCache(key)) {
                    onGracePeriod.set(true);
                    graceEntryLogged.set(true);
                    printResult("§e🛡 §fGrace Mode", "Backend timed out — running on cached grace token.");
                    logGraceInfo();
                    startWatchdog();
                    registerPluginEventListener();
                } else {
                    logDisable("License check timed out");
                    Bukkit.getGlobalRegionScheduler().run(plugin, tt -> Bukkit.getPluginManager().disablePlugin(plugin));
                }
            }
        });
    }

    private CheckResult performCheck(final String key, final boolean verbose) {
        try {
            final String ip = InetAddress.getLocalHost().getHostAddress();

            final String payload = String.format(
                    "{\"licenseKey\":\"%s\",\"plugin\":\"%s\",\"ip\":\"%s\"}",
                    key, productName, ip
            );

            final URL url = new URL(getVerifyUrl());
            final HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Authorization", getAuth());
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            try (final OutputStream os = conn.getOutputStream()) {
                os.write(payload.getBytes(StandardCharsets.UTF_8));
            }

            final int code = conn.getResponseCode();
            final StringBuilder response = new StringBuilder();
            try (final BufferedReader br = new BufferedReader(new InputStreamReader(
                    code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream(),
                    StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) response.append(line);
            }

            final String body   = response.toString();
            final boolean valid = extractBoolean(body, "valid");
            final String reason = extractString(body, "reason", "unknown");

            if (valid) {
                final boolean isPending = extractBoolean(body, "pending");
                if (!isPending) {
                    final String graceToken  = extractString(body, "grace_token", "");
                    final String graceExpStr = extractString(body, "grace_expires_at_unix", "0");
                    if (!graceToken.isEmpty()) {
                        long exp = 0;
                        try { exp = Long.parseLong(graceExpStr.trim()); } catch (NumberFormatException ignored) {}
                        saveGraceCache(key, graceToken, exp);
                        graceExpiresAt = exp;
                    }
                }
                if (onGracePeriod.compareAndSet(true, false)) {
                    graceEntryLogged.set(false);
                    Bukkit.getConsoleSender().sendMessage("§8    ⚐ §a✓ Backend reachable again §8— exited grace mode.");
                    Bukkit.getConsoleSender().sendMessage("§8  ●───────────────────────────────────●");
                }
                return CheckResult.VALID;
            }

            deleteGraceCache();

            if (verbose) {
                switch (reason.toLowerCase()) {
                    case "invalid" ->
                            printResult("§c🗡 §fDenied", "This key does not exist in the database.");
                    case "product_mismatch" ->
                            printResult("§c🗡 §fDenied", "This key does not belong to " + productName + ".");
                    case "suspended" ->
                            printResult("§c🗡 §fDenied", "This license was suspended by the owner.");
                    case "expired" ->
                            printResult("§c🗡 §fDenied", "The license subscription has expired.");
                    case "blacklisted" ->
                            printResult("§c🗡 §fDenied", "This license has been blacklisted.");
                    case "ip_mismatch", "ip_not_registered" ->
                            printResult("§c🗡 §fDenied", "The IP is not allowed for this license.");
                    case "too_many_ips" ->
                            printResult("§c🗡 §fDenied", "License has reached maximum allowed IP bindings.");
                    case "ip_denied" ->
                            printResult("§c🗡 §fDenied", "This IP was denied by the license owner.");
                    default ->
                            printResult("§c🗡 §fDenied", "Backend reason: " + reason);
                }
            } else {
                Bukkit.getConsoleSender().sendMessage("§8    ⚐ §cLicense invalid at runtime §8— reason: §f" + reason);
            }
            return CheckResult.INVALID;

        } catch (Exception e) {
            if (verbose) {
                printResult("§c⚐ §fConnection Failed", e.getMessage());
            }
            return CheckResult.NETWORK_ERROR;
        }
    }

    private void startWatchdog() {
        watchdogTask = Bukkit.getAsyncScheduler().runAtFixedRate(
                plugin,
                t -> runBackgroundCheck(),
                CHECK_INTERVAL_SECONDS,
                CHECK_INTERVAL_SECONDS,
                TimeUnit.SECONDS
        );
    }

    private void runBackgroundCheck() {
        if (disabled.get()) return;
        if (!inFlight.compareAndSet(false, true)) return;

        final String key = plugin.getConfig().getString("license-key", "").trim();
        if (key.isEmpty()) {
            inFlight.set(false);
            triggerDisable("Missing license key (runtime)");
            return;
        }

        httpExecutor.submit(() -> {
            try {
                final CheckResult result = performCheck(key, false);
                lastResult = result;

                switch (result) {
                    case VALID -> consecutiveFailures.set(0);
                    case INVALID -> {
                        consecutiveFailures.set(0);
                        triggerDisable("License revoked at runtime");
                    }
                    case NETWORK_ERROR -> {
                        final int n = consecutiveFailures.incrementAndGet();

                        if (n >= MAX_CONSECUTIVE_FAILURES) {
                            if (verifyGraceCache(key)) {
                                onGracePeriod.set(true);
                                if (graceEntryLogged.compareAndSet(false, true)) {
                                    Bukkit.getConsoleSender().sendMessage("§8    ⚐ §e⚠ Backend unreachable §8— entering grace mode.");
                                    logGraceInfo();
                                }
                                consecutiveFailures.set(0);
                            } else {
                                triggerDisable("License backend unreachable and grace expired");
                            }
                        }
                    }
                }
            } catch (Exception e) {
                // Silent
            } finally {
                inFlight.set(false);
            }
        });
    }

    private void triggerDisable(final String reason) {
        if (!disabled.compareAndSet(false, true)) return;
        final var task = watchdogTask;
        if (task != null) task.cancel();
        deleteGraceCache();
        httpExecutor.shutdownNow();
        Bukkit.getGlobalRegionScheduler().run(plugin, t -> {
            sendBanner();
            Bukkit.getConsoleSender().sendMessage("§8    ● §7Product §8» §f" + productName);
            Bukkit.getConsoleSender().sendMessage("§8    ● §7Status  §8» §c🗡 §fRevoked");
            Bukkit.getConsoleSender().sendMessage("§8    ● §7Info    §8» §7" + reason);
            Bukkit.getConsoleSender().sendMessage("§8  ●───────────────────────────────────●");
            Bukkit.getConsoleSender().sendMessage("§8    ⚐ §c" + reason + " §8— plugin disabled.");
            Bukkit.getConsoleSender().sendMessage("§8  ●───────────────────────────────────●");
            Bukkit.getPluginManager().disablePlugin(plugin);
        });
    }

    private void registerPluginEventListener() {
        Bukkit.getGlobalRegionScheduler().run(plugin, t ->
                Bukkit.getPluginManager().registerEvents(new PluginEventListener(), plugin)
        );
    }

    private final class PluginEventListener implements Listener {
        @EventHandler(priority = EventPriority.MONITOR)
        public void onPluginEnable(final PluginEnableEvent e) {
            if (e.getPlugin() == plugin) return;
            if (disabled.get()) return;
            runBackgroundCheck();
        }

        @EventHandler(priority = EventPriority.MONITOR)
        public void onPluginDisable(final PluginDisableEvent e) {
            if (e.getPlugin() == plugin) return;
            if (disabled.get()) return;
            runBackgroundCheck();
        }
    }

    public void shutdown() {
        final var task = watchdogTask;
        if (task != null) task.cancel();
        httpExecutor.shutdownNow();
    }

    private void saveGraceCache(final String licenseKey, final String token, final long expiresAt) {
        try {
            Files.createDirectories(getCacheFile().getParent());
            final String json = "{"
                    + "\"key\":\""    + escape(licenseKey)  + "\","
                    + "\"plugin\":\"" + escape(productName) + "\","
                    + "\"token\":\""  + escape(token)       + "\","
                    + "\"exp\":"      + expiresAt
                    + "}";
            Files.write(getCacheFile(), json.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            // Silent
        }
    }

    private void deleteGraceCache() {
        try {
            final Path cf = getCacheFile();
            if (Files.exists(cf)) Files.delete(cf);
        } catch (IOException e) {
            // Silent
        }
        graceExpiresAt = 0L;
    }

    private boolean verifyGraceCache(final String currentKey) {
        try {
            final Path cf = getCacheFile();
            if (!Files.exists(cf)) return false;
            final String json = new String(Files.readAllBytes(cf), StandardCharsets.UTF_8);
            final String cachedKey    = extractString(json, "key", "");
            final String cachedPlugin = extractString(json, "plugin", "");
            final String token        = extractString(json, "token", "");
            if (token.isEmpty() || cachedKey.isEmpty() || cachedPlugin.isEmpty()) return false;
            if (!currentKey.equals(cachedKey)) return false;
            if (!productName.equalsIgnoreCase(cachedPlugin)) return false;

            final String[] parts = token.split("\\.");
            if (parts.length != 2) return false;
            final String payloadB64 = parts[0];
            final String sigB64     = parts[1];

            final Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(GRACE_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            final byte[] computed    = mac.doFinal(payloadB64.getBytes(StandardCharsets.UTF_8));
            final String computedB64 = base64UrlEncode(computed);
            if (!constantTimeEquals(computedB64, sigB64)) return false;

            final byte[] payloadBytes = Base64.getUrlDecoder().decode(addPadding(payloadB64));
            final String payloadJson  = new String(payloadBytes, StandardCharsets.UTF_8);
            final String tokKey       = extractString(payloadJson, "key", "");
            final String tokPlugin    = extractString(payloadJson, "plugin", "");
            final String tokExpStr    = extractString(payloadJson, "exp", "0");
            long tokExp = 0;
            try { tokExp = Long.parseLong(tokExpStr.trim()); } catch (NumberFormatException ignored) {}

            if (!currentKey.equals(tokKey)) return false;
            if (!productName.equalsIgnoreCase(tokPlugin)) return false;

            final long now = System.currentTimeMillis() / 1000L;
            if (tokExp <= now) return false;

            graceExpiresAt = tokExp;
            return true;

        } catch (Exception e) {
            return false;
        }
    }

    private void logGraceInfo() {
        final long now      = System.currentTimeMillis() / 1000L;
        final long secsLeft = Math.max(0, graceExpiresAt - now);
        final long hoursLeft = secsLeft / 3600;
        final long minsLeft  = (secsLeft % 3600) / 60;
        Bukkit.getConsoleSender().sendMessage("§8    ● §7Grace   §8» §e" + hoursLeft + "h " + minsLeft + "m §7left until forced disable");
        Bukkit.getConsoleSender().sendMessage("§8  ●───────────────────────────────────●");
    }

    private static boolean constantTimeEquals(final String a, final String b) {
        if (a.length() != b.length()) return false;
        int result = 0;
        for (int i = 0; i < a.length(); i++) result |= a.charAt(i) ^ b.charAt(i);
        return result == 0;
    }

    private static String base64UrlEncode(final byte[] bytes) {
        return Base64.getEncoder().encodeToString(bytes)
                .replace("=", "")
                .replace("+", "-")
                .replace("/", "_");
    }

    private static String addPadding(final String s) {
        final int pad = 4 - (s.length() % 4);
        if (pad < 4) {
            final StringBuilder sb = new StringBuilder(s);
            for (int i = 0; i < pad; i++) sb.append("=");
            return sb.toString();
        }
        return s;
    }

    private static String escape(final String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private boolean extractBoolean(final String json, final String key) {
        final String search = "\"" + key + "\"";
        final int idx = json.indexOf(search);
        if (idx == -1) return false;
        final String rest = json.substring(idx + search.length()).replaceFirst("\\s*:\\s*", "");
        return rest.startsWith("true");
    }

    private String extractString(final String json, final String key, final String defaultValue) {
        final String search = "\"" + key + "\"";
        final int idx = json.indexOf(search);
        if (idx == -1) return defaultValue;
        final String afterKey = json.substring(idx + search.length()).replaceFirst("\\s*:\\s*", "");
        if (afterKey.startsWith("\"")) {
            final String rest = afterKey.substring(1);
            final int end = rest.indexOf('"');
            if (end == -1) return defaultValue;
            return rest.substring(0, end);
        } else {
            final int comma = afterKey.indexOf(',');
            final int brace = afterKey.indexOf('}');
            int end;
            if (comma == -1 && brace == -1) return defaultValue;
            else if (comma == -1) end = brace;
            else if (brace == -1) end = comma;
            else end = Math.min(comma, brace);
            return afterKey.substring(0, end).trim();
        }
    }

    private void sendBanner() {
        Bukkit.getConsoleSender().sendMessage("§e                                      ");
        Bukkit.getConsoleSender().sendMessage("§e     /\\                              ");
        Bukkit.getConsoleSender().sendMessage("§e    /  \\   ___ _   _ _ __   ___      ");
        Bukkit.getConsoleSender().sendMessage("§e   / /\\ \\ / __| | | | '_ \\ / __|     ");
        Bukkit.getConsoleSender().sendMessage("§e  / ____ \\\\__ \\ |_| | | | | (__      ");
        Bukkit.getConsoleSender().sendMessage("§e /_/____\\_\\___/\\__, |_|_|_|\\___|     ");
        Bukkit.getConsoleSender().sendMessage("§e  / ____| |     __/ | | (_)          ");
        Bukkit.getConsoleSender().sendMessage("§e | (___ | |_ _ |___/__| |_  ___  ___ ");
        Bukkit.getConsoleSender().sendMessage("§e  \\___ \\| __| | | |/ _` | |/ _ \\/ __|");
        Bukkit.getConsoleSender().sendMessage("§e  ____) | |_| |_| | (_| | | (_) \\__ \\");
        Bukkit.getConsoleSender().sendMessage("§e |_____/ \\__|\\__,_|\\__,_|_|\\___/|___/");
        Bukkit.getConsoleSender().sendMessage("§e                                      ");
        Bukkit.getConsoleSender().sendMessage("§8  ●───────────────────────────────────●");
        Bukkit.getConsoleSender().sendMessage("§8    ● §7Author  §8» §f" + AUTHOR);
    }

    private void printResult(final String status, final String extraInfo) {
        Bukkit.getConsoleSender().sendMessage("§8    ● §7Product §8» §f" + productName);
        Bukkit.getConsoleSender().sendMessage("§8    ● §7Status  §8» " + status);
        Bukkit.getConsoleSender().sendMessage("§8    ● §7Info    §8» §7" + extraInfo);
        Bukkit.getConsoleSender().sendMessage("§8  ●───────────────────────────────────●");
    }

    private void logDisable(final String reason) {
        Bukkit.getConsoleSender().sendMessage("§8    ⚐ §c" + reason + " §8— plugin disabled.");
        Bukkit.getConsoleSender().sendMessage("§8  ●───────────────────────────────────●");
    }
}
