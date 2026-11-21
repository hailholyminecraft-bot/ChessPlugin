package com.chesssmp.commands;

import com.chesssmp.ChessSMP;
import com.chesssmp.Messages;
import com.chesssmp.storage.PlayerData;
import com.chesssmp.weapons.LegendaryWeapon;
import com.chesssmp.weapons.PromotionMenu;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** /legendary [give|info|reload|cooldowns|promote] */
public final class LegendaryCommand implements CommandExecutor, TabCompleter {

    private final ChessSMP plugin;

    public LegendaryCommand(ChessSMP plugin) {
        this.plugin = plugin;
    }

    private boolean can(CommandSender sender, String permission) {
        if (sender.hasPermission(permission) || sender.hasPermission("chesssmp.legendary.admin")) {
            return true;
        }
        plugin.messages().send(sender, "no-permission");
        return false;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "list" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "give" -> give(sender, args);
            case "info" -> info(sender, args);
            case "reload" -> {
                if (can(sender, "chesssmp.legendary.admin")) {
                    plugin.reloadAll();
                    plugin.messages().send(sender, "reload-done");
                }
            }
            case "cooldowns" -> cooldowns(sender);
            case "promote" -> promote(sender);
            default -> list(sender);
        }
        return true;
    }

    private void list(CommandSender sender) {
        if (!can(sender, "chesssmp.legendary.use")) {
            return;
        }
        sender.sendMessage(plugin.messages().component("legendary-header"));
        for (LegendaryWeapon w : plugin.weapons().all()) {
            Component line = plugin.messages().mm(w.rawName())
                    .append(Component.text("  " + w.typeName() + " \u2022 " + w.playstyle(), NamedTextColor.GRAY))
                    .clickEvent(ClickEvent.runCommand("/legendary info " + w.id()))
                    .hoverEvent(HoverEvent.showText(Component.text("Click for details")));
            sender.sendMessage(line);
        }
        sender.sendMessage(plugin.messages().component("legendary-footer"));
    }

    private void info(CommandSender sender, String[] args) {
        if (!can(sender, "chesssmp.legendary.use")) {
            return;
        }
        LegendaryWeapon w = args.length > 1 ? plugin.weapons().find(String.join(" ", Arrays.copyOfRange(args, 1, args.length))) : null;
        if (w == null) {
            plugin.messages().send(sender, "weapon-unknown", Messages.text("weapons", names()));
            return;
        }
        sender.sendMessage(plugin.messages().mm(w.rawName()));
        for (Component line : plugin.weapons().lore(w)) {
            sender.sendMessage(line);
        }
    }

    private String names() {
        List<String> ids = new ArrayList<>();
        for (LegendaryWeapon w : plugin.weapons().all()) {
            ids.add(w.id());
        }
        return String.join(", ", ids);
    }

    private void give(CommandSender sender, String[] args) {
        if (!can(sender, "chesssmp.legendary.give")) {
            return;
        }
        if (args.length < 3) {
            plugin.messages().send(sender, "usage", Messages.text("usage", "/legendary give <player> <weapon>"));
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            plugin.messages().send(sender, "player-not-found", Messages.text("player", args[1]));
            return;
        }
        LegendaryWeapon w = plugin.weapons().find(String.join(" ", Arrays.copyOfRange(args, 2, args.length)));
        if (w == null) {
            plugin.messages().send(sender, "weapon-unknown", Messages.text("weapons", names()));
            return;
        }
        ItemStack item = plugin.weapons().create(w.id());
        for (ItemStack leftover : target.getInventory().addItem(item).values()) {
            target.getWorld().dropItemNaturally(target.getLocation(), leftover);
        }
        plugin.messages().send(sender, "weapon-given", Messages.text("weapon", w.plainName()), Messages.text("player", target.getName()));
        if (!target.equals(sender)) {
            plugin.messages().send(target, "weapon-received", Messages.text("weapon", w.plainName()));
        }
    }

    private void cooldowns(CommandSender sender) {
        if (!(sender instanceof Player p)) {
            plugin.messages().send(sender, "player-only");
            return;
        }
        if (!can(sender, "chesssmp.legendary.use")) {
            return;
        }
        PlayerData d = plugin.data().of(p);
        long now = System.currentTimeMillis();
        boolean any = false;
        sender.sendMessage(plugin.messages().component("cooldowns-header"));
        for (Map.Entry<String, Long> e : d.cooldowns().entrySet()) {
            if (!e.getKey().startsWith("w_") || e.getValue() <= now) {
                continue;
            }
            for (LegendaryWeapon w : plugin.weapons().all()) {
                String prefix = "w_" + w.id() + "_";
                if (e.getKey().startsWith(prefix)) {
                    String ability = e.getKey().substring(prefix.length()).replace('-', ' ');
                    sender.sendMessage(plugin.messages().component("cooldowns-line", Messages.text("weapon", w.plainName()),
                            Messages.text("ability", ability), Messages.text("time", Messages.duration(e.getValue() - now))));
                    any = true;
                }
            }
        }
        if (!any) {
            sender.sendMessage(plugin.messages().component("cooldowns-empty"));
        }
    }

    private void promote(CommandSender sender) {
        if (!(sender instanceof Player p)) {
            plugin.messages().send(sender, "player-only");
            return;
        }
        PlayerData d = plugin.data().of(p);
        if (!d.promotionReady()) {
            plugin.messages().send(p, "promo-not-ready");
            return;
        }
        new PromotionMenu(plugin, p).open();
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            out.addAll(List.of("info", "cooldowns", "promote"));
            if (sender.hasPermission("chesssmp.legendary.give") || sender.hasPermission("chesssmp.legendary.admin")) {
                out.add("give");
            }
            if (sender.hasPermission("chesssmp.legendary.admin")) {
                out.add("reload");
            }
            out.removeIf(s -> !s.startsWith(args[0].toLowerCase(Locale.ROOT)));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                out.add(p.getName());
            }
            out.removeIf(s -> !s.toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT)));
        } else if ((args.length == 2 && args[0].equalsIgnoreCase("info")) || (args.length == 3 && args[0].equalsIgnoreCase("give"))) {
            for (LegendaryWeapon w : plugin.weapons().all()) {
                out.add(w.id());
            }
            out.removeIf(s -> !s.startsWith(args[args.length - 1].toLowerCase(Locale.ROOT)));
        }
        return out;
    }
}
