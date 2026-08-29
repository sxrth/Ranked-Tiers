package com.harbourpvp.ranked;
import org.bukkit.Material;
public enum Kit {
    Sword, Axe, Mace, Pot, NethPot, SMP, UHC, Vanilla, Spear;
    public static Kit from(String value){ for(Kit k:values()) if(k.name().equalsIgnoreCase(value)) return k; return null; }
    public Material icon(HarbourPVP plugin){
        String raw=plugin.getConfig().getString("kits."+name()+".icon", "DIAMOND_SWORD");
        try{return Material.valueOf(raw.toUpperCase());}catch(Exception e){
            return switch(this){case Sword->Material.DIAMOND_SWORD;case Axe->Material.DIAMOND_AXE;case Mace->Material.MACE;case Pot->Material.POTION;case NethPot->Material.NETHERITE_SWORD;case SMP->Material.SHIELD;case UHC->Material.GOLDEN_APPLE;case Vanilla->Material.END_CRYSTAL;case Spear->Material.TRIDENT;};
        }
    }
}
