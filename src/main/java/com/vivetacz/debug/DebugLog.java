/*
 * ViveTaCZ — VR support for TaCZ Refabricated via Vivecraft.
 * Copyright (C) 2026 Laggy
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 * This is an unofficial add-on and is not affiliated with or endorsed by
 * the TaCZ (Timeless & Classics Zero) or Vivecraft projects. It bundles no
 * TaCZ or Vivecraft code or assets; it references them at runtime only.
 */

package com.vivetacz.debug;

import com.vivetacz.ViveTaCZClient;
import com.vivetacz.config.ViveTaczConfig;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.math.Vec3d;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * Opt-in diagnostic logger (toggle: {@link ViveTaczConfig#debugLogging}).
 *
 * Everything the mod computes at runtime — VR poses/rotations, the resolved barrel
 * direction, where the bullet spawns and how fast, recoil, two-hand grip state and
 * reload events — is dumped here, tagged by category, so aim/placement can be tuned
 * from real numbers instead of guesswork.
 *
 * Writes to <code>logs/vivetacz-debug.log</code> (fresh each game start) AND mirrors
 * to the normal log with a <code>[DBG]</code> prefix. Cheap when disabled (one bool).
 */
public final class DebugLog {

    private DebugLog() {}

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private static PrintWriter writer;
    private static boolean initTried;
    private static final Map<String, Long> lastThrottleMs = new HashMap<>();

    public static boolean enabled() {
        return ViveTaczConfig.get().debugLogging;
    }

    /** Log a pre-formatted line under a short category tag (no-op unless enabled). */
    public static void log(String category, String message) {
        if (!enabled()) return;
        emit(category, message);
    }

    /** printf-style convenience (formatted only when enabled). */
    public static void logf(String category, String fmt, Object... args) {
        if (!enabled()) return;
        emit(category, safeFormat(fmt, args));
    }

    /**
     * Log at most once per {@code everyMs} for the given key — for high-frequency
     * streams like the per-frame pose so they don't drown the file.
     */
    public static void throttled(String key, long everyMs, String category, String fmt, Object... args) {
        if (!enabled()) return;
        long now = System.currentTimeMillis();
        Long last = lastThrottleMs.get(key);
        if (last != null && now - last < everyMs) return;
        lastThrottleMs.put(key, now);
        emit(category, safeFormat(fmt, args));
    }

    // ------------------------------------------------------------------ format
    /** Compact vector formatter used across call sites. */
    public static String v(Vec3d p) {
        return p == null ? "null"
                : String.format("(%.3f, %.3f, %.3f)", p.x, p.y, p.z);
    }

    /** Compact quaternion formatter (accepts a JOML Quaternionfc as Object). */
    public static String q(Object rot) {
        if (rot instanceof org.joml.Quaternionfc) {
            org.joml.Quaternionfc r = (org.joml.Quaternionfc) rot;
            return String.format("[x%.3f y%.3f z%.3f w%.3f]", r.x(), r.y(), r.z(), r.w());
        }
        return "null";
    }

    // ------------------------------------------------------------------ sink
    private static void emit(String category, String message) {
        String line = String.format("[%s] %-5s %s", LocalDateTime.now().format(TIME), category, message);
        try {
            ViveTaCZClient.LOGGER.info("[DBG] {}", line);
        } catch (Throwable ignored) {
        }
        PrintWriter w = writer();
        if (w != null) {
            w.println(line);
            w.flush();
        }
    }

    private static synchronized PrintWriter writer() {
        if (writer != null) return writer;
        if (initTried) return null;
        initTried = true;
        try {
            Path dir = FabricLoader.getInstance().getGameDir().resolve("logs");
            Files.createDirectories(dir);
            Path file = dir.resolve("vivetacz-debug.log");
            writer = new PrintWriter(Files.newBufferedWriter(file, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING));
            writer.println("=== ViveTaCZ debug log — session start "
                    + LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) + " ===");
            writer.flush();
        } catch (IOException e) {
            ViveTaCZClient.LOGGER.warn("[ViveTaCZ] Could not open debug log file (console only)", e);
            writer = null;
        }
        return writer;
    }

    private static String safeFormat(String fmt, Object... args) {
        try {
            return String.format(fmt, args);
        } catch (Exception e) {
            return fmt + " " + java.util.Arrays.toString(args);
        }
    }
}
