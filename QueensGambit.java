package com.chesssmp.weapons;

import com.chesssmp.ChessSMP;
import com.chesssmp.Fx;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Queen's Gambit: Queen's Movement (dash), Diagonal Strike (magic bolt), Royal Pressure (speed on hit). */
public final class QueensGambit extends LegendaryWeapon {

    private final Map<UUID, Long> pressureReady = new HashMap<>();

    public QueensGambit(ChessSMP plugin, WeaponManager mgr) {
        super(plugin, mgr);
    }

    @Override public String id() { return "queens_gambit"; }
    @Override public String alias() { return "queen"; }
    @Override public String defaultName() { return "<gradient:#ffd700:#ffffff><bold>\u2655 Queen's Gambit"; }
    @Override public Material defaultMaterial() { return Material.NETHERITE_SWORD; }
    @Override public String typeName() { return "Netherite Sword"; }
    @Override public String playstyle() { return "Aggressive \u2022 Mobility"; }

    @Override
    public List<String> abilityLore() {
        List<String> l = new ArrayList<>();
        l.addAll(ability("Queen's Movement", "Right-click", "Dash forward. You never clip through blocks.", cooldown("movement", 25)));
        l.addAll(ability("Diagonal Strike", "Sneak + Right-click", "Fire a golden bolt that deals "
                + fmt(num("strike.damage", 7)) + " damage on impact.", cooldown("strike", 35)));
        l.addAll(ability("Royal Pressure", "Passive", "Hits grant Speed I for " + fmt(num("pressure.speed-seconds", 2))
                + "s (every " + fmt(num("pressure.internal-cooldown", 8)) + "s at most).", 0));
        return l;
    }

    @Override
    public void onRightClick(Player p, boolean sneaking) {
        if (sneaking) {
            strike(p);
        } else {
            movement(p);
        }
    }

    private void movement(Player p) {
        if (!mgr.ready(p, this, "movement", "Queen's Movement")) {
            return;
        }
        if (p.isInsideVehicle() || p.isGliding()) {
            plugin.messages().actionBar(p, "weapon-cannot-now");
            return;
        }
        Vector dir = p.getLocation().getDirection().setY(0);
        if (dir.lengthSquared() < 1.0E-4) {
            plugin.messages().actionBar(p, "weapon-cannot-now");
            return;
        }
        dir.normalize();
        double strength = num("movement.strength", 2.0);
        // Velocity-based dash (collision still applies). A wall right in front shortens or cancels it.
        Location eye = p.getEyeLocation();
        RayTraceResult wall = p.getWorld().rayTraceBlocks(eye, dir, strength * 2.5, FluidCollisionMode.NEVER, true);
        if (wall != null) {
            double free = wall.getHitPosition().distance(eye.toVector());
            if (free < 1.5) {
                plugin.messages().actionBar(p, "weapon-blocked");
                return;
            }
            strength = Math.min(strength, free / 2.5);
        }
        mgr.start(p, this, "movement", cooldown("movement", 25));
        Location from = p.getLocation().add(0, 0.5, 0);
        for (int i = 0; i < 8; i++) {
            Fx.particle(plugin, from.clone().add(dir.clone().multiply(i * 0.9)), str("movement.particle", "end_rod"), 3, 0.15);
            Fx.particle(plugin, from.clone().add(dir.clone().multiply(i * 0.9)), "wax_on", 2, 0.2);
        }
        Vector v = dir.multiply(strength);
        v.setY(0.15);
        p.setVelocity(v);
        plugin.abilities().grantNoFall(p.getUniqueId(), 3000);
        Fx.sound(plugin, p.getLocation(), str("movement.sound", "entity.breeze.wind_burst"), 1f, 1.2f);
    }

    private void strike(Player p) {
        if (!mgr.ready(p, this, "strike", "Diagonal Strike")) {
            return;
        }
        mgr.start(p, this, "strike", cooldown("strike", 35));
        Fx.sound(plugin, p.getLocation(), str("strike.sound", "entity.evoker.cast_spell"), 1f, 1.5f);
        mgr.fireBolt(p, new WeaponManager.Bolt(num("strike.range", 25), num("strike.speed", 1.8), num("strike.damage", 7),
                false, 0, num("strike.hit-radius", 0.8), "end_rod", "wax_on", "totem_of_undying", "entity.player.attack.crit"), null, null);
    }

    /** Royal Pressure with an internal cooldown. */
    @Override
    public void afterHit(Player attacker, LivingEntity victim, EntityDamageByEntityEvent event) {
        long now = System.currentTimeMillis();
        if (pressureReady.getOrDefault(attacker.getUniqueId(), 0L) > now) {
            return;
        }
        pressureReady.put(attacker.getUniqueId(), now + (long) (num("pressure.internal-cooldown", 8) * 1000));
        mgr.effect(attacker, PotionEffectType.SPEED, (int) num("pressure.speed-level", 1), num("pressure.speed-seconds", 2));
        Fx.particle(plugin, attacker.getLocation().add(0, 1, 0), "wax_on", 12, 0.4);
    }

    @Override
    public void forget(UUID id) {
        pressureReady.remove(id);
    }
}
