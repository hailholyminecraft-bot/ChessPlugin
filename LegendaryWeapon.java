package com.chesssmp.weapons;

import com.chesssmp.ChessSMP;
import com.chesssmp.Fx;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** One legendary weapon: identity, item look, lore and the ability logic. Values come from config.yml (weapons.&lt;id&gt;). */
public abstract class LegendaryWeapon {

    protected final ChessSMP plugin;
    protected final WeaponManager mgr;

    protected LegendaryWeapon(ChessSMP plugin, WeaponManager mgr) {
        this.plugin = plugin;
        this.mgr = mgr;
    }

    // ------------------------------------------------------------------ identity

    public abstract String id();

    /** Short name for commands (king, queen ...). */
    public abstract String alias();

    /** MiniMessage name used when config.yml has no "name". */
    public abstract String defaultName();

    public abstract Material defaultMaterial();

    /** "Netherite Sword", "Custom Shield" ... shown under the name. */
    public abstract String typeName();

    public abstract String playstyle();

    /** Lore lines for the abilities, built from the current config values. */
    public abstract List<String> abilityLore();

    public boolean allowOffhand() {
        return false;
    }

    /** False for the shield so that blocking still works. */
    public boolean cancelsInteract() {
        return true;
    }

    // ------------------------------------------------------------------ events (all optional)

    public void onRightClick(Player p, boolean sneaking) { }

    /** Before damage is applied (may change the damage). Attacker holds this weapon. */
    public void beforeHit(Player attacker, LivingEntity victim, EntityDamageByEntityEvent event) { }

    /** After a successful hit. Attacker holds this weapon. */
    public void afterHit(Player attacker, LivingEntity victim, EntityDamageByEntityEvent event) { }

    /** The wielder (weapon in either hand) took damage. */
    public void onDamaged(Player victim, EntityDamageEvent event) { }

    /** A player killed another player. Every weapon is asked; it decides whether the killer owns it. */
    public void onKill(Player killer, Player victim) { }

    public void forget(UUID id) { }

    // ------------------------------------------------------------------ config helpers

    protected double num(String path, double def) {
        return plugin.getConfig().getDouble("weapons." + id() + "." + path, def);
    }

    protected boolean flag(String path, boolean def) {
        return plugin.getConfig().getBoolean("weapons." + id() + "." + path, def);
    }

    protected String str(String path, String def) {
        return plugin.getConfig().getString("weapons." + id() + "." + path, def);
    }

    protected double cooldown(String ability, double def) {
        return num(ability + ".cooldown", def);
    }

    public String rawName() {
        return plugin.getConfig().getString("weapons." + id() + ".name", defaultName());
    }

    /** Name without formatting, e.g. "♛ King's Decree". */
    public String plainName() {
        return PlainTextComponentSerializer.plainText().serialize(MiniMessage.miniMessage().deserialize(rawName()));
    }

    public Material material() {
        Material m = Material.matchMaterial(str("material", defaultMaterial().name()));
        return m == null || !m.isItem() ? defaultMaterial() : m;
    }

    // ------------------------------------------------------------------ lore / feedback helpers

    protected static String fmt(double v) {
        return v == Math.floor(v) ? String.valueOf((long) v) : String.format(Locale.ROOT, "%.1f", v);
    }

    /** Three lore lines for one ability. cooldown &lt;= 0 prints no cooldown line. */
    protected List<String> ability(String name, String how, String description, double cooldownSeconds) {
        List<String> lines = new ArrayList<>();
        lines.add("<gold>" + name + " <dark_gray>[" + how + "]");
        lines.add("<gray>" + description);
        if (cooldownSeconds > 0) {
            lines.add("<dark_gray>Cooldown: <white>" + fmt(cooldownSeconds) + "s");
        }
        lines.add("");
        return lines;
    }

    /** Particles and sound of an ability, configurable under weapons.&lt;id&gt;.&lt;path&gt;.particle / .sound. */
    protected void burst(Location loc, String path, String defParticle, int defCount, String defSound, float pitch) {
        Fx.particle(plugin, loc, str(path + ".particle", defParticle), (int) num(path + ".particle-count", defCount), 0.5);
        Fx.sound(plugin, loc, str(path + ".sound", defSound), (float) num(path + ".sound-volume", 1.0), pitch);
    }
}
