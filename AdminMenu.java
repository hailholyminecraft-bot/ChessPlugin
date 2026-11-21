package com.chesssmp.gui;

import com.chesssmp.ChessSMP;
import com.chesssmp.meteor.ShopTradesMenu;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** /chess gui - toggle the villager shop and the most important settings without editing config.yml. */
public final class AdminMenu extends Menu {

    private enum Kind { TOGGLE, CYCLE, NUMBER }

    private record Entry(Kind kind, String path, String label, Material icon, String help,
                         List<String> values, int step, int min, int max) { }

    private static final List<Entry> ENTRIES = List.of(
            toggle("meteor.shop.enabled", "Glip Glop's shop", Material.VILLAGER_SPAWN_EGG, "Players can trade with the meteor villager."),
            toggle("meteor.auto-spawn.enabled", "Automatic meteors", Material.FIRE_CHARGE, "A meteor crashes now and then."),
            toggle("crafting.block-enabled", "Crafting restrictions", Material.CRAFTING_TABLE, "Blocks crafting of shields, maces, apples ..."),
            toggle("choose.enabled", "/chess choose", Material.ENDER_EYE, "Let players pick their own piece."),
            toggle("random-piece.on-respawn", "New piece on respawn", Material.TOTEM_OF_UNDYING, "Every respawn deals a random piece."),
            toggle("ability-items.enabled", "Chess Scepter", Material.BLAZE_ROD, "Use abilities with the scepter item."),
            toggle("nametag.enabled", "Name tags", Material.NAME_TAG, "Chess emoji in front of names."),
            toggle("event.auto-join-all", "Event: auto-join everybody", Material.PLAYER_HEAD, "Everyone online joins when an event starts."),
            toggle("event.allow-late-join", "Event: late joining", Material.OAK_DOOR, "Players may join a running event."),
            toggle("event.sidebar", "Event: sidebar", Material.OAK_SIGN, "Show the score on the sidebar."),
            toggle("combat.punish-logout", "Kill combat loggers", Material.IRON_SWORD, "Logging out in combat kills the player."),
            toggle("abilities.allow-concurrent", "Stack timed abilities", Material.POTION, "Allow several timed abilities at once."),
            toggle("particles.enabled", "Particles", Material.FIREWORK_STAR, "Ability and effect particles."),
            toggle("sounds.enabled", "Sounds", Material.NOTE_BLOCK, "Ability and effect sounds."),
            toggle("join-messages.enabled", "Join messages", Material.OAK_SIGN, "Chess themed join messages."),
            toggle("death-messages.enabled", "Death messages", Material.SKELETON_SKULL, "Chess themed kill messages."),
            cycle("event.respawn.mode", "Event respawn mode", Material.RED_BED, "INSTANT, DELAYED or ELIMINATION.", List.of("INSTANT", "DELAYED", "ELIMINATION")),
            cycle("event.balance-mode", "Event team balance", Material.BEACON, "STRENGTH or RANDOM.", List.of("STRENGTH", "RANDOM")),
            cycle("grandmaster-mace.recipient", "Mace winner", Material.MACE, "Who gets the Grandmaster Mace.", List.of("MVP", "ALL_WINNERS", "NONE")),
            number("event.score-limit", "Event score limit", Material.GOLD_INGOT, "Points needed to win.", 1, 1, 200),
            number("event.time-limit-minutes", "Event time limit (min)", Material.CLOCK, "Minutes before the event ends.", 5, 1, 240),
            number("event.join-period-seconds", "Event join period (s)", Material.HOPPER, "Seconds players can join.", 15, 5, 600),
            number("clock.cooldown-seconds", "Chess Clock cooldown (s)", Material.CLOCK, "Seconds between uses.", 5, 5, 600),
            number("grandmaster.freeze-cooldown-seconds", "Freeze cooldown (s)", Material.PACKED_ICE, "Seconds between freezing hits.", 15, 15, 900));

    private static Entry toggle(String path, String label, Material icon, String help) {
        return new Entry(Kind.TOGGLE, path, label, icon, help, List.of(), 0, 0, 0);
    }

    private static Entry cycle(String path, String label, Material icon, String help, List<String> values) {
        return new Entry(Kind.CYCLE, path, label, icon, help, values, 0, 0, 0);
    }

    private static Entry number(String path, String label, Material icon, String help, int step, int min, int max) {
        return new Entry(Kind.NUMBER, path, label, icon, help, List.of(), step, min, max);
    }

    private final Map<Integer, Entry> bySlot = new HashMap<>();

    public AdminMenu(ChessSMP plugin, Player viewer) {
        super(plugin, viewer, 6, Component.text("ChessSMP settings"));
        render();
    }

    private void render() {
        inventory.clear();
        bySlot.clear();
        for (int i = 0; i < ENTRIES.size() && i < 45; i++) {
            Entry e = ENTRIES.get(i);
            bySlot.put(i, e);
            inventory.setItem(i, display(e));
        }
        inventory.setItem(45, Items.make(Material.VILLAGER_SPAWN_EGG, Component.text("Villager trades", NamedTextColor.GREEN),
                List.of(Component.text("Turn single shop items on or off", NamedTextColor.GRAY))));
        inventory.setItem(46, Items.make(Material.FIRE_CHARGE, Component.text("Meteor here", NamedTextColor.GOLD),
                List.of(Component.text("Builds a meteor site where you stand", NamedTextColor.GRAY))));
        inventory.setItem(47, Items.make(Material.REDSTONE, Component.text("Reload config.yml", NamedTextColor.AQUA),
                List.of(Component.text("After editing the file by hand", NamedTextColor.GRAY))));
        inventory.setItem(49, Items.make(Material.BARRIER, Component.text("Close", NamedTextColor.RED), List.of()));
    }

    private ItemStack display(Entry e) {
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(e.help(), NamedTextColor.GRAY));
        lore.add(Component.empty());
        ItemStack item;
        switch (e.kind()) {
            case TOGGLE -> {
                boolean on = plugin.getConfig().getBoolean(e.path());
                lore.add(Component.text(on ? "ON" : "OFF", on ? NamedTextColor.GREEN : NamedTextColor.RED));
                lore.add(Component.text("Click to switch", NamedTextColor.DARK_GRAY));
                item = Items.make(e.icon(), Component.text(e.label(), on ? NamedTextColor.GREEN : NamedTextColor.RED), lore);
                if (on) {
                    item = Items.glow(item);
                }
            }
            case CYCLE -> {
                lore.add(Component.text("Now: " + plugin.getConfig().getString(e.path()), NamedTextColor.YELLOW));
                lore.add(Component.text("Click for the next value", NamedTextColor.DARK_GRAY));
                item = Items.make(e.icon(), Component.text(e.label(), NamedTextColor.GOLD), lore);
            }
            default -> {
                lore.add(Component.text("Now: " + plugin.getConfig().getInt(e.path()), NamedTextColor.YELLOW));
                lore.add(Component.text("Left click +" + e.step() + ", right click -" + e.step() + ", shift = x5", NamedTextColor.DARK_GRAY));
                item = Items.make(e.icon(), Component.text(e.label(), NamedTextColor.GOLD), lore);
            }
        }
        return item;
    }

    private void afterChange(String path) {
        if (path.equals("nametag.enabled")) {
            plugin.nameTags().reload();
        } else if (path.startsWith("meteor.auto-spawn")) {
            plugin.meteor().startAuto();
        }
    }

    @Override
    public void onClick(InventoryClickEvent event) {
        int slot = event.getSlot();
        switch (slot) {
            case 45 -> {
                new ShopTradesMenu(plugin, viewer).openLater();
                return;
            }
            case 46 -> {
                viewer.closeInventory();
                Location l = viewer.getLocation();
                plugin.meteor().spawn(viewer, l.getWorld(), l.getBlockX(), l.getBlockZ());
                return;
            }
            case 47 -> {
                plugin.reloadAll();
                plugin.messages().send(viewer, "reload-done");
                render();
                return;
            }
            case 49 -> {
                viewer.closeInventory();
                return;
            }
            default -> { }
        }
        Entry e = bySlot.get(slot);
        if (e == null) {
            return;
        }
        switch (e.kind()) {
            case TOGGLE -> plugin.getConfig().set(e.path(), !plugin.getConfig().getBoolean(e.path()));
            case CYCLE -> {
                String now = plugin.getConfig().getString(e.path(), e.values().get(0));
                int idx = e.values().indexOf(now.toUpperCase());
                plugin.getConfig().set(e.path(), e.values().get((idx + 1) % e.values().size()));
            }
            case NUMBER -> {
                int step = e.step() * (event.isShiftClick() ? 5 : 1);
                int value = plugin.getConfig().getInt(e.path()) + (event.isRightClick() ? -step : step);
                plugin.getConfig().set(e.path(), Math.max(e.min(), Math.min(e.max(), value)));
            }
        }
        plugin.saveConfig();
        afterChange(e.path());
        render();
    }
}
