package com.chesssmp.weapons;

import com.chesssmp.ChessSMP;
import com.chesssmp.Fx;
import com.chesssmp.Messages;
import com.chesssmp.storage.PlayerData;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Pawn's Sacrifice (axe): Sacrifice (health for damage), Promotion (kill reward) and Last Stand.
 * Five legitimate kills unlock the promotion into one of the four other weapons.
 */
public final class PawnsSacrifice extends LegendaryWeapon {

    private final Map<UUID, Long> empoweredUntil = new HashMap<>();
    private final Map<String, Long> lastKill = new HashMap<>();

    public PawnsSacrifice(ChessSMP plugin, WeaponManager mgr) {
        super(plugin, mgr);
    }

    @Override public String id() { return "pawns_sacrifice"; }
    @Override public String alias() { return "pawn"; }
    @Override public String defaultName() { return "<gradient:#8c8c8c:#ffffff><bold>\u265F Pawn's Sacrifice"; }
    @Override public Material defaultMaterial() { return Material.NETHERITE_AXE; }
    @Override public String typeName() { return "Netherite Axe"; }
    @Override public String playstyle() { return "Risk \u2022 Reward"; }

    @Override
    public List<String> abilityLore() {
        List<String> l = new ArrayList<>();
        l.addAll(ability("Sacrifice", "Right-click", "Pay " + fmt(num("sacrifice.health-cost", 6) / 2)
                + " hearts for +" + fmt(num("sacrifice.bonus-damage", 4)) + " damage on every hit for "
                + fmt(num("sacrifice.duration", 6)) + "s. Never kills you.", cooldown("sacrifice", 45)));
        l.addAll(ability("Promotion", "Passive", "Defeating a player grants Strength I for "
                + fmt(num("promotion.strength-seconds", 8)) + "s.", 0));
        l.addAll(ability("Last Stand", "Sneak + Right-click", "Below " + (int) (num("last-stand.health-threshold", 0.3) * 100)
                + "% health: Resistance for " + fmt(num("last-stand.duration", 5)) + "s.", cooldown("last-stand", 60)));
        l.add("<gold>Pawn Promotion");
        l.add("<gray>" + plugin.getConfig().getInt("promotion.kills-required", 5)
                + " player kills let you trade this axe for another legendary weapon.");
        l.add("");
        return l;
    }

    @Override
    public void onRightClick(Player p, boolean sneaking) {
        if (sneaking) {
            lastStand(p);
        } else {
            sacrifice(p);
        }
    }

    private void sacrifice(Player p) {
        if (!mgr.ready(p, this, "sacrifice", "Sacrifice")) {
            return;
        }
        double cost = num("sacrifice.health-cost", 6);
        if (empoweredUntil.getOrDefault(p.getUniqueId(), 0L) > System.currentTimeMillis()) {
            plugin.messages().actionBar(p, "weapon-sacrifice-active");
            return;
        }
        if (p.getHealth() <= cost + 1.0) { // would die or end up on half a heart
            plugin.messages().actionBar(p, "weapon-sacrifice-low");
            return;
        }
        mgr.start(p, this, "sacrifice", cooldown("sacrifice", 45));
        p.setHealth(p.getHealth() - cost);
        empoweredUntil.put(p.getUniqueId(), System.currentTimeMillis() + (long) (num("sacrifice.duration", 6) * 1000));
        burst(p.getLocation().add(0, 1, 0), "sacrifice", "crit", 35, "entity.wither.hurt", 1.2f);
        Fx.particle(plugin, p.getLocation().add(0, 1, 0), "wax_off", 20, 0.5);
    }

    private void lastStand(Player p) {
        if (!mgr.ready(p, this, "last-stand", "Last Stand")) {
            return;
        }
        double max = com.chesssmp.abilities.AbilityManager.maxHealth(p);
        if (p.getHealth() / max >= num("last-stand.health-threshold", 0.3)) {
            plugin.messages().actionBar(p, "weapon-last-stand-high");
            return;
        }
        mgr.start(p, this, "last-stand", cooldown("last-stand", 60));
        mgr.effect(p, PotionEffectType.RESISTANCE, (int) num("last-stand.resistance-level", 2), num("last-stand.duration", 5));
        burst(p.getLocation().add(0, 1, 0), "last-stand", "totem_of_undying", 40, "item.totem.use", 1f);
    }

    @Override
    public void beforeHit(Player attacker, LivingEntity victim, EntityDamageByEntityEvent event) {
        if (empoweredUntil.getOrDefault(attacker.getUniqueId(), 0L) > System.currentTimeMillis()) {
            event.setDamage(event.getDamage() + num("sacrifice.bonus-damage", 4));
            Fx.particle(plugin, victim.getLocation().add(0, 1, 0), "crit", 10, 0.3);
        }
    }

    /** Legitimate kills: another player, no teammate/trusted victim, and not the same victim again within the window. */
    @Override
    public void onKill(Player killer, Player victim) {
        if (!mgr.ownsPawn(killer) || killer.equals(victim)
                || plugin.data().of(killer).trusted().contains(victim.getUniqueId()) || plugin.events().sameSide(killer, victim)) {
            return;
        }
        long now = System.currentTimeMillis();
        String key = killer.getUniqueId() + ":" + victim.getUniqueId();
        Long last = lastKill.get(key);
        if (last != null && now - last < (long) (num("promotion.same-victim-seconds", 300) * 1000)) {
            return;
        }
        lastKill.put(key, now);
        if (lastKill.size() > 500) {
            lastKill.values().removeIf(t -> now - t > 3_600_000L);
        }

        if (mgr.held(killer) == this) {
            mgr.effect(killer, PotionEffectType.STRENGTH, 1, num("promotion.strength-seconds", 8));
            Fx.particle(plugin, killer.getLocation().add(0, 1, 0), "totem_of_undying", 30, 0.6);
            Fx.sound(plugin, killer.getLocation(), "entity.player.levelup", 1f, 1.5f);
        }

        PlayerData d = plugin.data().of(killer);
        if (d.promotionReady() || (d.promoted() != null && !plugin.getConfig().getBoolean("promotion.allow-repeat", false))) {
            return;
        }
        d.setPawnKills(d.pawnKills() + 1);
        plugin.data().markDirty();
        int required = Math.max(1, plugin.getConfig().getInt("promotion.kills-required", 5));
        if (d.pawnKills() >= required) {
            d.setPromotionReady(true);
            plugin.messages().send(killer, "promo-ready");
            killer.sendMessage(Component.text("  [Choose your promotion]", net.kyori.adventure.text.format.NamedTextColor.GOLD)
                    .clickEvent(ClickEvent.runCommand("/legendary promote"))
                    .hoverEvent(HoverEvent.showText(Component.text("Open the promotion menu"))));
            if (plugin.getConfig().getBoolean("promotion.auto-open", true)) {
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (killer.isOnline() && !killer.isDead()) {
                        new PromotionMenu(plugin, killer).open();
                    }
                }, 40L);
            }
        } else {
            plugin.messages().actionBar(killer, "promo-progress", Messages.num("kills", d.pawnKills()), Messages.num("required", required));
        }
    }

    @Override
    public void forget(UUID id) {
        empoweredUntil.remove(id);
    }
}
