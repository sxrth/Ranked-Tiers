package com.harbourpvp.ranked;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import java.io.IOException;
import java.util.*;

public class DataStore {
    private final HarbourPVP plugin;
    private final File file;
    private final Map<UUID, PlayerData> players = new HashMap<>();

    public DataStore(HarbourPVP plugin) {
        this.plugin = plugin; this.file = new File(plugin.getDataFolder(), "data.yml");
        load();
    }
    public PlayerData get(UUID uuid, String name) {
        PlayerData p = players.computeIfAbsent(uuid, id -> new PlayerData(id, name, plugin.getConfig().getInt("starting-rating", 1000)));
        p.name(name); return p;
    }
    public Collection<PlayerData> all() { return players.values(); }
    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        for (PlayerData p : players.values()) {
            String base = "players." + p.uuid();
            y.set(base + ".name", p.name());
            for (Kit kit : Kit.values()) { y.set(base + ".ratings." + kit.name(), p.rating(kit)); y.set(base + ".placements." + kit.name(), p.placements(kit)); }
            y.set(base + ".history", p.history());
        }
        try { y.save(file); } catch (IOException e) { plugin.getLogger().severe("Could not save data.yml: " + e.getMessage()); }
    }
    private void load() {
        if (!file.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection sec = y.getConfigurationSection("players");
        if (sec == null) return;
        for (String key : sec.getKeys(false)) {
            try {
                UUID id = UUID.fromString(key);
                String name = y.getString("players." + key + ".name", "Unknown");
                PlayerData p = new PlayerData(id, name, plugin.getConfig().getInt("starting-rating", 1000));
                for (Kit kit : Kit.values()) { p.rating(kit, y.getInt("players." + key + ".ratings." + kit.name(), p.rating(kit))); p.placements(kit, y.getInt("players." + key + ".placements." + kit.name(), p.placements(kit))); }
                p.history().addAll(y.getStringList("players." + key + ".history"));
                players.put(id, p);
            } catch (IllegalArgumentException ignored) {}
        }
    }
}
