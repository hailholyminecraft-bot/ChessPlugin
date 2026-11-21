package com.chesssmp.weapons;

import com.chesssmp.ChessSMP;
import com.chesssmp.Fx;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Knight's Pursuit (mace): Knight's Leap, L-Shape Movement, Mounted Combat. */
public final class KnightsPursuit extends LegendaryWeapon {

    private final Map<UUID, Long> mountedReady = new HashMap<>();

    public KnightsPursuit(ChessSMP plugin, WeaponManager mgr) {
        super(plugin, mgr);
    }

    @Override public String id() { return "knights_pursuit"; }
    @Override public String alias() { return "knight"; }
    @Override public String defaultName() { return "<gradient:#ffffff:#ffd700><bold>\u265E Knight's Pursuit"; }
    @Override public Material defaultMaterial() { return Material.MACE; }
    @Override public String typeName() { return "Netherite Mace"; }
    @Override public String playstyle() { return "Mobility \u2022 Burst damage"; }

    @Override
    public List<String> abilityLore() {
        List<String> l = new ArrayList<>();
        l.addAll(ability("Knight's Leap", "Right-click", "Launch yourself forward and up. No fall damage from the leap.", cooldown("leap", 25)));
        l.addAll(ability("L-Shape Movement", "Sneak + Right-click", "Jump " + (int) num("lshape.forward", 3) + " blocks forward and "
                + (int) num("lshape.sideways", 2) + " to the side, like a knight.", cooldown("lshape", 35)));
        l.addAll(ability("Mounted Combat", "Passive", "A mace hit grants Speed I for " + fmt(num("mounted.speed-seconds", 2))
                + "s (every " + fmt(num("mounted.internal-cooldown", 10)) + "s at most).", 0));
        return l;
    }

    @Override
    public void onRightClick(Player p, boolean sneaking) {
        if (sneaking) {
            lShape(p);
        } else {
            leap(p);
        }
    }

    private void leap(Player p) {
        if (!mgr.ready(p, this, "leap", "Knight's Leap")) {
            return;
        }
        if (p.isInsideVehicle() || p.isGliding()) {
            plugin.messages().actionBar(p, "weapon-cannot-now");
            return;
        }
        Vector dir = p.getLocation().getDirection().setY(0);
        if (dir.lengthSquared() < 1.0E-4) {
            dir = new Vector(0, 0, 1);
        }
        dir.normalize().multiply(num("leap.forward", 1.3));
        dir.setY(num("leap.vertical", 0.9));
        mgr.start(p, this, "leap", cooldown("leap", 25));
        p.setVelocity(dir);
        plugin.abilities().grantNoFall(p.getUniqueId(), (long) (num("leap.fall-protection", 5) * 1000));
        Location at = p.getLocation().add(0, 0.2, 0);
        Fx.particle(plugin, at, str("leap.particle", "cloud"), 30, 0.5);
        Fx.particle(plugin, at, "crit", 15, 0.4);
        Fx.sound(plugin, at, str("leap.sound", "entity.horse.jump"), 1f, 0.9f);
        Fx.sound(plugin, at, "entity.horse.gallop", 0.8f, 1f);
    }

    private Location lPath(Player p, Vector forward, Vector side, int f, int s) {
        Location cur = p.getLocation().clone();
        for (int i = 0; i < f; i++) {
            Location next = cur.clone().add(forward);
            if (!mgr.pathClear(cur, next)) {
                return null;
            }
            cur = next;
        }
        for (int i = 0; i < s; i++) {
            Location next = cur.clone().add(side);
            if (!mgr.pathClear(cur, next)) {
                return null;
            }
            cur = next;
        }
        return mgr.isSafe(cur) ? cur : null;
    }

    private void lShape(Player p) {
        if (!mgr.ready(p, this, "lshape", "L-Shape Movement")) {
            return;
        }
        Vector forward = p.getLocation().getDirection().setY(0);
        if (p.isInsideVehicle() || forward.lengthSquared() < 1.0E-4) {
            plugin.messages().actionBar(p, "weapon-cannot-now");
            return;
        }
        forward.normalize();
        Vector right = new Vector(-forward.getZ(), 0, forward.getX());
        int f = Math.max(1, (int) num("lshape.forward", 3));
        int s = Math.max(1, (int) num("lshape.sideways", 2));
        Location dest = lPath(p, forward, right, f, s);
        if (dest == null) {
            dest = lPath(p, forward, right.clone().multiply(-1), f, s);
        }
        if (dest == null) {
            plugin.messages().actionBar(p, "weapon-unsafe");
            return;
        }
        Location from = p.getLocation();
        mgr.start(p, this, "lshape", cooldown("lshape", 35));
        Fx.particle(plugin, from.clone().add(0, 1, 0), str("lshape.particle", "cloud"), 25, 0.4);
        dest.setYaw(from.getYaw());
        dest.setPitch(from.getPitch());
        p.teleport(dest);
        Fx.particle(plugin, dest.clone().add(0, 1, 0), str("lshape.particle", "cloud"), 25, 0.4);
        Fx.sound(plugin, dest, str("lshape.sound", "entity.horse.step_wood"), 1f, 1f);
    }

    @Override
    public void afterHit(Player attacker, LivingEntity victim, EntityDamageByEntityEvent event) {
        if (attacker.getInventory().getItemInMainHand().getType() != Material.MACE) {
            return;
        }
        long now = System.currentTimeMillis();
        if (mountedReady.getOrDefault(attacker.getUniqueId(), 0L) > now) {
            return;
        }
        mountedReady.put(attacker.getUniqueId(), now + (long) (num("mounted.internal-cooldown", 10) * 1000));
        mgr.effect(attacker, PotionEffectType.SPEED, (int) num("mounted.speed-level", 1), num("mounted.speed-seconds", 2));
        Fx.particle(plugin, attacker.getLocation().add(0, 1, 0), "cloud", 12, 0.4);
    }

    @Override
    public void forget(UUID id) {
        mountedReady.remove(id);
    }
}
