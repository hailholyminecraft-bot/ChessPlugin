ackage com.chesssmp.weapons;

import com.chesssmp.ChessSMP;
import com.chesssmp.Fx;
import com.chesssmp.Messages;
import com.chesssmp.gui.Items;
import com.chesssmp.items.CustomItems;
import com.chesssmp.storage.PlayerData;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * Registry and shared services of the legendary weapons.
 *
 * Security: every weapon item carries its id, a unique instance id and a signature (SHA-256 over a secret that is
 * generated on the server). Renamed or NBT-edited items without a valid signature are ordinary items.
 * Cooldowns are saved per player together with the other player data.
 */
public final class WeaponManager {

    private record Mark(UUID owner, long end) { }

    private record Zone(UUID owner, Location center, double radius, long end, double ownerReduction,
                        double allyReduction) { }

    private record Use(UUID user, long time) { }

    /** Settings of a magic bolt. */
    public record Bolt(double range, double stepPerTick, double damage, boolean piercing, double falloff,
                       double hitRadius, String trailA, String trailB, String impactParticle, String impactSound) { }

    private final ChessSMP plugin;
    private final Map<String, LegendaryWeapon> weapons = new LinkedHashMap<>();
    private final NamespacedKey idKey;
    private final NamespacedKey instanceKey;
    private final NamespacedKey sigKey;
    private byte[] secret;
    private final Map<UUID, Mark> marks = new HashMap<>();
    private final List<Zone> zones = new ArrayList<>();
    private final Map<UUID, Use> instanceUse = new HashMap<>();
    private BukkitTask zoneTask;
    private boolean suppress;

    public WeaponManager(ChessSMP plugin) {
        this.plugin = plugin;
        this.idKey = new NamespacedKey(plugin, "legendary_weapon");
        this.instanceKey = new NamespacedKey(plugin, "legendary_instance");
        this.sigKey = new NamespacedKey(plugin, "legendary_sig");
        loadSecret();
        for (LegendaryWeapon w : List.of(new KingsDecree(plugin, this), new QueensGambit(plugin, this),
                new RooksFortress(plugin, this), new BishopsJudgement(plugin, this),
                new KnightsPursuit(plugin, this), new PawnsSacrifice(plugin, this))) {
            weapons.put(w.id(), w);
        }
    }

    // ------------------------------------------------------------------ registry

    public Collection<LegendaryWeapon> all() {
        return weapons.values();
    }

    public LegendaryWeapon get(String id) {
        return weapons.get(id);
    }

    private static String normalize(String text) {
        return text.toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");
    }

    /** Finds a weapon by id, alias or display name ("kings_decree", "king", "King's Decree"). */
    public LegendaryWeapon find(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String n = normalize(text);
        for (LegendaryWeapon w : weapons.values()) {
            if (normalize(w.id()).equals(n) || normalize(w.alias()).equals(n) || normalize(w.plainName()).equals(n)) {
                return w;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ secret / signature

    private void loadSecret() {
        Path file = plugin.getDataFolder().toPath().resolve("weapon-secret.key");
        try {
            if (Files.exists(file)) {
                secret = Files.readString(file, StandardCharsets.UTF_8).trim().getBytes(StandardCharsets.UTF_8);
                if (secret.length >= 32) {
                    return;
                }
            }
            byte[] raw = new byte[32];
            new SecureRandom().nextBytes(raw);
            StringBuilder hex = new StringBuilder();
            for (byte b : raw) {
                hex.append(String.format("%02x", b));
            }
            Files.createDirectories(file.getParent());
            Files.writeString(file, hex.toString(), StandardCharsets.UTF_8);
            secret = hex.toString().getBytes(StandardCharsets.UTF_8);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "Could not read/write weapon-secret.key - weapons will be invalid after a restart", ex);
            byte[] raw = new byte[32];
            new SecureRandom().nextBytes(raw);
            secret = raw;
        }
    }

    private String sign(String id, String instance) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(secret);
            md.update((id + ":" + instance).getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            byte[] digest = md.digest();
            for (int i = 0; i < 12; i++) {
                hex.append(String.format("%02x", digest[i]));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    // ------------------------------------------------------------------ items

    public ItemStack create(String id) {
        LegendaryWeapon w = weapons.get(id);
        if (w == null) {
            return null;
        }
        ItemStack item = new ItemStack(w.material());
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Items.noItalic(plugin.messages().mm(w.rawName())));
        meta.lore(lore(w));
        meta.setUnbreakable(plugin.getConfig().getBoolean("weapons." + id + ".unbreakable", true));
        meta.setEnchantmentGlintOverride(true);
        CustomItems.applyModel(plugin, meta, plugin.getConfig().getString("weapons." + id + ".model", "chesssmp:" + id));
        String instance = UUID.randomUUID().toString();
        meta.getPersistentDataContainer().set(idKey, PersistentDataType.STRING, id);
        meta.getPersistentDataContainer().set(instanceKey, PersistentDataType.STRING, instance);
        meta.getPersistentDataContainer().set(sigKey, PersistentDataType.STRING, sign(id, instance));
        item.setItemMeta(meta);
        return item;
    }

    public List<Component> lore(LegendaryWeapon w) {
        List<String> raw = plugin.getConfig().getStringList("weapons." + w.id() + ".lore");
        if (raw.isEmpty()) {
            raw = new ArrayList<>();
            raw.add("<gold><bold>LEGENDARY</bold> <dark_gray>\u2022 <gray>" + w.typeName());
            raw.add("<dark_gray>" + w.playstyle());
            raw.add("");
            raw.addAll(w.abilityLore());
            raw.add("<dark_gray><italic>A legendary weapon of the ChessSMP.");
        }
        List<Component> out = new ArrayList<>();
        for (String line : raw) {
            out.add(Items.noItalic(plugin.messages().mm(line)));
        }
        return out;
    }

    /** The weapon an item really is (valid signature), or null for ordinary / forged items. */
    public LegendaryWeapon weaponOf(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        var pdc = item.getItemMeta().getPersistentDataContainer();
        String id = pdc.get(idKey, PersistentDataType.STRING);
        String instance = pdc.get(instanceKey, PersistentDataType.STRING);
        String sig = pdc.get(sigKey, PersistentDataType.STRING);
        if (id == null || instance == null || sig == null || !sig.equals(sign(id, instance))) {
            return null;
        }
        return weapons.get(id);
    }

    public boolean isWeapon(ItemStack item) {
        return weaponOf(item) != null;
    }

    /** Weapon in the main hand (passives). */
    public LegendaryWeapon held(Player p) {
        return weaponOf(p.getInventory().getItemInMainHand());
    }

    /**
     * Like {@link #weaponOf} but also records the use. Two different players using the very same weapon instance
     * within three seconds means the item was duplicated - the use is refused and logged.
     */
    public LegendaryWeapon authorize(Player p, ItemStack item) {
        LegendaryWeapon w = weaponOf(item);
        if (w == null || !p.hasPermission("chesssmp.legendary.use")) {
            return null;
        }
        UUID instance;
        try {
            instance = UUID.fromString(item.getItemMeta().getPersistentDataContainer().get(instanceKey, PersistentDataType.STRING));
        } catch (IllegalArgumentException ex) {
            return null;
        }
        long now = System.currentTimeMillis();
        Use previous = instanceUse.get(instance);
        if (previous != null && !previous.user().equals(p.getUniqueId()) && now - previous.time() < 3000) {
            plugin.getLogger().warning("Duplicated legendary weapon detected: " + w.id() + " used by " + p.getName()
                    + " and another player within 3 seconds (instance " + instance + ")");
            return null;
        }
        instanceUse.put(instance, new Use(p.getUniqueId(), now));
        if (instanceUse.size() > 500) {
            instanceUse.values().removeIf(u -> now - u.time() > 60_000);
        }
        return w;
    }

    // ------------------------------------------------------------------ event entry points

    public void use(Player p, LegendaryWeapon w, boolean sneaking) {
        try {
            w.onRightClick(p, sneaking);
        } catch (RuntimeException ex) {
            plugin.getLogger().log(Level.SEVERE, "Legendary weapon " + w.id() + " failed for " + p.getName(), ex);
            plugin.messages().actionBar(p, "ability-error");
        }
    }

    public boolean suppressed() {
        return suppress;
    }

    public void onKill(Player killer, Player victim) {
        for (LegendaryWeapon w : weapons.values()) {
            try {
                w.onKill(killer, victim);
            } catch (RuntimeException ex) {
                plugin.getLogger().log(Level.SEVERE, "Legendary weapon " + w.id() + " failed on kill", ex);
            }
        }
    }

    public void beforeHit(Player attacker, LivingEntity victim, EntityDamageByEntityEvent e) {
        LegendaryWeapon w = held(attacker);
        if (w != null) {
            w.beforeHit(attacker, victim, e);
        }
    }

    public void afterHit(Player attacker, LivingEntity victim, EntityDamageByEntityEvent e) {
        LegendaryWeapon w = held(attacker);
        if (w != null) {
            w.afterHit(attacker, victim, e);
        }
    }

    public void damaged(Player victim, EntityDamageEvent e) {
        LegendaryWeapon main = held(victim);
        LegendaryWeapon off = weaponOf(victim.getInventory().getItemInOffHand());
        if (main != null) {
            main.onDamaged(victim, e);
        }
        if (off != null && off != main) {
            off.onDamaged(victim, e);
        }
    }

    public void forget(UUID id) {
        for (LegendaryWeapon w : weapons.values()) {
            w.forget(id);
        }
        marks.remove(id);
        zones.removeIf(z -> z.owner().equals(id));
    }

    public void shutdown() {
        if (zoneTask != null) {
            zoneTask.cancel();
            zoneTask = null;
        }
        zones.clear();
        marks.clear();
    }

    // ------------------------------------------------------------------ cooldowns (persisted in PlayerData)

    private String cdKey(LegendaryWeapon w, String ability) {
        return "w_" + w.id() + "_" + ability;
    }

    public long remaining(Player p, LegendaryWeapon w, String ability) {
        return Math.max(0, plugin.data().of(p).cooldownEnd(cdKey(w, ability)) - System.currentTimeMillis());
    }

    /** Checks the cooldown; tells the player and returns false while it runs. */
    public boolean ready(Player p, LegendaryWeapon w, String ability, String name) {
        long remain = remaining(p, w, ability);
        if (remain > 0) {
            plugin.messages().actionBar(p, "weapon-cooldown", Messages.text("ability", name),
                    Messages.text("time", Messages.duration(remain)));
            return false;
        }
        return true;
    }

    public void start(Player p, LegendaryWeapon w, String ability, double seconds) {
        plugin.data().of(p).setCooldown(cdKey(w, ability), System.currentTimeMillis() + (long) (seconds * 1000));
        plugin.data().markDirty();
    }

    public void reduce(Player p, LegendaryWeapon w, String ability, long millis) {
        PlayerData d = plugin.data().of(p);
        long now = System.currentTimeMillis();
        long end = d.cooldownEnd(cdKey(w, ability));
        if (end > now) {
            d.setCooldown(cdKey(w, ability), Math.max(now, end - millis));
            plugin.data().markDirty();
        }
    }

    // ------------------------------------------------------------------ targets and safety

    /** May {@code owner} harm / target {@code other}? (not trusted, not a teammate, alive, survival-like) */
    public boolean canTarget(Player owner, Player other) {
        if (other.equals(owner) || other.isDead() || !other.isOnline()) {
            return false;
        }
        GameMode mode = other.getGameMode();
        if (mode == GameMode.SPECTATOR || mode == GameMode.CREATIVE) {
            return false;
        }
        return !plugin.data().of(owner).trusted().contains(other.getUniqueId()) && !plugin.events().sameSide(owner, other);
    }

    public Player nearestEnemy(Player user, double radius, boolean lineOfSight) {
        Player best = null;
        double bestDist = Double.MAX_VALUE;
        for (Player other : user.getWorld().getNearbyPlayers(user.getLocation(), radius)) {
            if (!canTarget(user, other) || (lineOfSight && !user.hasLineOfSight(other))) {
                continue;
            }
            double d = other.getLocation().distanceSquared(user.getLocation());
            if (d <= radius * radius && d < bestDist) {
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
    public boolean isSafe(Location loc) {
        World world = loc.getWorld();
        if (world == null || loc.getBlockY() <= world.getMinHeight() + 1) {
            return false;
        }
        Block feet = loc.getBlock();
        Block head = feet.getRelative(0, 1, 0);
        if (dangerous(feet.getType()) || dangerous(head.getType()) || !feet.isPassable() || !head.isPassable()) {
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

    /** True when nothing solid lies between the two points (checked at foot and eye height). */
    public boolean pathClear(Location from, Location to) {
        if (from.getWorld() == null || !from.getWorld().equals(to.getWorld())) {
            return false;
        }
        Vector delta = to.toVector().subtract(from.toVector());
        double length = delta.length();
        if (length < 0.01) {
            return true;
        }
        Vector dir = delta.clone().normalize();
        for (double h : new double[]{0.3, 1.5}) {
            Location start = from.clone().add(0, h, 0);
            if (from.getWorld().rayTraceBlocks(start, dir, length, FluidCollisionMode.NEVER, true) != null) {
                return false;
            }
        }
        return true;
    }

    public void effect(Player p, PotionEffectType type, int level, double seconds) {
        if (level > 0 && seconds > 0) {
            plugin.abilities().applyEffect(p, type, level, (int) (seconds * 20));
        }
    }

    // ------------------------------------------------------------------ marks (Bishop)

    public void mark(LivingEntity victim, Player owner, double seconds) {
        marks.put(victim.getUniqueId(), new Mark(owner.getUniqueId(), System.currentTimeMillis() + (long) (seconds * 1000)));
    }

    public boolean markedBy(LivingEntity victim, Player owner) {
        Mark m = marks.get(victim.getUniqueId());
        if (m == null) {
            return false;
        }
        if (m.end() < System.currentTimeMillis()) {
            marks.remove(victim.getUniqueId());
            return false;
        }
        return m.owner().equals(owner.getUniqueId());
    }

    // ------------------------------------------------------------------ protective zones (Rook)

    /** Creates a non-solid protective area (never traps anybody). One zone per owner and kind. */
    public void addZone(Player owner, double radius, double seconds, double ownerReduction, double allyReduction) {
        zones.removeIf(z -> z.owner().equals(owner.getUniqueId()) && (z.allyReduction() > 0) == (allyReduction > 0));
        zones.add(new Zone(owner.getUniqueId(), owner.getLocation().clone(), radius,
                System.currentTimeMillis() + (long) (seconds * 1000), ownerReduction, allyReduction));
        if (zoneTask == null) {
            zoneTask = new BukkitRunnable() {
                int tick;

                @Override
                public void run() {
                    long now = System.currentTimeMillis();
                    zones.removeIf(z -> z.end() < now);
                    if (zones.isEmpty()) {
                        cancel();
                        zoneTask = null;
                        return;
                    }
                    tick++;
                    for (Zone z : zones) {
                        ring(z, tick % 2 == 0 ? "end_rod" : "wax_on");
                    }
                }
            }.runTaskTimer(plugin, 4L, 4L);
        }
    }

    private void ring(Zone z, String particle) {
        int points = Math.max(16, (int) (z.radius() * 8));
        for (int i = 0; i < points; i++) {
            double angle = 2 * Math.PI * i / points;
            Location at = z.center().clone().add(Math.cos(angle) * z.radius(), 0.15, Math.sin(angle) * z.radius());
            Fx.particle(plugin, at, particle, 1, 0.0);
            if (i % 4 == 0) {
                Fx.particle(plugin, at.clone().add(0, 1.2, 0), particle, 1, 0.0);
            }
        }
    }

    /** Damage multiplier of the protective zones the victim is standing in (1 = none). */
    public double zoneMultiplier(Player victim) {
        if (zones.isEmpty()) {
            return 1.0;
        }
        long now = System.currentTimeMillis();
        double mult = 1.0;
        for (Zone z : zones) {
            if (z.end() < now || !z.center().getWorld().equals(victim.getWorld())
                    || z.center().distanceSquared(victim.getLocation()) > z.radius() * z.radius()) {
                continue;
            }
            if (z.owner().equals(victim.getUniqueId())) {
                mult = Math.min(mult, 1.0 - z.ownerReduction());
            } else if (z.allyReduction() > 0) {
                Player owner = Bukkit.getPlayer(z.owner());
                if (owner != null && plugin.relations().isTeammate(owner, victim)) {
                    mult = Math.min(mult, 1.0 - z.allyReduction());
                }
            }
        }
        return mult;
    }

    // ------------------------------------------------------------------ magic bolts (Queen, Bishop)

    private boolean boltTarget(Player owner, LivingEntity e) {
        if (e.equals(owner) || e.isDead() || e instanceof ArmorStand || plugin.meteor().isShopVillager(e)) {
            return false;
        }
        return !(e instanceof Player other) || canTarget(owner, other);
    }

    /**
     * Fires a bolt that flies in a straight line, stops at solid blocks and damages what it hits.
     *
     * @param adjust optional damage adjustment per target (e.g. Holy Precision bonus)
     * @param after  optional callback for every target that was hit
     */
    public void fireBolt(Player owner, Bolt spec, BiFunction<LivingEntity, Double, Double> adjust, Consumer<LivingEntity> after) {
        Vector dir = owner.getEyeLocation().getDirection().normalize();
        Location start = owner.getEyeLocation().add(dir.clone().multiply(0.6));
        new BukkitRunnable() {
            final Location pos = start.clone();
            final Set<UUID> hit = new HashSet<>();
            double travelled;
            int hits;

            private void impact(Location at) {
                Fx.particle(plugin, at, spec.impactParticle(), 25, 0.4);
                Fx.sound(plugin, at, spec.impactSound(), 1f, 1.2f);
            }

            @Override
            public void run() {
                int substeps = 3;
                double step = spec.stepPerTick() / substeps;
                for (int i = 0; i < substeps; i++) {
                    if (!owner.isOnline() || travelled >= spec.range()) {
                        cancel();
                        return;
                    }
                    pos.add(dir.clone().multiply(step));
                    travelled += step;
                    if (!pos.getBlock().isPassable()) {
                        impact(pos);
                        cancel();
                        return;
                    }
                    Fx.particle(plugin, pos, spec.trailA(), 1, 0.05);
                    Fx.particle(plugin, pos, spec.trailB(), 1, 0.08);
                    for (Entity e : pos.getWorld().getNearbyEntities(pos, spec.hitRadius(), spec.hitRadius(), spec.hitRadius())) {
                        if (!(e instanceof LivingEntity target) || hit.contains(target.getUniqueId()) || !boltTarget(owner, target)) {
                            continue;
                        }
                        double damage = spec.damage() * Math.max(0.1, 1.0 - spec.falloff() * hits);
                        if (adjust != null) {
                            damage = adjust.apply(target, damage);
                        }
                        suppress = true;
                        try {
                            target.damage(damage, owner);
                        } finally {
                            suppress = false;
                        }
                        hit.add(target.getUniqueId());
                        hits++;
                        if (after != null) {
                            after.accept(target);
                        }
                        impact(target.getLocation().add(0, 1, 0));
                        if (!spec.piercing()) {
                            cancel();
                            return;
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    // ------------------------------------------------------------------ promotion (Pawn's Sacrifice)

    public boolean ownsPawn(Player p) {
        for (ItemStack it : p.getInventory().getContents()) {
            if (weaponOf(it) instanceof PawnsSacrifice) {
                return true;
            }
        }
        return false;
    }

    public List<String> promotionChoices() {
        List<String> ids = new ArrayList<>();
        for (String raw : plugin.getConfig().getStringList("promotion.choices")) {
            LegendaryWeapon w = find(raw);
            if (w != null && !(w instanceof PawnsSacrifice) && !ids.contains(w.id())) {
                ids.add(w.id());
            }
        }
        if (ids.isEmpty()) {
            ids.addAll(List.of("queens_gambit", "rooks_fortress", "bishops_judgement", "knights_pursuit"));
        }
        return ids;
    }

    /** Replaces one Pawn's Sacrifice in the inventory with the chosen weapon. */
    public boolean promote(Player p, String weaponId) {
        PlayerData d = plugin.data().of(p);
        if (!d.promotionReady()) {
            plugin.messages().send(p, "promo-not-ready");
            return false;
        }
        if (d.promoted() != null && !plugin.getConfig().getBoolean("promotion.allow-repeat", false)) {
            plugin.messages().send(p, "promo-already");
            return false;
        }
        if (!promotionChoices().contains(weaponId)) {
            return false;
        }
        int slot = -1;
        for (int i = 0; i < p.getInventory().getSize(); i++) {
            if (weaponOf(p.getInventory().getItem(i)) instanceof PawnsSacrifice) {
                slot = i;
                break;
            }
        }
        if (slot < 0) {
            plugin.messages().send(p, "promo-no-axe");
            return false;
        }
        ItemStack reward = create(weaponId);
        p.getInventory().setItem(slot, reward);
        d.setPromoted(weaponId);
        d.setPromotionReady(false);
        d.setPawnKills(0);
        plugin.data().markDirty();
        LegendaryWeapon w = weapons.get(weaponId);
        plugin.messages().send(p, "promo-done", Messages.text("weapon", w.plainName()));
        Bukkit.broadcast(plugin.messages().component("promo-broadcast", Messages.text("player", p.getName()),
                Messages.text("weapon", w.plainName())));
        Fx.particle(plugin, p.getLocation().add(0, 1, 0), "totem_of_undying", 60, 0.7);
        Fx.sound(plugin, p.getLocation(), "ui.toast.challenge_complete", 1f, 1f);
        return true;
    }
}
