package dev.luis.asyncmotd.gui;

import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.InventoryHolder;

public interface Menu extends InventoryHolder {

    void click(InventoryClickEvent event);
}
