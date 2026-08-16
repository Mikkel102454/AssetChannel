package nu.miguel.assetChannel.asset;

import net.kyori.adventure.bossbar.BossBar;
import nu.miguel.assetChannel.api.*;
import nu.miguel.assetChannel.texturepack.TexturePack;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.function.Function;

/** Immutable, domain-neutral HUD template loaded from plugins/AssetChannel/hud. */
public final class Hud implements Asset {
    public enum Overflow { ELLIPSIS, CLIP }
    public enum Fallback { TEXT_ONLY, FAIL }

    private final String id;
    private final HudSurface surface;
    private final String slot;
    private final long durationTicks;
    private final int priority;
    private final boolean resumeInterrupted;
    private final Map<String, Variable> variables;
    private final String background;
    private final String content;
    private final String subtitle;
    private final int horizontalPadding;
    private final Integer width;
    private final Overflow overflow;
    private final BossBar.Color bossColor;
    private final BossBar.Overlay bossOverlay;
    private final float bossProgress;
    private final long fadeInTicks, stayTicks, fadeOutTicks;
    private final Fallback fallback;
    private final String fallbackContent;

    public record Variable(boolean required, String defaultValue) {}

    private Hud(String id, YamlConfiguration y) {
        this.id = id;
        this.surface = parseEnum(HudSurface.class, required(y, "surface"));
        this.slot = y.getString("slot", "default").toLowerCase(Locale.ROOT);
        this.durationTicks = ticks(y.get("duration", "4s"), "duration", true);
        this.priority = y.getInt("priority", 0);
        this.resumeInterrupted = y.getBoolean("resume-interrupted", false);
        this.variables = parseVariables(y.getConfigurationSection("variables"));
        this.background = blankToNull(y.getString("layout.background"));
        this.content = required(y, "layout.content");
        this.subtitle = y.getString("layout.subtitle", "");
        this.horizontalPadding = y.getInt("layout.horizontal-padding", 0);
        this.width = y.contains("layout.width") ? y.getInt("layout.width") : null;
        this.overflow = parseEnum(Overflow.class, y.getString("layout.overflow", "ellipsis"));
        this.bossColor = parseEnum(BossBar.Color.class, y.getString("boss-bar.color", "white"));
        this.bossOverlay = parseEnum(BossBar.Overlay.class, y.getString("boss-bar.overlay", "progress"));
        this.bossProgress = (float) y.getDouble("boss-bar.progress", 1.0);
        this.fadeInTicks = ticks(y.get("title.fade-in", "500ms"), "title.fade-in", false);
        this.stayTicks = ticks(y.get("title.stay", durationTicks == 0 ? "1000000s" : y.get("duration", "4s")), "title.stay", false);
        this.fadeOutTicks = ticks(y.get("title.fade-out", "500ms"), "title.fade-out", false);
        String mode = y.getString("fallback.mode", "fail");
        if (!mode.equalsIgnoreCase("text-only") && !mode.equalsIgnoreCase("fail")) throw new IllegalArgumentException("fallback.mode must be text-only or fail in " + id);
        this.fallback = mode.equalsIgnoreCase("text-only") ? Fallback.TEXT_ONLY : Fallback.FAIL;
        this.fallbackContent = y.getString("fallback.content", content);
        validate();
    }

    public static Hud load(String id, Path path) { return new Hud(id, YamlConfiguration.loadConfiguration(path.toFile())); }

    private void validate() {
        if (!id.matches("[a-z0-9._-]+") || !slot.matches("[a-z0-9][a-z0-9._-]*"))
            throw new IllegalArgumentException("Invalid HUD id or slot in " + id);
        if (horizontalPadding < 0 || width != null && width <= 0) throw new IllegalArgumentException("Invalid HUD dimensions in " + id);
        if (!Float.isFinite(bossProgress) || bossProgress < 0 || bossProgress > 1) throw new IllegalArgumentException("boss-bar.progress must be between 0 and 1 in " + id);
        validateVarReferences(content); validateVarReferences(subtitle); validateVarReferences(fallbackContent);
    }

    private void validateVarReferences(String value) {
        var matcher = java.util.regex.Pattern.compile("<var:([a-zA-Z0-9_.-]+)>").matcher(value);
        while (matcher.find()) if (!variables.containsKey(matcher.group(1)))
            throw new IllegalArgumentException("HUD " + id + " references undeclared variable '" + matcher.group(1) + "'");
    }

    private static Map<String, Variable> parseVariables(ConfigurationSection section) {
        if (section == null) return Map.of();
        Map<String, Variable> result = new LinkedHashMap<>();
        for (String name : section.getKeys(false)) {
            if (!name.matches("[a-zA-Z][a-zA-Z0-9_.-]*")) throw new IllegalArgumentException("Invalid HUD variable " + name);
            ConfigurationSection value = section.getConfigurationSection(name);
            if (value == null) throw new IllegalArgumentException("HUD variable " + name + " must be a section");
            boolean required = value.getBoolean("required", false);
            String fallback = value.contains("default") ? String.valueOf(value.get("default")) : null;
            if (required && fallback != null) throw new IllegalArgumentException("Required HUD variable " + name + " cannot have a default");
            result.put(name, new Variable(required, fallback));
        }
        return Map.copyOf(result);
    }

    private static String required(YamlConfiguration y, String path) {
        String result = y.getString(path); if (result == null || result.isBlank()) throw new IllegalArgumentException("Missing '" + path + "'"); return result;
    }
    private static String blankToNull(String s) { return s == null || s.isBlank() ? null : s.toLowerCase(Locale.ROOT); }
    private static <E extends Enum<E>> E parseEnum(Class<E> type, String value) {
        try { return Enum.valueOf(type, value.toUpperCase(Locale.ROOT).replace('-', '_')); }
        catch (RuntimeException e) { throw new IllegalArgumentException("Invalid " + type.getSimpleName() + " value '" + value + "'"); }
    }
    private static long ticks(Object raw, String field, boolean allowZero) {
        String s = String.valueOf(raw).trim().toLowerCase(Locale.ROOT); double millis;
        try {
            if (s.endsWith("ms")) millis = Double.parseDouble(s.substring(0, s.length()-2));
            else if (s.endsWith("s")) millis = Double.parseDouble(s.substring(0, s.length()-1)) * 1000;
            else if (s.endsWith("m")) millis = Double.parseDouble(s.substring(0, s.length()-1)) * 60000;
            else millis = Double.parseDouble(s) * 50;
        } catch (NumberFormatException e) { throw new IllegalArgumentException("Invalid duration " + field + ": " + raw); }
        if (!Double.isFinite(millis) || millis < 0 || !allowZero && millis == 0) throw new IllegalArgumentException("Invalid duration " + field + ": " + raw);
        return millis == 0 ? 0 : Math.max(1, Math.round(millis / 50));
    }

    @Override public boolean build(TexturePack texturePack) { return true; }
    @Override public String getId() { return id; }
    public HudAsset descriptor(Function<String,net.kyori.adventure.text.Component> parser) {
        Map<String,HudVariable> vars = new LinkedHashMap<>();
        variables.forEach((n,v) -> vars.put(n, new HudVariable(n, v.required(), Optional.ofNullable(v.defaultValue()).map(parser))));
        return new HudAsset(id, surface, slot, Duration.ofMillis(durationTicks * 50), priority, resumeInterrupted, vars);
    }
    public HudSurface surface() { return surface; } public String slot() { return slot; } public long durationTicks() { return durationTicks; }
    public int priority() { return priority; } public boolean resumeInterrupted() { return resumeInterrupted; }
    public Map<String,Variable> variables() { return variables; } public String background() { return background; }
    public String content() { return content; } public String subtitle() { return subtitle; } public int horizontalPadding() { return horizontalPadding; }
    public Integer width() { return width; } public Overflow overflow() { return overflow; }
    public BossBar.Color bossColor() { return bossColor; } public BossBar.Overlay bossOverlay() { return bossOverlay; } public float bossProgress() { return bossProgress; }
    public long fadeInTicks() { return fadeInTicks; } public long stayTicks() { return stayTicks; } public long fadeOutTicks() { return fadeOutTicks; }
    public Fallback fallback() { return fallback; } public String fallbackContent() { return fallbackContent; }
}
