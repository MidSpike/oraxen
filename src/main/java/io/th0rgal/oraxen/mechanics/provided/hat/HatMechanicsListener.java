package io.th0rgal.oraxen.mechanics.provided.hat;

import io.th0rgal.oraxen.items.OraxenItems;
import io.th0rgal.oraxen.mechanics.MechanicFactory;

import io.th0rgal.oraxen.utils.armorequipevent.ArmorEquipEvent;
import io.th0rgal.oraxen.utils.armorequipevent.ArmorType;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

public class HatMechanicsListener implements Listener {

    private final MechanicFactory factory;

    public HatMechanicsListener(MechanicFactory factory) {
        this.factory = factory;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onInventoryHatPut(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK)
            return;

        ItemStack item = event.getItem();
        String itemID = OraxenItems.getIdByItem(item);

        if (factory.isNotImplementedIn(itemID))
            return;

        PlayerInventory inventory = event.getPlayer().getInventory();
        if (inventory.getHelmet() == null) {
            inventory.setHelmet(item);
            item.setAmount(0);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void OnPlaceHatOnHelmetSlot(InventoryClickEvent e) {
        Inventory clickedInventory = e.getClickedInventory();
        ItemStack cursor = e.getCursor();

        if (clickedInventory == null
                || !clickedInventory.getType().equals(InventoryType.PLAYER)
                || e.getSlotType() != InventoryType.SlotType.ARMOR
                || e.getSlot() != 39
                || cursor == null)
            return;

        ItemStack clone = cursor.clone();
        String itemID = OraxenItems.getIdByItem(clone);
        ItemStack currentItem = e.getCurrentItem();

        if (factory.isNotImplementedIn(itemID))
            return;

        if (currentItem == null || currentItem.getType() == Material.AIR) {
            ArmorEquipEvent armorEquipEvent = new ArmorEquipEvent((Player) e.getWhoClicked(), ArmorEquipEvent.EquipMethod.ORAXEN_HAT, ArmorType.HELMET, currentItem, clone);
            Bukkit.getServer().getPluginManager().callEvent(armorEquipEvent);
            if (armorEquipEvent.isCancelled())
                return;

            e.setCancelled(true);
            e.getWhoClicked().getInventory().setHelmet(armorEquipEvent.getNewArmorPiece());
            cursor.setAmount(0);
        }
    }

}
