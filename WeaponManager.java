package com.chesssmp.weapons;

import com.chesssmp.ChessSMP;
import com.chesssmp.Fx;
import com.chesssmp.Messages;
import com.chesssmp.gui.Items;
import com.chesssmp.items.CustomItems;
import com.chesssmp.storage.PlayerData;
import net.kyori.adventure.text.Component;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Legendary weapons. Identified by a PersistentDataContainer tag, configured in config.yml ("weapons.*").
 * Cooldowns are stored per player (not per item) with the other cooldowns, so they survive relogs, restarts,
 * dropping and re-picking the weapon.
 *
 * Implemented: King's Decree. Queen's Gambit and the rest of the set follow.
 */
public final class WeaponManager {

    public static final String KINGS_DECREE = "kings_decree";
    private static final String CD_CASTLING = "kd_castling";
    private static final String CD_CHECKMATE = "kd_checkmate";

    private final ChessSMP plugin;
    private final NamespacedKey weaponKey;
    private final Map<UUID, int[]> hitCounter = new HashMap<>();      // [count]
    private final Map<UUID, Long> lastHit = new HashMap<>();
    private final Map<UUID, Long> protectionFightEnd = new HashMap<>();

    public WeaponManager(ChessSMP plugin) {
        this.plugin = plugin;
        this.weaponKey = new NamespacedKey(plugin, "legendary_weapon");
    }

    public static Set<String> ids() {
        return Set.of(KINGS_DECREE);
    }

    // ------------------------------------------------------------------ item

    public ItemStack create(String id) {
        ConfigurationSection s = plugin.getConfig().getConfigurationSection("weapons." + id);
        if (s == null || !ids().contains(id)) {
            return null;
        }
        Material material = Material.matchMaterial(s.getString("material", "NETHERITE_SWORD"));
        if (material == null || !material.isItem()) {
            material = Material.NETHERITE_SWORD;
        }
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Items.noItalic(plugin.messages().mm(s.getString("name", id))));
        List<Component> lore = new ArrayList<>();
        for (String line : s.getStringList("lore")) {
            lore.add(Items.noItalic(plugin.messages().mm(line)));
        }
        meta.lore(lore);
        meta.setUnbreakable(s.getBoolean("unbreakable", true));
        meta.setEnchantmentGlintOverride(true);
        CustomItems.applyModel(plugin, meta, s.getString("model", ""));
        meta.getPersistentDataContainer().set(weaponKey, PersistentDataType.STRING, id);
        item.setItemMeta(meta);
        return item;
    }

    public String idOf(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        return item.getItemMeta().getPersistentDataContainer().get(weaponKey, PersistentDataType.STRING);
    }

    public boolean isWeapon(ItemStack item) {
        return idOf(item) != null;
    }

    private boolean holding(Player p, String id) {
        return id.equals(idOf(p.getInventory().getItemInMainHand()));
    }

    // ------------------------------------------------------------------ helpers

    private ConfigurationSection cfg(String path) {
        return plugin.getConfig().getConfigurationSection("weapons." + KINGS_DECREE + "." + path);
    }

    private double num(String path, String key, double def) {
        ConfigurationSection s = cfg(path);
        return s == null ? def : s.getDouble(key, def);
    }

    /** A player the wielder may target: alive, in the same world, not trusted, not a teammate, visible. */
    private boolean validTarget(Player user, Player other, double radius) {
        if (other.equals(user) || other.isDead() || !other.isOnline()) {
            return false;
        }
        GameMode mode = other.getGameMode();
        if (mode == GameMode.SPECTATOR || mode == GameMode.CREATIVE) {
            return false;
        }
        if (!other.getWorld().equals(user.getWorld()) || other.getLocation().distanceSquared(user.getLocation()) > radius * radius) {
            return false;
        }
        if (plugin.data().of(user).trusted().contains(other.getUniqueId()) || plugin.events().sameSide(user, other)) {
            return false;
        }
        if (plugin.getConfig().getBoolean("weapons." + KINGS_DECREE + ".require-hostile", false)
                && !plugin.relations().isEnemy(user, other)) {
            return false;
        }
        return user.hasLineOfSight(other); // never through walls
    }

    private Player nearestEnemy(Player user, double radius) {
        Player best = null;
        double bestDist = Double.MAX_VALUE;
        for (Player other : user.getWorld().getNearbyPlayers(user.getLocation(), radius)) {
            if (!validTarget(user, other, radius)) {
                continue;
            }
            double d = other.getLocation().distanceSquared(user.getLocation());
            if (d < bestDist) {
                bestDist = d;
                best = other;
            }
        }
        return best;
    }

    private static boolean dangerous(Material m) {
        return switch (m) {
            case LAVA, FIRE, SOUL_FIRE, MAGMA_BLOCK, CACTUS, CAMPFIRE, SOUL_CAMPFIRE, SWEET_BERRY_BUSH,
                 WITHER_ROSE, POWDER_SNOW, COBWEB -> true;
            default -> false;
        };
    }

    /** Feet and head free, no hazard, and solid ground within four blocks below (no void, no lava). */
    static boolean isSafe(Location loc) {
        if (loc.getWorld() == null) {
            return false;
        }
        Block feet = loc.getBlock();
        Block head = feet.getRelative(0, 1, 0);
        if (loc.getBlockY() <= loc.getWorld().getMinHeight() + 1 || dangerous(feet.getType()) || dangerous(head.getType())) {
            return false;
        }
        if (!feet.isPassable() || !head.isPassable()) {
            return false;
        }
        for (int i = 1; i <= 4; i++) {
            Block below = feet.getRelative(0, -i, 0);
            if (dangerous(below.getType())) {
                return false;
            }
            if (below.getType() == Material.WATER || !below.isPassable()) {
                return true;
            }
        }
        return false;
    }

    private long remaining(PlayerData d, String cooldownId) {
        return Math.max(0, d.cooldownEnd(cooldownId) - System.currentTimeMillis());
    }

    private void applyCooldown(PlayerData d, String cooldownId, double seconds) {
        d.setCooldown(cooldownId, System.currentTimeMillis() + (long) (seconds * 1000));
        plugin.data().markDirty();
    }

    private void effect(Player p, PotionEffectType type, int level, double seconds) {
        if (level > 0 && seconds > 0) {
            plugin.abilities().applyEffect(p, type, level, (int) (seconds * 20));
        }
    }

    // ------------------------------------------------------------------ right-click entry point

    /** Called for a right-click with a legendary weapon in the main hand. */
    public void onRightClick(Player p) {
        String id = idOf(p.getInventory().getItemInMainHand());
        if (!KINGS_DECREE.equals(id) || !p.hasPermission("chesssmp.player")) {
            return;
        }
        if (p.isSneaking()) {
            checkmate(p);
        } else {
            castling(p);
        }
    }

    // ------------------------------------------------------------------ King's Decree

    private void castling(Player p) {
        PlayerData d = plugin.data().of(p);
        long remain = remaining(d, CD_CASTLING);
        if (remain > 0) {
            plugin.messages().actionBar(p, "weapon-cooldown", Messages.text("ability", "Castling"),
                    Messages.text("time", Messages.duration(remain)));
            return;
        }
        if (p.isInsideVehicle() || p.isGliding() || p.isDead()) {
            plugin.messages().actionBar(p, "weapon-cannot-now");
            return;
        }
        double radius = num("castling", "radius", 12);
        Player target = nearestEnemy(p, radius);
        if (target == null) {
            plugin.messages().actionBar(p, "weapon-no-target");
            return;
        }
        Location mine = p.getLocation();
        Location theirs = target.getLocation();
        // Both destinations must be safe before anybody moves.
        if (target.isInsideVehicle() || !isSafe(theirs) || !isSafe(mine)) {
            plugin.messages().actionBar(p, "weapon-unsafe");
            return;
        }
        Location toUser = theirs.clone();
        toUser.setYaw(mine.getYaw());
        toUser.setPitch(mine.getPitch());
        Location toTarget = mine.clone();
        toTarget.setYaw(theirs.getYaw());
        toTarget.setPitch(theirs.getPitch());

        applyCooldown(d, CD_CASTLING, num("castling", "cooldown", 45));
        String particle = cfg("castling").getString("particle", "portal");
        int count = cfg("castling").getInt("particle-count", 40);
        Fx.particle(plugin, mine.clone().add(0, 1, 0), particle, count, 0.5);
        Fx.particle(plugin, theirs.clone().add(0, 1, 0), particle, count, 0.5);

        p.teleport(toUser);
        target.teleport(toTarget);

        String sound = cfg("castling").getString("sound", "entity.illusioner.mirror_move");
        Fx.sound(plugin, toUser, sound, 1f, 1f);
        Fx.sound(plugin, toTarget, sound, 1f, 1f);
        int level = (int) num("castling", "slowness-level", 1);
        double seconds = num("castling", "slowness-seconds", 2);
        effect(target, PotionEffectType.SLOWNESS, level, seconds);
        if (cfg("castling").getBoolean("slow-user", true)) {
            effect(p, PotionEffectType.SLOWNESS, level, seconds);
        }
        plugin.combat().tag(p.getUniqueId(), target.getUniqueId());
        plugin.messages().actionBar(p, "weapon-castled", Messages.text("player", target.getName()));
        plugin.messages().actionBar(target, "weapon-castled-target", Messages.text("player", p.getName()));
    }

    private void checkmate(Player p) {
        PlayerData d = plugin.data().of(p);
        long remain = remaining(d, CD_CHECKMATE);
        if (remain > 0) {
            plugin.messages().actionBar(p, "weapon-cooldown", Messages.text("ability", "Checkmate"),
                    Messages.text("time", Messages.duration(remain)));
            return;
        }
        Player target = nearestEnemy(p, num("checkmate", "radius", 30));
        if (target == null) {
            plugin.messages().actionBar(p, "weapon-no-target");
            return;
        }
        applyCooldown(d, CD_CHECKMATE, num("checkmate", "cooldown", 40));
        effect(p, PotionEffectType.SPEED, (int) num("checkmate", "speed-level", 2), num("checkmate", "speed-seconds", 4));
        effect(target, PotionEffectType.GLOWING, 1, num("checkmate", "glowing-seconds", 3));
        Fx.particle(plugin, target.getLocation().add(0, 2.2, 0), cfg("checkmate").getString("particle", "crit"), 20, 0.3);
        Fx.sound(plugin, p.getLocation(), cfg("checkmate").getString("sound", "block.bell.use"), 1f, 0.8f);
        plugin.combat().tag(p.getUniqueId(), target.getUniqueId());
        plugin.messages().actionBar(p, "weapon-checkmate", Messages.text("player", target.getName()));
        plugin.messages().actionBar(target, "weapon-marked", Messages.text("player", p.getName()));
    }

    /** Royal Authority: every third consecutive hit grants a short Speed I. */
    public void onMeleeHit(Player attacker, LivingEntity victim) {
        if (!holding(attacker, KINGS_DECREE)) {
            return;
        }
        ConfigurationSection s = cfg("royal-authority");
        if (s == null) {
            return;
        }
        UUID id = attacker.getUniqueId();
        long now = System.currentTimeMillis();
        Long last = lastHit.get(id);
        if (last != null && now - last > (long) (s.getDouble("reset-seconds", 4) * 1000)) {
            hitCounter.remove(id);
        }
        lastHit.put(id, now);
        int[] counter = hitCounter.computeIfAbsent(id, k -> new int[1]);
        counter[0]++;
        if (counter[0] >= Math.max(1, s.getInt("hits", 3))) {
            counter[0] = 0;
            effect(attacker, PotionEffectType.SPEED, s.getInt("speed-level", 1), s.getDouble("speed-seconds", 2));
            Fx.particle(plugin, attacker.getLocation().add(0, 1, 0), s.getString("particle", "crit"), 10, 0.4);
        }
    }

    /**
     * King's Protection: when the wielder drops below 3 hearts, Castling's cooldown is reduced once per fight.
     * A fight lasts until nobody has been in combat with the wielder for the combat-tag time.
     */
    public void onWielderDamaged(Player victim, double finalDamage) {
        ConfigurationSection s = cfg("kings-protection");
        if (s == null || !s.getBoolean("enabled", true) || !holding(victim, KINGS_DECREE)) {
            return;
        }
        UUID id = victim.getUniqueId();
        long now = System.currentTimeMillis();
        long fightLength = plugin.getConfig().getLong("combat.tag-seconds", 20) * 1000L;
        Long end = protectionFightEnd.get(id);
        boolean sameFight = end != null && end > now;
        if (sameFight) {
            protectionFightEnd.put(id, now + fightLength); // fight goes on
            return;
        }
        double threshold = s.getDouble("hearts", 3) * 2.0;
        if (victim.getHealth() - finalDamage >= threshold) {
            return;
        }
        protectionFightEnd.put(id, now + fightLength);
        PlayerData d = plugin.data().of(victim);
        long reduceMs = (long) (s.getDouble("reduce-seconds", 15) * 1000);
        long cdEnd = d.cooldownEnd(CD_CASTLING);
        if (cdEnd > now) {
            d.setCooldown(CD_CASTLING, Math.max(now, cdEnd - reduceMs));
            plugin.data().markDirty();
            plugin.messages().actionBar(victim, "weapon-protection");
            Fx.sound(plugin, victim.getLocation(), "block.beacon.power_select", 1f, 1.4f);
        }
    }

    /** Keeps an ongoing fight alive when the wielder deals damage. */
    public void onWielderAttacked(Player attacker) {
        Long end = protectionFightEnd.get(attacker.getUniqueId());
        if (end != null && end > System.currentTimeMillis()) {
            protectionFightEnd.put(attacker.getUniqueId(),
                    System.currentTimeMillis() + plugin.getConfig().getLong("combat.tag-seconds", 20) * 1000L);
        }
    }

    public void forget(UUID id) {
        hitCounter.remove(id);
        lastHit.remove(id);
        protectionFightEnd.remove(id);
    }

    public static String normalize(String text) {
        return text.toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
    }
}
