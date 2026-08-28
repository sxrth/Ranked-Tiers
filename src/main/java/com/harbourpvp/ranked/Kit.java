package com.harbourpvp.ranked;

import org.bukkit.Material;

public enum Kit {
    Sword, Axe, Mace, Pot, NethPot, UHC, SMP, Vanilla;

    public static Kit from(String value) {
        for (Kit kit : values()) if (kit.name().equalsIgnoreCase(value)) return kit;
        return null;
    }

    public Material icon(HarbourPVP plugin) {
        String raw = plugin.getConfig().getString("kits." + name() + ".icon", "DIAMOND_SWORD");
        try { return Material.valueOf(raw.toUpperCase()); }
        catch (IllegalArgumentException ex) { return Material.DIAMOND_SWORD; }
    }
}
