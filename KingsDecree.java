package com.chesssmp.weapons;

import com.chesssmp.ChessSMP;
import com.chesssmp.Fx;
import com.chesssmp.Messages;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** King's Decree: Castling (swap places), Royal Authority (every third hit), Checkmate (mark), King's Protection. */
public final class KingsDecree extends LegendaryWeapon {

    private final Map<UUID, Integer> hits = new HashMap<>();
    private final Map<UUID, Long> lastHit = new HashMap<>();
    private final Map<UUID, Long> protectionFightEnd = new HashMap<>();

    public KingsDecree(ChessSMP plugin, WeaponManager mgr) {
        super(plugin, mgr);
    }

    @Override public String id() { return "kings_decree"; }
    @Override public String alias() { return "king"; }
    @Override public String defaultName() { return "<gradient:#ffffff:#ffd700><bold>\u265B King's Decree"; }
    @Override public Material defaultMaterial() { return Material.NETHERITE_SWORD; }
    @Override public String typeName() { return "Netherite Sword"; }
    @Override public String playstyle() { return "Balanced \u2022 Position manipulation"; }

    @Override
    public List<String> abilityLore() {
        List<String> l = new ArrayList<>();
        l.addAll(ability("Castling", "Right-click", "Swap places with the nearest enemy within "
                + fmt(num("castling.radius", 12)) + " blocks. Both spots must be safe.", cooldown("castling", 45)));
        l.addAll(ability("Royal Authority", "Passive", "Every " + (int) num("royal-authority.hits", 3)
                + "rd hit in a row grants Speed I for " + fmt(num("royal-authority.speed-seconds", 2)) + "s.", 0));
        l.addAll(ability("Checkmate", "Sneak + Right-click", "Mark the nearest enemy: Speed II for you, Glowing for them.",
                cooldown("checkmate", 40)));
        l.add("<gold>King's Protection");
        l.add("<gray>Below " + fmt(num("kings-protection.hearts", 3)) + " hearts Castling recharges "
                + fmt(num("kings-protection.reduce-seconds", 15)) + "s faster (once per fight).");
        l.add("");
        return l;
    }

    @Override
    public void onRightClick(Player p, boolean sneaking) {
        if (sneaking) {
            checkmate(p);
        } else {
            castling(p);
        }
    }

    private void castling(Player p) {
        if (!mgr.ready(p, this, "castling", "Castling")) {
            return;
        }
        if (p.isInsideVehicle() || p.isGliding() || p.isDead()) {
            plugin.messages().actionBar(p, "weapon-cannot-now");
            return;
        }
        double radius = num("castling.radius", 12);
        Player target = mgr.nearestEnemy(p, radius, true);
        if (target == null) {
            plugin.messages().actionBar(p, "weapon-no-target");
            return;
        }
        Location mine = p.getLocation();
        Location theirs = target.getLocation();
        if (target.isInsideVehicle() || !mgr.isSafe(theirs) || !mgr.isSafe(mine)) {
            plugin.messages().actionBar(p, "weapon-unsafe");
            return;
        }
        Location toUser = theirs.clone();
        toUser.setYaw(mine.getYaw());
        toUser.setPitch(mine.getPitch());
        Location toTarget = mine.clone();
        toTarget.setYaw(theirs.getYaw());
        toTarget.setPitch(theirs.getPitch());

        mgr.start(p, this, "castling", cooldown("castling", 45));
        Fx.particle(plugin, mine.clone().add(0, 1, 0), str("castling.particle", "portal"), (int) num("castling.particle-count", 40), 0.5);
        Fx.particle(plugin, theirs.clone().add(0, 1, 0), str("castling.particle", "portal"), (int) num("castling.particle-count", 40), 0.5);
        p.teleport(toUser);
        target.teleport(toTarget);
        String sound = str("castling.sound", "entity.illusioner.mirror_move");
        Fx.sound(plugin, toUser, sound, 1f, 1f);
        Fx.sound(plugin, toTarget, sound, 1f, 1f);
        int level = (int) num("castling.slowness-level", 1);
        double seconds = num("castling.slowness-seconds", 2);
        mgr.effect(target, PotionEffectType.SLOWNESS, level, seconds);
        if (flag("castling.slow-user", true)) {
            mgr.effect(p, PotionEffectType.SLOWNESS, level, seconds);
        }
        plugin.combat().tag(p.getUniqueId(), target.getUniqueId());
        plugin.messages().actionBar(p, "weapon-castled", Messages.text("player", target.getName()));
        plugin.messages().actionBar(target, "weapon-castled-target", Messages.text("player", p.getName()));
    }

    private void checkmate(Player p) {
        if (!mgr.ready(p, this, "checkmate", "Checkmate")) {
            return;
        }
        Player target = mgr.nearestEnemy(p, num("checkmate.radius", 30), true);
        if (target == null) {
            plugin.messages().actionBar(p, "weapon-no-target");
            return;
        }
        mgr.start(p, this, "checkmate", cooldown("checkmate", 40));
        mgr.effect(p, PotionEffectType.SPEED, (int) num("checkmate.speed-level", 2), num("checkmate.speed-seconds", 4));
        mgr.effect(target, PotionEffectType.GLOWING, 1, num("checkmate.glowing-seconds", 3));
        Fx.particle(plugin, target.getLocation().add(0, 2.3, 0), str("checkmate.particle", "crit"), 20, 0.3);
        Fx.sound(plugin, p.getLocation(), str("checkmate.sound", "block.bell.use"), 1f, 0.8f);
        plugin.combat().tag(p.getUniqueId(), target.getUniqueId());
        plugin.messages().actionBar(p, "weapon-checkmate", Messages.text("player", target.getName()));
        plugin.messages().actionBar(target, "weapon-marked", Messages.text("player", p.getName()));
    }

    @Override
    public void afterHit(Player attacker, LivingEntity victim, EntityDamageByEntityEvent event) {
        UUID id = attacker.getUniqueId();
        long now = System.currentTimeMillis();
        Long last = lastHit.get(id);
        if (last != null && now - last > (long) (num("royal-authority.reset-seconds", 4) * 1000)) {
            hits.remove(id);
        }
        lastHit.put(id, now);
        int count = hits.merge(id, 1, Integer::sum);
        if (count >= Math.max(1, (int) num("royal-authority.hits", 3))) {
            hits.remove(id);
            mgr.effect(attacker, PotionEffectType.SPEED, (int) num("royal-authority.speed-level", 1), num("royal-authority.speed-seconds", 2));
            Fx.particle(plugin, attacker.getLocation().add(0, 1, 0), str("royal-authority.particle", "crit"), 10, 0.4);
        }
        keepFightAlive(id);
    }

    private void keepFightAlive(UUID id) {
        Long end = protectionFightEnd.get(id);
        if (end != null && end > System.currentTimeMillis()) {
            protectionFightEnd.put(id, System.currentTimeMillis() + fightLength());
        }
    }

    private long fightLength() {
        return plugin.getConfig().getLong("combat.tag-seconds", 20) * 1000L;
    }

    /** King's Protection: below 3 hearts the Castling cooldown shrinks - once per fight. */
    @Override
    public void onDamaged(Player victim, EntityDamageEvent event) {
        if (!flag("kings-protection.enabled", true)) {
            return;
        }
        UUID id = victim.getUniqueId();
        long now = System.currentTimeMillis();
        Long end = protectionFightEnd.get(id);
        if (end != null && end > now) {
            protectionFightEnd.put(id, now + fightLength()); // same fight, already used
            return;
        }
        double threshold = num("kings-protection.hearts", 3) * 2.0;
        if (victim.getHealth() - event.getFinalDamage() >= threshold) {
            return;
        }
        protectionFightEnd.put(id, now + fightLength());
        if (mgr.remaining(victim, this, "castling") > 0) {
            mgr.reduce(victim, this, "castling", (long) (num("kings-protection.reduce-seconds", 15) * 1000));
            plugin.messages().actionBar(victim, "weapon-protection");
            Fx.sound(plugin, victim.getLocation(), "block.beacon.power_select", 1f, 1.4f);
        }
    }

    @Override
    public void forget(UUID id) {
        hits.remove(id);
        lastHit.remove(id);
        protectionFightEnd.remove(id);
    }
}
