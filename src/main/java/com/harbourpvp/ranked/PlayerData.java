package com.harbourpvp.ranked;

import java.util.*;

public class PlayerData {
    private final UUID uuid;
    private String name;
    private final EnumMap<Kit, Integer> ratings = new EnumMap<>(Kit.class);
    private final List<String> history = new ArrayList<>();
    private final EnumMap<Kit, Integer> placements = new EnumMap<>(Kit.class);

    public PlayerData(UUID uuid, String name, int starting) {
        this.uuid = uuid; this.name = name;
        for (Kit kit : Kit.values()) { ratings.put(kit, starting); placements.put(kit, 0); }
    }
    public UUID uuid() { return uuid; }
    public String name() { return name; }
    public void name(String n) { name = n; }
    public int rating(Kit kit) { return ratings.getOrDefault(kit, 1000); }
    public void rating(Kit kit, int value) { ratings.put(kit, value); }
    public List<String> history() { return history; }
    public int placements(Kit kit) { return placements.getOrDefault(kit, 0); }
    public void placements(Kit kit, int value) { placements.put(kit, value); }
}
