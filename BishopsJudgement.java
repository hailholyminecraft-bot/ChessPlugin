package com.chesssmp.weapons;

import com.chesssmp.ChessSMP;
import com.chesssmp.Fx;
import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

import java.util.ArrayList;
import java.util.List;

/** Bishop's Judgement (trident): Divine Shot, Diagonal Judgement (piercing), Holy Precision (marked targets). */
public final class BishopsJudgement extends LegendaryWeapon {

    public BishopsJudgement(ChessSMP plugin, WeaponManager mgr) {
        super(plugin, mgr);
    }

    @Override public String id() { return "bishops_judgement"; }
    @Override public String alias() { return "bishop"; }
    @Override public String defaultName() { return "<gradient:#ffffff:#c9a227><bold>\u265D Bishop's Judgement"; }
    @Override public Material defaultMaterial() { return Material.TRIDENT; }
    @Override public String typeName() { return "Netherite Trident"; }
    @Override public String playstyle() { return "Ranged \u2022 Precision"; }

    @Override
    public List<String> abilityLore() {
        List<String> l = new ArrayList<>();
        l.addAll(ability("Divine Shot", "Right-click", "Fire a holy bolt for " + fmt(num("shot.damage", 8))
                + " damage. Hits mark the target.", cooldown("shot", 30)));
        l.addAll(ability("Diagonal Judgement", "Sneak + Right-click", "A piercing bolt that hits every enemy in a line. Each extra target takes "
                + (int) (num("judgement.falloff", 0.25) * 100) + "% less.", cooldown("judgement", 45)));
        l.addAll(ability("Holy Precision", "Passive", "Marked enemies take " + (int) (num("precision.bonus", 0.3) * 100)
                + "% more damage from you for " + fmt(num("precision.mark-seconds", 4)) + "s.", 0));
        return l;
    }

    @Override
    public void onRightClick(Player p, boolean sneaking) {
        if (sneaking) {
            judgement(p);
        } else {
            shot(p);
        }
    }

    private double bonus(Player owner, LivingEntity target, double damage) {
        return mgr.markedBy(target, owner) ? damage * (1 + num("precision.bonus", 0.3)) : damage;
    }

    private void shot(Player p) {
        if (!mgr.ready(p, this, "shot", "Divine Shot")) {
            return;
        }
        mgr.start(p, this, "shot", cooldown("shot", 30));
        Fx.sound(plugin, p.getLocation(), str("shot.sound", "item.trident.throw"), 1f, 1.4f);
        mgr.fireBolt(p, new WeaponManager.Bolt(num("shot.range", 40), num("shot.speed", 2.2), num("shot.damage", 8), false, 0,
                        num("shot.hit-radius", 0.8), "end_rod", "wax_on", "totem_of_undying", "item.trident.hit"),
                (target, damage) -> bonus(p, target, damage),
                target -> mgr.mark(target, p, num("precision.mark-seconds", 4)));
    }

    private void judgement(Player p) {
        if (!mgr.ready(p, this, "judgement", "Diagonal Judgement")) {
            return;
        }
        mgr.start(p, this, "judgement", cooldown("judgement", 45));
        Fx.sound(plugin, p.getLocation(), str("judgement.sound", "item.trident.thunder"), 1f, 1.2f);
        mgr.fireBolt(p, new WeaponManager.Bolt(num("judgement.range", 35), num("judgement.speed", 2.5), num("judgement.damage", 9),
                        true, num("judgement.falloff", 0.25), num("judgement.hit-radius", 1.0), "end_rod", "wax_on",
                        "wax_off", "item.trident.return"),
                (target, damage) -> bonus(p, target, damage),
                target -> mgr.mark(target, p, num("precision.mark-seconds", 4)));
    }

    /** Holy Precision also applies to normal hits with the trident. */
    @Override
    public void beforeHit(Player attacker, LivingEntity victim, EntityDamageByEntityEvent event) {
        if (mgr.markedBy(victim, attacker)) {
            event.setDamage(event.getDamage() * (1 + num("precision.bonus", 0.3)));
            Fx.particle(plugin, victim.getLocation().add(0, 1.5, 0), "wax_on", 8, 0.3);
        }
    }
}
