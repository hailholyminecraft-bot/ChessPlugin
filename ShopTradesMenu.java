package com.chesssmp.meteor;

import com.chesssmp.ChessSMP;
import com.chesssmp.gui.AdminMenu;
import com.chesssmp.gui.Items;
import com.chesssmp.gui.Menu;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Admin view of the villager's shop: click an item to turn it on or off. */
public final class ShopTradesMenu extends Menu {

    private final Map<Integer, String> bySlot = new HashMap<>();

    public ShopTradesMenu(ChessSMP plugin, Player viewer) {
        super(plugin, viewer, 6, Component.text("Villager trades"));
        render();
    }

    private void render() {
        inventory.clear();
        bySlot.clear();
        ConfigurationSection offers = plugin.getConfig().getConfigurationSection("meteor.shop.offers");
        int slot = 0;
        if (offers != null) {
            for (String id : offers.getKeys(false)) {
                ConfigurationSection s = offers.getConfigurationSection(id);
                if (s == null || slot >= 45) {
                    continue;
                }
                ItemStack icon = ShopMenu.buildReward(plugin, id, s);
                if (icon == null) {
                    continue;
                }
                boolean on = s.getBoolean("enabled", true);
                ItemMeta meta = icon.getItemMeta();
                List<Component> lore = new ArrayList<>();
                lore.add(Component.text(on ? "ON - players can buy this" : "OFF - hidden from the shop",
                        on ? NamedTextColor.GREEN : NamedTextColor.RED));
                lore.add(Component.text("Click to switch", NamedTextColor.DARK_GRAY));
                meta.lore(lore.stream().map(Items::noItalic).toList());
                icon.setItemMeta(meta);
                bySlot.put(slot, id);
                inventory.setItem(slot, on ? Items.glow(icon) : icon);
                slot++;
            }
        }
        inventory.setItem(49, Items.make(Material.ARROW, Component.text("Back", NamedTextColor.GRAY), List.of()));
    }

    @Override
    public void onClick(InventoryClickEvent event) {
        int slot = event.getSlot();
        if (slot == 49) {
            new AdminMenu(plugin, viewer).openLater();
            return;
        }
        String id = bySlot.get(slot);
        if (id == null) {
            return;
        }
        String path = "meteor.shop.offers." + id + ".enabled";
        plugin.getConfig().set(path, !plugin.getConfig().getBoolean(path, true));
        plugin.saveConfig();
        render();
    }
}
