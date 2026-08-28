package com.harbourpvp.ranked;

import java.util.*;

public class PlayerData {
    private final UUID uuid;
    private String name;
    private final EnumMap<Kit, Integer> ratings = new EnumMap<>(Kit.class);
    private final List<String> history = new ArrayList<>();

    public PlayerData(UUID uuid, String name, int starting) {
        this.uuid = uuid; this.name = name;
        for (Kit kit : Kit.values()) ratings.put(kit, starting);
    }
    public UUID uuid() { return uuid; }
    public String name() { return name; }
    public void name(String n) { name = n; }
    public int rating(Kit kit) { return ratings.getOrDefault(kit, 1000); }
    public void rating(Kit kit, int value) { ratings.put(kit, value); }
    public List<String> history() { return history; }
}
