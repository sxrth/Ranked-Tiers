package com.harbourpvp.ranked;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.configuration.serialization.ConfigurationSerialization;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.scoreboard.*;
import org.bukkit.boss.*;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import java.util.*;

public class HarbourPVP extends org.bukkit.plugin.java.JavaPlugin implements Listener, CommandExecutor, TabCompleter {
    private DataStore store;
    private final EnumMap<Kit, Deque<UUID>> queues = new EnumMap<>(Kit.class);
    private final List<Match> matches = new ArrayList<>();
    private final Map<UUID, Location> returnLocations = new HashMap<>();
    private static final String GUI_TITLE = "§8HarbourPVP §7| §6Ranked Kits";
    private static final String EDITOR_PREFIX = "§8HarbourPVP §7| §eKit Editor: ";
    private static final String LOBBY_TITLE = "§8HarbourPVP §7| §6Lobby";
    private final Map<UUID, UUID> partyOwner = new HashMap<>();
    private final Map<UUID, Set<UUID>> parties = new HashMap<>();
    private final Map<UUID, Kit> selectedKit = new HashMap<>();
    private final Map<UUID, String> activeArena = new HashMap<>();

    @Override public void onEnable() {
        saveDefaultConfig(); store = new DataStore(this);
        for (Kit kit : Kit.values()) queues.put(kit, new ArrayDeque<>());
        getServer().getPluginManager().registerEvents(this, this);
        for (String cmd : List.of("play","stats","leaderboard","history","queue","party","ht")) {
            Objects.requireNonNull(getCommand(cmd)).setExecutor(this); Objects.requireNonNull(getCommand(cmd)).setTabCompleter(this);
        }
        getServer().getScheduler().runTaskTimer(this, store::save, 20L * 60L, 20L * 60L);
        getLogger().info("HarbourPVP Ranked enabled.");
    }
    @Override public void onDisable() { store.save(); }

    @Override public boolean onCommand(CommandSender s, Command c, String label, String[] a) {
        if (c.getName().equalsIgnoreCase("play")) return play(s,a);
        if (c.getName().equalsIgnoreCase("stats")) return stats(s,a);
        if (c.getName().equalsIgnoreCase("leaderboard")) return leaderboard(s,a);
        if (c.getName().equalsIgnoreCase("history")) return history(s,a);
        if (c.getName().equalsIgnoreCase("queue")) return queue(s);
        if (c.getName().equalsIgnoreCase("party")) return party(s,a);
        if (c.getName().equalsIgnoreCase("ht")) return admin(s,a);
        return false;
    }
    private void giveLobbyItems(Player p){
        p.getInventory().clear();
        ItemStack queue=new ItemStack(Material.DIAMOND_SWORD); ItemMeta qm=queue.getItemMeta(); qm.setDisplayName("§b§lSıraya Gir"); qm.setLore(List.of("§7Ranked kit seçmek için tıkla")); queue.setItemMeta(qm);
        ItemStack party=new ItemStack(Material.CHEST); ItemMeta pm=party.getItemMeta(); pm.setDisplayName("§6§lParti Aç"); pm.setLore(List.of("§7/party create ile parti oluştur")); party.setItemMeta(pm);
        ItemStack stats=new ItemStack(Material.PAPER); ItemMeta sm=stats.getItemMeta(); sm.setDisplayName("§e§lİstatistikler"); sm.setLore(List.of("§7Ranked istatistiklerini görüntüle")); stats.setItemMeta(sm);
        ItemStack book=new ItemStack(Material.BOOK); ItemMeta bm=book.getItemMeta(); bm.setDisplayName("§d§lKit Düzenleme"); bm.setLore(List.of("§7Kişisel kitlerini düzenle")); book.setItemMeta(bm);
        p.getInventory().setItem(0,queue); p.getInventory().setItem(1,party); p.getInventory().setItem(4,stats); p.getInventory().setItem(8,book);
        p.getInventory().setHeldItemSlot(0);
    }
    private void openKitEditorSelector(Player p){
        Inventory inv=Bukkit.createInventory(null,27,"§8HarbourPVP §7| §dKit Düzenleme");
        int[] slots={10,11,12,13,14,15,16,22,26};
        for(int i=0;i<Kit.values().length;i++){ Kit k=Kit.values()[i]; ItemStack it=new ItemStack(k.icon(this)); ItemMeta m=it.getItemMeta(); m.setDisplayName("§d§l"+k.name()); m.setLore(List.of("§7Kit loadoutunu düzenle","§eTıkla → düzenle")); it.setItemMeta(m); inv.setItem(slots[i],it); }
        p.openInventory(inv);
    }
    @EventHandler public void join(org.bukkit.event.player.PlayerJoinEvent e){ selectedKit.put(e.getPlayer().getUniqueId(),Kit.Sword);
        Bukkit.getScheduler().runTask(this,()->giveLobbyItems(e.getPlayer()));
        updateTag(e.getPlayer().getUniqueId()); }

    private boolean play(CommandSender s, String[] a) {
        if (!(s instanceof Player p)) { s.sendMessage("Players only."); return true; }
        if (a.length == 0) { openKitGui(p); return true; }
        Kit kit = Kit.from(a[0]);
        if (kit == null) { p.sendMessage("§cUnknown kit. Use /play to open the GUI."); return true; }
        toggleQueue(p, kit);
        return true;
    }

    private void openKitGui(Player p) {
        Inventory inv = Bukkit.createInventory(null, 27, GUI_TITLE);
        Kit[] kits = Kit.values();
        int[] slots = {10,11,12,13,14,15,16,20,22};
        for (int i=0; i<kits.length; i++) {
            Kit k = kits[i];
            ItemStack item = new ItemStack(k.icon(this));
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName("§6§l" + k.name());
            PlayerData d = store.get(p.getUniqueId(), p.getName());
            int rating = d.rating(k);
            int waiting = queues.get(k).size();
            List<String> lore = new ArrayList<>();
            lore.add("§7Rating: §e" + rating);
            lore.add("§7Tier: §d§l" + tier(rating));
            lore.add("§7Sırada: §a" + waiting + " oyuncu");
            lore.add("");
            lore.add(queues.get(k).contains(p.getUniqueId()) ? "§cTıkla: Sıradan çık" : "§aTıkla: Sıraya gir");
            lore.add(d.placements(k) < 5 ? "§cUnranked §7• Placement: §f"+d.placements(k)+"/5" : "§aRanked");
            meta.setLore(lore);
            item.setItemMeta(meta);
            inv.setItem(slots[i], item);
        }
        ItemStack close = new ItemStack(Material.BARRIER);
        ItemMeta cm = close.getItemMeta(); cm.setDisplayName("§cKapat"); close.setItemMeta(cm); inv.setItem(26, close);
        p.openInventory(inv);
    }

    private void toggleQueue(Player p, Kit kit) {
        selectedKit.put(p.getUniqueId(), kit); updateTag(p.getUniqueId());
        if (matches.stream().anyMatch(m -> m.contains(p.getUniqueId()))) { p.sendMessage("§cZaten bir maçtasın."); return; }
        Deque<UUID> q = queues.get(kit);
        if (q.contains(p.getUniqueId())) { q.remove(p.getUniqueId()); p.sendMessage("§c"+kit+" §7sırasından çıktın."); return; }
        UUID opponent = null;
        for (UUID id : q) if (!id.equals(p.getUniqueId()) && Bukkit.getPlayer(id) != null && !matches.stream().anyMatch(m -> m.contains(id))) { opponent=id; break; }
        if (opponent == null) { q.addLast(p.getUniqueId()); p.sendMessage("§a"+kit+" §7sırasına girdin. Rakip bekleniyor..."); }
        else { q.remove(opponent); startMatch(kit, p.getUniqueId(), opponent); }
    }

    @EventHandler public void interactLobby(org.bukkit.event.player.PlayerInteractEvent e){
        Player p=e.getPlayer(); if(find(p.getUniqueId())!=null)return; ItemStack it=e.getItem(); if(it==null||!it.hasItemMeta())return; String n=ChatColor.stripColor(it.getItemMeta().getDisplayName());
        if(n.equals("Sıraya Gir")){e.setCancelled(true);openKitGui(p);} else if(n.equals("Parti Aç")){e.setCancelled(true);party(p,new String[]{"gui"});} else if(n.equals("İstatistikler")){e.setCancelled(true);stats(p,new String[0]);} else if(n.equals("Kit Düzenleme")){e.setCancelled(true);openKitEditorSelector(p);}
    }
    @EventHandler public void dropLobby(org.bukkit.event.player.PlayerDropItemEvent e){ if(find(e.getPlayer().getUniqueId())==null) e.setCancelled(true); }

    @EventHandler public void inventoryClick(InventoryClickEvent e) {
        String title=e.getView().getTitle();
        if (LOBBY_TITLE.equals(title)){
            e.setCancelled(true); if(!(e.getWhoClicked() instanceof Player p)||e.getCurrentItem()==null)return;
            int slot=e.getRawSlot();
            if(slot==0) openKitGui(p);
            else if(slot==1) party(p,new String[]{"gui"});
            else if(slot==4) stats(p,new String[0]);
            else if(slot==8) openKitEditorSelector(p);
            return;
        }
        if ("§8HarbourPVP §7| §dKit Düzenleme".equals(title)){
            e.setCancelled(true); if(!(e.getWhoClicked() instanceof Player p)||e.getCurrentItem()==null)return;
            ItemMeta m=e.getCurrentItem().getItemMeta(); if(m==null)return; Kit k=Kit.from(ChatColor.stripColor(m.getDisplayName())); if(k!=null) openKitEditor(p,k); return;
        }
        if ("§8HarbourPVP §7| §6Parti".equals(title)) {
            e.setCancelled(true);
            if (!(e.getWhoClicked() instanceof Player p) || e.getCurrentItem()==null) return;
            int slot=e.getRawSlot();
            UUID owner=partyOwner.getOrDefault(p.getUniqueId(),p.getUniqueId());
            if(slot==10){ p.closeInventory(); p.sendMessage("§eOyuncu davet etmek için: §f/party invite <oyuncu>"); }
            else if(slot==16){ party(p,new String[]{"leave"}); p.closeInventory(); }
            else if(slot==22){ party(p,new String[]{"disband"}); }
            else if(slot==26){ p.closeInventory(); }
            return;
        }
        if (!GUI_TITLE.equals(title)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p) || e.getCurrentItem() == null) return;
        ItemStack item = e.getCurrentItem();
        if (item.getType() == Material.BARRIER) { p.closeInventory(); return; }
        ItemMeta meta = item.getItemMeta();
        if (meta == null || meta.getDisplayName() == null) return;
        String name = ChatColor.stripColor(meta.getDisplayName()).replace("Ranked", "").trim();
        Kit k = Kit.from(name);
        if (k != null) { toggleQueue(p, k); Bukkit.getScheduler().runTask(this, () -> openKitGui(p)); }
    }

    private String firstReadyArena(){
        if(!getConfig().isConfigurationSection("arenas")) return null;
        for(String n:getConfig().getConfigurationSection("arenas").getKeys(false)){
            if(location("arenas."+n+".spawn1")!=null && location("arenas."+n+".spawn2")!=null && location("arenas."+n+".pos1")!=null && location("arenas."+n+".pos2")!=null) return n;
        }
        return null;
    }
    private boolean insideArena(Player p, String name){
        Location x=p.getLocation(), a=location("arenas."+name+".pos1"), b=location("arenas."+name+".pos2");
        if(a==null||b==null||x.getWorld()==null||a.getWorld()==null||!x.getWorld().equals(a.getWorld())) return false;
        double minX=Math.min(a.getX(),b.getX()), maxX=Math.max(a.getX(),b.getX());
        double minY=Math.min(a.getY(),b.getY()), maxY=Math.max(a.getY(),b.getY());
        double minZ=Math.min(a.getZ(),b.getZ()), maxZ=Math.max(a.getZ(),b.getZ());
        return x.getX()>=minX&&x.getX()<=maxX&&x.getY()>=minY&&x.getY()<=maxY&&x.getZ()>=minZ&&x.getZ()<=maxZ;
    }
    @EventHandler public void arenaMove(PlayerMoveEvent e){
        Player p=e.getPlayer(); String n=activeArena.get(p.getUniqueId()); if(n==null) return;
        if(e.getTo()==null) return;
        if(!insideArena(p,n)){ Match mm=find(p.getUniqueId()); String slot=(mm!=null && mm.one().equals(p.getUniqueId()))?"1":"2"; Location s=location("arenas."+n+".spawn"+slot); if(s!=null) e.setTo(s); }
    }

    private Location firstArenaSpawn(int slot) {
        if (getConfig().isConfigurationSection("arenas")) {
            for (String name : getConfig().getConfigurationSection("arenas").getKeys(false)) {
                Location l = location("arenas." + name + ".spawn" + slot);
                if (l != null) return l;
            }
        }
        return null;
    }

    private void saveLocation(String path, Location l) {
        getConfig().set(path, l.getWorld().getName()+","+l.getX()+","+l.getY()+","+l.getZ()+","+l.getYaw()+","+l.getPitch());
        saveConfig();
    }

    private boolean openPartyGui(Player p) {
        UUID owner = partyOwner.getOrDefault(p.getUniqueId(), p.getUniqueId());
        if (!parties.containsKey(owner)) {
            parties.put(owner, new LinkedHashSet<>(Set.of(p.getUniqueId())));
            partyOwner.put(p.getUniqueId(), owner);
        }
        Inventory inv = Bukkit.createInventory(null, 27, "§8HarbourPVP §7| §6Parti");
        inv.setItem(10, item(Material.EMERALD, "§a§lOyuncu Davet Et", "§7Çevrim içi oyunculardan davet et"));
        inv.setItem(13, item(Material.PLAYER_HEAD, "§e§lParti Üyeleri", "§7Üye sayısı: §f"+parties.get(owner).size()));
        inv.setItem(16, item(Material.IRON_DOOR, "§c§lPartiden Ayrıl", "§7Partiden çık"));
        inv.setItem(22, item(Material.BARRIER, "§c§lPartiyi Dağıt", "§7Sadece parti lideri"));
        inv.setItem(26, item(Material.BOOK, "§7§lKapat", "§7Menüyü kapat"));
        int slot=0;
        for(UUID id: parties.get(owner)) {
            if(slot>=9) break;
            Player member=Bukkit.getPlayer(id);
            inv.setItem(slot++, item(Material.PLAYER_HEAD, "§f"+(member==null?id.toString():member.getName()), id.equals(owner)?"§eLider":"§7Üye"));
        }
        p.openInventory(inv);
        return true;
    }

    private ItemStack item(Material material, String name, String... lore) {
        ItemStack i = new ItemStack(material);
        ItemMeta m = i.getItemMeta();
        m.setDisplayName(name);
        m.setLore(Arrays.asList(lore));
        i.setItemMeta(m);
        return i;
    }

    private void startMatch(Kit kit, UUID one, UUID two) {
        Player a=Bukkit.getPlayer(one), b=Bukkit.getPlayer(two); if(a==null||b==null)return;
        if (returnLocations.put(one,a.getLocation())==null) returnLocations.put(two,b.getLocation()); else returnLocations.put(two,b.getLocation());
        String arenaName = firstReadyArena();
        Location l1=arenaName==null?null:location("arenas."+arenaName+".spawn1"), l2=arenaName==null?null:location("arenas."+arenaName+".spawn2");
        if(l1==null||l2==null){a.sendMessage("§cArena ayarlanmadı. Admin: /ht arena create <isim>, spawn1/2 ve pos1/pos2 ayarla.");b.sendMessage("§cArena ayarlanmadı. Admin: /ht arena create <isim>, spawn1/2 ve pos1/pos2 ayarla.");return;}
        prepare(a,kit); prepare(b,kit); a.teleport(l1); b.teleport(l2); activeArena.put(one,arenaName); activeArena.put(two,arenaName); matches.add(new Match(kit,one,two));
        a.setInvulnerable(true); b.setInvulnerable(true);
        for(int sec=3;sec>=1;sec--){ final int n=sec; Bukkit.getScheduler().runTaskLater(this,()->{ if(a.isOnline()) {a.sendTitle("§6"+n,"§7Get ready!",0,20,0); a.spigot().sendMessage(ChatMessageType.ACTION_BAR,new TextComponent("§e§l"+n));} if(b.isOnline()){b.sendTitle("§6"+n,"§7Get ready!",0,20,0); b.spigot().sendMessage(ChatMessageType.ACTION_BAR,new TextComponent("§e§l"+n));}},(3-sec)*20L); }
        Bukkit.getScheduler().runTaskLater(this,()->{ if(a.isOnline()) {a.setInvulnerable(false); a.sendTitle("§a§lFIGHT!","§7Good luck",0,20,10);} if(b.isOnline()){b.setInvulnerable(false); b.sendTitle("§a§lFIGHT!","§7Good luck",0,20,10);}},60L);
    }
    private void prepare(Player p, Kit kit){
        p.setHealth(p.getMaxHealth()); p.setFoodLevel(20); p.setSaturation(20);
        p.getInventory().clear();
        if (!loadSavedKit(p, kit)) applyDefaultKit(p, kit);
    }

    private void applyDefaultKit(Player p, Kit kit){
        switch(kit){
            case Sword, Vanilla -> {p.getInventory().addItem(new ItemStack(Material.NETHERITE_SWORD));p.getInventory().addItem(new ItemStack(Material.SHIELD));p.getInventory().addItem(new ItemStack(Material.GOLDEN_APPLE,4));}
            case Axe -> {p.getInventory().addItem(new ItemStack(Material.NETHERITE_AXE));p.getInventory().addItem(new ItemStack(Material.NETHERITE_SWORD));p.getInventory().addItem(new ItemStack(Material.GOLDEN_APPLE,4));}
            case Mace -> {p.getInventory().addItem(new ItemStack(Material.MACE));p.getInventory().addItem(new ItemStack(Material.ENDER_PEARL,4));p.getInventory().addItem(new ItemStack(Material.GOLDEN_APPLE,4));}
            case Pot, NethPot -> {p.getInventory().addItem(new ItemStack(Material.NETHERITE_SWORD));p.getInventory().addItem(new ItemStack(Material.SPLASH_POTION,16));p.getInventory().addItem(new ItemStack(Material.ENDER_PEARL,4));}
            case UHC -> {p.getInventory().addItem(new ItemStack(Material.NETHERITE_SWORD));p.getInventory().addItem(new ItemStack(Material.BOW));p.getInventory().addItem(new ItemStack(Material.ARROW,32));p.getInventory().addItem(new ItemStack(Material.GOLDEN_APPLE,8));}
            case SMP, Spear -> {p.getInventory().addItem(new ItemStack(Material.NETHERITE_SWORD));p.getInventory().addItem(new ItemStack(Material.ENDER_PEARL,8));p.getInventory().addItem(new ItemStack(Material.GOLDEN_APPLE,8));}
        }
    }

    private boolean loadSavedKit(Player p, Kit kit){
        String path="kits."+kit.name()+".items";
        if(!getConfig().isList(path)) return false;
        List<?> raw=getConfig().getList(path);
        if(raw==null || raw.isEmpty()) return false;
        PlayerInventory inv=p.getInventory();
        inv.clear();
        for(int i=0;i<Math.min(36,raw.size());i++){
            Object o=raw.get(i);
            if(o instanceof Map<?,?> map){
                try { inv.setItem(i, ItemStack.deserialize((Map<String,Object>) map)); } catch(Exception ignored) {}
            }
        }
        if(raw.size()>36){ inv.setHelmet(deserializeItem(raw.get(36))); }
        if(raw.size()>37){ inv.setChestplate(deserializeItem(raw.get(37))); }
        if(raw.size()>38){ inv.setLeggings(deserializeItem(raw.get(38))); }
        if(raw.size()>39){ inv.setBoots(deserializeItem(raw.get(39))); }
        if(raw.size()>40){ inv.setItemInOffHand(deserializeItem(raw.get(40))); }
        return true;
    }

    @SuppressWarnings("unchecked")
    private ItemStack deserializeItem(Object o){
        if(!(o instanceof Map<?,?> map)) return null;
        try { return ItemStack.deserialize((Map<String,Object>) map); } catch(Exception e){ return null; }
    }

    private void openKitEditor(Player p, Kit kit){
        Inventory inv=Bukkit.createInventory(null,45,EDITOR_PREFIX+kit.name());
        String path="kits."+kit.name()+".items";
        List<?> raw=getConfig().getList(path);
        if(raw!=null){
            for(int i=0;i<Math.min(41,raw.size());i++){
                ItemStack item=deserializeItem(raw.get(i));
                if(item!=null) inv.setItem(i,item);
            }
        } else {
            p.sendMessage("§7Bu kitin kayıtlı loadout'u yok. §e/ht kit save "+kit.name()+"§7 ile mevcut envanterini kaydedebilirsin.");
        }
        ItemStack info=new ItemStack(Material.KNOWLEDGE_BOOK);
        ItemMeta meta=info.getItemMeta(); meta.setDisplayName("§e§lKit Editor");
        meta.setLore(List.of("§7Kit: §f"+kit.name(),"§7Slot 0-35: ana envanter","§7Slot 36-39: zırh","§7Slot 40: offhand","","§a/ht kit save "+kit.name(),"§c/ht kit clear "+kit.name()));
        info.setItemMeta(meta); inv.setItem(44,info);
        p.openInventory(inv);
    }

    private boolean saveKitFromInventory(Player p, Kit kit){
        PlayerInventory inv=p.getInventory();
        List<Map<String,Object>> items=new ArrayList<>();
        for(int i=0;i<36;i++){
            ItemStack item=inv.getItem(i);
            items.add(item==null?null:item.serialize());
        }
        items.add(inv.getHelmet()==null?null:inv.getHelmet().serialize());
        items.add(inv.getChestplate()==null?null:inv.getChestplate().serialize());
        items.add(inv.getLeggings()==null?null:inv.getLeggings().serialize());
        items.add(inv.getBoots()==null?null:inv.getBoots().serialize());
        items.add(inv.getItemInOffHand()==null?null:inv.getItemInOffHand().serialize());
        getConfig().set("kits."+kit.name()+".items",items);
        saveConfig();
        p.sendMessage("§a✓ "+kit.name()+" kit loadout'u mevcut envanterinden kaydedildi.");
        p.sendMessage("§7Komut: §e/ht kit save "+kit.name());
        return true;
    }

    private boolean clearKit(Kit kit, CommandSender s){
        getConfig().set("kits."+kit.name()+".items",null); saveConfig(); s.sendMessage("§a✓ "+kit.name()+" kayıtlı loadout'u silindi. Varsayılan kit kullanılacak."); return true;
    }

    @EventHandler public void blockPlace(BlockPlaceEvent e) {
        Match m=find(e.getPlayer().getUniqueId());
        if(m!=null) e.setCancelled(!getConfig().getBoolean("kits."+m.kit().name()+".allow-block-place", false));
    }

    @EventHandler public void blockBreak(BlockBreakEvent e) {
        Match m=find(e.getPlayer().getUniqueId());
        if(m!=null) e.setCancelled(!getConfig().getBoolean("kits."+m.kit().name()+".allow-block-break", false));
    }

    @EventHandler public void death(PlayerDeathEvent e){ Player loser=e.getEntity(); Match m=find(loser.getUniqueId()); if(m!=null) Bukkit.getScheduler().runTask(this,()->finish(m,m.opponent(loser.getUniqueId()))); }
    @EventHandler public void quit(PlayerQuitEvent e){ UUID id=e.getPlayer().getUniqueId(); queues.values().forEach(q->q.remove(id)); Match m=find(id); if(m!=null) Bukkit.getScheduler().runTask(this,()->finish(m,m.opponent(id))); }
    private void finish(Match m, UUID winner){ if(!matches.remove(m))return; activeArena.remove(m.one()); activeArena.remove(m.two()); Player w=Bukkit.getPlayer(winner), loser=Bukkit.getPlayer(m.opponent(winner));
        PlayerData wp=store.get(winner,w==null?"Unknown":w.getName()), lp=store.get(m.opponent(winner),loser==null?"Unknown":loser.getName());
        int win=wp.rating(m.kit()), lose=lp.rating(m.kit()); int wr=getConfig().getInt("win-rating",25), lr=getConfig().getInt("loss-rating",20);
        int newW=win+wr,newL=Math.max(getConfig().getInt("min-rating",0),lose-lr); wp.rating(m.kit(),newW); lp.rating(m.kit(),newL); wp.placements(m.kit(),Math.min(5,wp.placements(m.kit())+1)); lp.placements(m.kit(),Math.min(5,lp.placements(m.kit())+1));
        String stamp=Long.toString(System.currentTimeMillis()); wp.history().add(0,stamp+"|"+m.kit()+"|WIN|"+newW+"|"+lp.name()); lp.history().add(0,stamp+"|"+m.kit()+"|LOSS|"+newL+"|"+wp.name()); trim(wp);trim(lp); store.save();
        String oldWTier=displayTier(wp,m.kit()), oldLTier=displayTier(lp,m.kit()), newWTier=displayTier(wp,m.kit()), newLTier=displayTier(lp,m.kit());
        if(w!=null){ String msg="§a§lVICTORY §8• §e"+m.kit()+" §8• §a+"+wr+" Rating §8• §d"+newWTier+" §7("+newW+")"; w.sendMessage(msg); w.sendTitle("§a§lVICTORY!","§e"+m.kit()+" §7• §a+"+wr+" Rating",5,35,10); action(w,msg); if(!oldWTier.equals(newWTier)) w.sendMessage("§d§lTIER UP! §7→ §e"+newWTier); returnPlayer(w);}
        if(loser!=null){ String msg="§c§lDEFEAT §8• §e"+m.kit()+" §8• §c-"+lr+" Rating §8• §d"+newLTier+" §7("+newL+")"; loser.sendMessage(msg); loser.sendTitle("§c§lDEFEAT","§e"+m.kit()+" §7• §c-"+lr+" Rating",5,35,10); action(loser,msg); if(!oldLTier.equals(newLTier)) loser.sendMessage("§c§lTIER CHANGE §7→ §e"+newLTier); returnPlayer(loser);}
        updateTag(winner); updateTag(m.opponent(winner));
    }
    private void action(Player p,String msg){ p.spigot().sendMessage(ChatMessageType.ACTION_BAR,new TextComponent(msg)); }
    private void trim(PlayerData p){while(p.history().size()>20)p.history().remove(p.history().size()-1);}
    private void returnPlayer(Player p){Location l=returnLocations.remove(p.getUniqueId()); if(l!=null)p.teleport(l); p.getInventory().clear(); updateTag(p.getUniqueId());}
    private Match find(UUID id){return matches.stream().filter(m->m.contains(id)).findFirst().orElse(null);}
    private boolean stats(CommandSender s,String[] a){Player p=a.length==0&&s instanceof Player?(Player)s:null; if(a.length>0)p=Bukkit.getPlayerExact(a[0]); if(p==null){s.sendMessage("§cPlayer not found.");return true;} PlayerData d=store.get(p.getUniqueId(),p.getName()); s.sendMessage("§6§lHarbourPVP §8» §f"+d.name()); for(Kit k:Kit.values())s.sendMessage("§e"+k+" §7» §f"+d.rating(k)+" §8» §e"+displayTier(d,k)); return true;}
    private boolean leaderboard(CommandSender s,String[] a){Kit k=a.length>0?Kit.from(a[0]):Kit.Sword;if(k==null){s.sendMessage("§cUnknown kit.");return true;}List<PlayerData> list=new ArrayList<>(store.all());list.sort((x,y)->Integer.compare(y.rating(k),x.rating(k)));s.sendMessage("§6§l"+k+" Leaderboard");int i=1;for(PlayerData p:list){if(i>10)break;s.sendMessage("§e#"+i+" §f"+p.name()+" §7» §a"+p.rating(k)+" §8("+displayTier(p,k)+")");i++;}return true;}
    private boolean history(CommandSender s,String[] a){if(!(s instanceof Player p)){s.sendMessage("Players only.");return true;}PlayerData d=store.get(p.getUniqueId(),p.getName());s.sendMessage("§6Recent matches");if(d.history().isEmpty()){s.sendMessage("§7No matches yet.");return true;}d.history().stream().limit(10).forEach(x->{String[] z=x.split("\\|",5);if(z.length>=5)s.sendMessage("§e"+z[1]+" §7» "+(z[2].equals("WIN")?"§aWIN":"§cLOSS")+" §7» §f"+z[3]+" §8vs §f"+z[4]);});return true;}
    private boolean party(CommandSender s,String[] a){
        if(!(s instanceof Player p)){s.sendMessage("§cPlayers only.");return true;}
        if(a.length==0 || a[0].equalsIgnoreCase("gui")){ if(a.length==0){ return openPartyGui(p); } String[] create={"create"}; party(p,create); return openPartyGui(p); }
        String sub=a[0].toLowerCase();
        UUID owner=partyOwner.get(p.getUniqueId());
        if(owner==null && parties.containsKey(p.getUniqueId())) owner=p.getUniqueId();
        switch(sub){
            case "create" -> { if(owner!=null){p.sendMessage("§cZaten bir partidesin.");return true;} parties.put(p.getUniqueId(),new LinkedHashSet<>(Set.of(p.getUniqueId()))); partyOwner.put(p.getUniqueId(),p.getUniqueId()); p.sendMessage("§aParti oluşturuldu.");}
            case "invite" -> { if(owner==null||!owner.equals(p.getUniqueId())){p.sendMessage("§cParti lideri olmalısın.");return true;} if(a.length<2){p.sendMessage("§e/party invite <player>");return true;} Player t=Bukkit.getPlayerExact(a[1]); if(t==null){p.sendMessage("§cOyuncu bulunamadı.");return true;} if(partyOwner.containsKey(t.getUniqueId())){p.sendMessage("§cBu oyuncu zaten bir partide.");return true;} parties.get(owner).add(t.getUniqueId()); partyOwner.put(t.getUniqueId(),owner); t.sendMessage("§a"+p.getName()+" seni partiye ekledi."); p.sendMessage("§aOyuncu partiye eklendi.");}
            case "leave" -> { if(owner==null){p.sendMessage("§cPartide değilsin.");return true;} if(owner.equals(p.getUniqueId())){p.sendMessage("§cLider olarak çıkmak için önce partiyi dağıt.");return true;} parties.get(owner).remove(p.getUniqueId()); partyOwner.remove(p.getUniqueId()); p.sendMessage("§aPartiden ayrıldın.");}
            case "disband" -> { if(owner==null||!owner.equals(p.getUniqueId())){p.sendMessage("§cParti lideri olmalısın.");return true;} for(UUID id:parties.get(owner))partyOwner.remove(id); parties.remove(owner); p.sendMessage("§aParti dağıtıldı."); p.closeInventory();}
            case "list" -> { if(owner==null){p.sendMessage("§cPartide değilsin.");return true;} p.sendMessage("§6Parti üyeleri:"); for(UUID id:parties.get(owner)){Player x=Bukkit.getPlayer(id);p.sendMessage("§7- §f"+(x==null?id:x.getName())+(id.equals(owner)?" §e(Lider)":""));}}
            default -> p.sendMessage("§e/party | /party create | /party invite <player> | /party leave | /party list | /party disband");
        } return true;
    }

    private boolean queue(CommandSender s){s.sendMessage("§6§lRanked Queues");for(Kit k:Kit.values())s.sendMessage("§e"+k+" §7» §f"+queues.get(k).size());return true;}
    private boolean admin(CommandSender s,String[] a){if(!s.hasPermission("harbourpvp.admin")){s.sendMessage("§cNo permission.");return true;}if(a.length==0){s.sendMessage("§e/ht setrating <player> <kit> <rating>");s.sendMessage("§e/ht settier <player> <kit> <tier>");s.sendMessage("§e/ht reset <player> <kit>");s.sendMessage("§e/ht forcematch <player1> <player2> <kit>");s.sendMessage("§e/ht arena create <isim>");s.sendMessage("§e/ht arena setspawn <isim> <1|2>");s.sendMessage("§e/ht arena pos1 <isim>");s.sendMessage("§e/ht arena pos2 <isim>");s.sendMessage("§e/ht arena delete <isim>");s.sendMessage("§e/ht arena list");s.sendMessage("§e/ht kit edit <kit>");s.sendMessage("§e/ht kit save <kit>");s.sendMessage("§e/ht kit clear <kit>");s.sendMessage("§e/ht reload");return true;}try{switch(a[0].toLowerCase()){case"setrating"->{if(a.length<4)break;Player p=Bukkit.getPlayerExact(a[1]);Kit k=Kit.from(a[2]);if(p==null||k==null)break;store.get(p.getUniqueId(),p.getName()).rating(k,Integer.parseInt(a[3]));store.save();updateTag(p.getUniqueId());s.sendMessage("§aRating updated.");return true;}case"settier"->{if(a.length<4)break;Player p=Bukkit.getPlayerExact(a[1]);Kit k=Kit.from(a[2]);if(p==null||k==null)break;Integer r=threshold(a[3]);if(r==null){s.sendMessage("§cUnknown tier.");return true;}store.get(p.getUniqueId(),p.getName()).rating(k,r);store.save();updateTag(p.getUniqueId());s.sendMessage("§aTier updated to §e"+a[3]+"§a.");return true;}case"reset"->{if(a.length<3)break;Player p=Bukkit.getPlayerExact(a[1]);Kit k=Kit.from(a[2]);if(p==null||k==null)break;store.get(p.getUniqueId(),p.getName()).rating(k,getConfig().getInt("starting-rating",1000));store.save();updateTag(p.getUniqueId());s.sendMessage("§aReset.");return true;}case"forcematch"->{if(a.length<4)break;Player p1=Bukkit.getPlayerExact(a[1]),p2=Bukkit.getPlayerExact(a[2]);Kit k=Kit.from(a[3]);if(p1==null||p2==null||k==null){s.sendMessage("§cInvalid player/kit.");return true;}startMatch(k,p1.getUniqueId(),p2.getUniqueId());return true;}case"arena"->{
                    if(a.length<2){s.sendMessage("§e/ht arena create <isim>");s.sendMessage("§e/ht arena setspawn <isim> <1|2>");s.sendMessage("§e/ht arena pos1 <isim>");s.sendMessage("§e/ht arena pos2 <isim>");s.sendMessage("§e/ht arena delete <isim>");s.sendMessage("§e/ht arena list");return true;}
                    String sub=a[1].toLowerCase();
                    if(sub.equals("create")){if(a.length<3){s.sendMessage("§e/ht arena create <isim>");return true;}String n=a[2];if(getConfig().contains("arenas."+n)){s.sendMessage("§cBu arena zaten var.");return true;}getConfig().set("arenas."+n+".spawn1",null);getConfig().set("arenas."+n+".spawn2",null);getConfig().set("arenas."+n+".pos1",null);getConfig().set("arenas."+n+".pos2",null);saveConfig();s.sendMessage("§aArena oluşturuldu: "+n);return true;}
                    if(sub.equals("setspawn")){if(a.length<4){s.sendMessage("§e/ht arena setspawn <isim> <1|2>");return true;}String n=a[2];String which=a[3];if(!which.equals("1")&&!which.equals("2")){s.sendMessage("§cSpawn 1 veya 2 olmalı.");return true;}if(!getConfig().contains("arenas."+n)){s.sendMessage("§cArena bulunamadı.");return true;}if(!(s instanceof Player p)){s.sendMessage("§cPlayers only.");return true;}saveLocation("arenas."+n+".spawn"+which,p.getLocation());s.sendMessage("§aArena "+n+" spawn"+which+" ayarlandı.");return true;}
                    if(sub.equals("pos1")||sub.equals("pos2")){if(a.length<3){s.sendMessage("§e/ht arena "+sub+" <isim>");return true;}String n=a[2];if(!getConfig().contains("arenas."+n)){s.sendMessage("§cArena bulunamadı.");return true;}if(!(s instanceof Player p)){s.sendMessage("§cPlayers only.");return true;}saveLocation("arenas."+n+"."+sub,p.getLocation());s.sendMessage("§aArena "+n+" "+sub+" ayarlandı.");return true;}
                    if(sub.equals("delete")){if(a.length<3){s.sendMessage("§e/ht arena delete <isim>");return true;}getConfig().set("arenas."+a[2],null);saveConfig();s.sendMessage("§aArena silindi.");return true;}
                    if(sub.equals("list")){s.sendMessage("§6§lArenalar");if(!getConfig().isConfigurationSection("arenas")){s.sendMessage("§7Henüz arena yok.");return true;}for(String n:getConfig().getConfigurationSection("arenas").getKeys(false))s.sendMessage("§e- "+n+" §7spawn1="+(location("arenas."+n+".spawn1")!=null?"✓":"✗")+" spawn2="+(location("arenas."+n+".spawn2")!=null?"✓":"✗")+" pos1="+(location("arenas."+n+".pos1")!=null?"✓":"✗")+" pos2="+(location("arenas."+n+".pos2")!=null?"✓":"✗"));return true;}
                    s.sendMessage("§e/ht arena create|setspawn|pos1|pos2|delete|list");return true;
                }case"kit"->{if(a.length<3){s.sendMessage("§e/ht kit edit|save|clear <kit>");return true;}Kit k=Kit.from(a[2]);if(k==null){s.sendMessage("§cUnknown kit.");return true;}switch(a[1].toLowerCase()){case"edit"->{if(!(s instanceof Player p)){s.sendMessage("§cPlayers only.");return true;}openKitEditor(p,k);return true;}case"save"->{if(!(s instanceof Player p)){s.sendMessage("§cPlayers only.");return true;}return saveKitFromInventory(p,k);}case"clear"-> {return clearKit(k,s);}}return true;}case"reload"-> {reloadConfig();s.sendMessage("§aConfig reloaded.");return true;}}}catch(Exception ex){s.sendMessage("§cInvalid command arguments.");}return true;}
    private String displayTier(PlayerData d, Kit kit){ return d.placements(kit) < getConfig().getInt("placement-matches",5) ? "Unranked" : tier(d.rating(kit)); }
    private String tier(int rating){String best="HT5";int br=-1;for(Map.Entry<String,Object> e:getConfig().getConfigurationSection("tiers").getValues(false).entrySet()){int t=getConfig().getInt("tiers."+e.getKey());if(t<=rating&&t>=br){br=t;best=e.getKey();}}return best;}
    private Integer threshold(String tier){if(getConfig().contains("tiers."+tier))return getConfig().getInt("tiers."+tier);return null;}
    private Location location(String path){String raw=getConfig().getString(path);if(raw==null)return null;String[] p=raw.split(",");if(p.length<4)return null;World w=Bukkit.getWorld(p[0]);if(w==null)return null;try{return new Location(w,Double.parseDouble(p[1]),Double.parseDouble(p[2]),Double.parseDouble(p[3]),p.length>4?Float.parseFloat(p[4]):0,p.length>5?Float.parseFloat(p[5]):0);}catch(Exception e){return null;}}
    private List<String> names(){return Arrays.stream(Kit.values()).map(Enum::name).toList();}
    private void updateTag(UUID id){
        Player p=Bukkit.getPlayer(id); if(p==null)return;
        Scoreboard sb=Bukkit.getScoreboardManager().getMainScoreboard();
        PlayerData d=store.get(id,p.getName());
        Kit kit=selectedKit.getOrDefault(id,Kit.Sword);
        String teamName="hp_"+kit.name()+"_"+displayTier(d,kit);
        Team old=sb.getEntryTeam(p.getName()); if(old!=null)old.removeEntry(p.getName());
        Team team=sb.getTeam(teamName); if(team==null)team=sb.registerNewTeam(teamName);
        team.setPrefix("§e["+displayTier(d,kit)+"] §f");
        team.addEntry(p.getName()); p.setScoreboard(sb);
    }
    @Override public List<String> onTabComplete(CommandSender s,Command c,String l,String[] a){if(c.getName().equals("play")||c.getName().equals("leaderboard")){if(a.length==1)return Arrays.stream(Kit.values()).map(Enum::name).filter(x->x.toLowerCase().startsWith(a[0].toLowerCase())).toList();}return List.of();}
}
