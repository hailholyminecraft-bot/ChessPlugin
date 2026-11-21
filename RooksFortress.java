
package com.chesssmp.weapons;

import com.chesssmp.ChessSMP;
import com.chesssmp.Fx;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Rook's Fortress (shield): Fortress barrier, Counterattack on blocks, Tower's Protection area. */
public final class RooksFortress extends LegendaryWeapon {

    private final Map<UUID, Long> counterReady = new HashMap<>();

    public RooksFortress(ChessSMP plugin, WeaponManager mgr) {
        super(plugin, mgr);
    }

    @Override public String id() { return "rooks_fortress"; }
    @Override public String alias() { return "rook"; }
    @Override public String defaultName() { return "<gradient:#a0a0a0:#ffd700><bold>\u265C Rook's Fortress"; }
    @Override public Material defaultMaterial() { return Material.SHIELD; }
    @Override public String typeName() { return "Custom Shield"; }
    @Override public String playstyle() { return "Defensive \u2022 Tank"; }
    @Override public boolean allowOffhand() { return true; }
    @Override public boolean cancelsInteract() { return false; }

    @Override
    public List<String> abilityLore() {
        List<String> l = new ArrayList<>();
        l.addAll(ability("Fortress", "Right-click", "A barrier of " + fmt(num("fortress.radius", 3.5)) + " blocks for "
                + fmt(num("fortress.duration", 5)) + "s: you take " + (int) (num("fortress.reduction", 0.5) * 100)
                + "% less damage inside. It never traps anyone.", cooldown("fortress", 50)));
        l.addAll(ability("Counterattack", "Passive", "Blocking an attack grants Resistance I for "
                + fmt(num("counter.resistance-seconds", 3)) + "s.", 0));
        l.add("<dark_gray>Internal cooldown: <white>" + fmt(num("counter.internal-cooldown", 10)) + "s");
        l.add("");
        l.addAll(ability("Tower's Protection", "Sneak + Right-click", "Allies in " + fmt(num("tower.radius", 6))
                + " blocks take " + (int) (num("tower.ally-reduction", 0.15) * 100) + "% less damage for "
                + fmt(num("tower.duration", 6)) + "s.", cooldown("tower", 60)));
        return l;
    }

    @Override
    public void onRightClick(Player p, boolean sneaking) {
        String ability = sneaking ? "tower" : "fortress";
        if (!mgr.ready(p, this, ability, sneaking ? "Tower's Protection" : "Fortress")) {
            return;
        }
        mgr.start(p, this, ability, cooldown(ability, sneaking ? 60 : 50));
        if (sneaking) {
            mgr.addZone(p, num("tower.radius", 6), num("tower.duration", 6), num("tower.owner-reduction", 0.2), num("tower.ally-reduction", 0.15));
            burst(p.getLocation().add(0, 1, 0), "tower", "end_rod", 40, "block.beacon.activate", 1.2f);
        } else {
            mgr.addZone(p, num("fortress.radius", 3.5), num("fortress.duration", 5), num("fortress.reduction", 0.5), 0);
            burst(p.getLocation().add(0, 1, 0), "fortress", "wax_on", 40, "block.anvil.land", 0.8f);
        }
    }

    /** Counterattack: damage that was fully absorbed by the shield. */
    @Override
    public void onDamaged(Player victim, EntityDamageEvent event) {
        if (!victim.isBlocking() || event.getDamage() <= 0 || event.getFinalDamage() > 0.001) {
            return;
        }
        long now = System.currentTimeMillis();
        if (counterReady.getOrDefault(victim.getUniqueId(), 0L) > now) {
            return;
        }
        counterReady.put(victim.getUniqueId(), now + (long) (num("counter.internal-cooldown", 10) * 1000));
        mgr.effect(victim, PotionEffectType.RESISTANCE, (int) num("counter.resistance-level", 1), num("counter.resistance-seconds", 3));
        Fx.particle(plugin, victim.getLocation().add(0, 1, 0), "wax_on", 15, 0.5);
        Fx.sound(plugin, victim.getLocation(), "item.shield.block", 1f, 1.5f);
    }

    @Override
    public void forget(UUID id) {
        counterReady.remove(id);
    }
}
