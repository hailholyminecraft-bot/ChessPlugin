package com.chesssmp.weapons;

import com.chesssmp.ChessSMP;
import com.chesssmp.gui.Items;
import com.chesssmp.gui.Menu;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** The Pawn Promotion menu: shows the four weapons with their abilities, click to promote. */
public final class PromotionMenu extends Menu {

    private static final int[] SLOTS = {10, 12, 14, 16};

    private final Map<Integer, String> bySlot = new HashMap<>();

    public PromotionMenu(ChessSMP plugin, Player viewer) {
        super(plugin, viewer, 3, Component.text("Pawn Promotion"));
        List<String> choices = plugin.weapons().promotionChoices();
        for (int i = 0; i < choices.size() && i < SLOTS.length; i++) {
            LegendaryWeapon w = plugin.weapons().get(choices.get(i));
            ItemStack item = plugin.weapons().create(w.id());
            ItemMeta meta = item.getItemMeta();
            List<Component> lore = new ArrayList<>(meta.lore() == null ? List.of() : meta.lore());
            lore.add(Component.empty());
            lore.add(Items.noItalic(Component.text("Click to promote your Pawn's Sacrifice", NamedTextColor.YELLOW)));
            meta.lore(lore);
            item.setItemMeta(meta);
            inventory.setItem(SLOTS[i], item);
            bySlot.put(SLOTS[i], w.id());
        }
        inventory.setItem(22, Items.make(Material.BARRIER, Component.text("Decide later", NamedTextColor.RED), List.of()));
    }

    @Override
    public void onClick(InventoryClickEvent event) {
        int slot = event.getSlot();
        if (slot == 22) {
            viewer.closeInventory();
            return;
        }
        String id = bySlot.get(slot);
        if (id != null) {
            viewer.closeInventory();
            plugin.weapons().promote(viewer, id);
        }
    }
}
