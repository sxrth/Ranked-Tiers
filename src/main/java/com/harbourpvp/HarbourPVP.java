package com.harbourpvp;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class HarbourPVP extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {
    private final Map<String, Party> parties = new HashMap<>();
    private final Map<UUID, String> partyOf = new HashMap<>();
    private final Map<String, LinkedList<UUID>> queues = new HashMap<>();
    private final Map<UUID, Match> matches = new HashMap<>();
    private final Map<String, Arena> arenas = new HashMap<>();
    private final Map<String, Map<UUID,Integer>> ratings = new HashMap<>();
    private final Map<String, Map<UUID,Integer>> placements = new HashMap<>();
    private final Set<UUID> inEditor = new HashSet<>();
    private BukkitTask matcher;
    private final List<String> kits = List.of("Sword","Axe","Mace","Pot","NethPot","SMP","UHC","Vanilla","Spear");

    @Override public void onEnable() {
        saveDefaultConfig();
        loadArenas();
        loadRatings();
        getServer().getPluginManager().registerEvents(this, this);
        Objects.requireNonNull(getCommand("play")).setExecutor(this);
        Objects.requireNonNull(getCommand("ht")).setExecutor(this);
        Objects.requireNonNull(getCommand("ht")).setTabCompleter(this);
        Objects.requireNonNull(getCommand("party")).setExecutor(this);
        Objects.requireNonNull(getCommand("party")).setTabCompleter(this);
        matcher = getServer().getScheduler().runTaskTimer(this, this::matchQueues, 20L, 20L);
        getLogger().info("HarbourPVP enabled: ranked + party queues + arenas + kit editor");
    }
    @Override public void onDisable() { if (matcher != null) matcher.cancel(); saveArenas(); saveRatings(); }

    private ItemStack item(Material m, String name, String... lore) {
        ItemStack i=new ItemStack(m); ItemMeta meta=i.getItemMeta(); meta.setDisplayName(name); meta.setLore(Arrays.asList(lore)); i.setItemMeta(meta); return i;
    }
    private void fill(Inventory inv) { ItemStack glass=item(Material.GRAY_STAINED_GLASS_PANE," "); for(int i=0;i<inv.getSize();i++) if(inv.getItem(i)==null) inv.setItem(i,glass); }
    private void openLobby(Player p) {
        Inventory inv=Bukkit.createInventory(null,27,"HarbourPVP • Play"); fill(inv);
        inv.setItem(0,item(Material.DIAMOND_SWORD,"§bSıraya Gir","§7Ranked kit seç ve sıraya gir."));
        inv.setItem(1,item(Material.CHEST,"§eParti Aç","§7Parti menüsünü aç."));
        inv.setItem(4,item(Material.PAPER,"§aİstatistikler","§7Rating ve maç istatistiklerin."));
        inv.setItem(8,item(Material.BOOK,"§dKit Düzenleme","§7Kendi kitlerini düzenle."));
        p.openInventory(inv);
    }
    private void openKitMenu(Player p, boolean party) {
        Inventory inv=Bukkit.createInventory(null,27,party?"HarbourPVP • Party Queue":"HarbourPVP • Ranked Queue"); fill(inv);
        for(int i=0;i<kits.size();i++) { String k=kits.get(i); int r=rating(p,k); int pl=placements(p,k); inv.setItem(i,kitIcon(k,"§f"+k,"§7Rating: §b"+r,"§7Tier: §e"+tier(p,k),"§7Placement: §f"+pl+"/5","§7Queue: §f"+queues.getOrDefault(k,new LinkedList<>()).size(),"","§aTıkla: sıraya gir")); }
        inv.setItem(22,item(Material.ARROW,"§cGeri")); p.openInventory(inv);
    }
    private void openParty(Player p) {
        String id=partyOf.get(p.getUniqueId());
        Inventory inv=Bukkit.createInventory(null,36,"HarbourPVP • Party"); fill(inv);
        if(id==null) { inv.setItem(13,item(Material.PLAYER_HEAD,"§eParti Oluştur","§7Tıkla ve yeni parti oluştur.")); }
        else {
            Party party=parties.get(id); int s=0; for(UUID u:party.members) { Player m=Bukkit.getPlayer(u); if(m!=null) inv.setItem(s++,item(Material.PLAYER_HEAD,(u.equals(party.leader)?"§6👑 ":"§f")+m.getName(),"§7Rating: §b"+rating(m,party.kit),"§7Tier: §e"+tier(rating(m,party.kit)),u.equals(party.leader)?"§6Lider":"§7Üye")); }
            inv.setItem(27,item(Material.EMERALD,"§aOyuncu Davet Et","§7Çevrimiçi oyuncu seç."));
            inv.setItem(30,item(Material.DIAMOND_SWORD,"§bParty Queue","§7Kit seçip parti olarak sıraya gir."));
            inv.setItem(31,item(Material.PAPER,"§fParti Kiti","§7Şu an: §e"+(party.kit==null?"Seçilmedi":party.kit)));
            inv.setItem(32,item(Material.BARRIER,"§cPartiden Ayrıl"));
            if(party.leader.equals(p.getUniqueId())) inv.setItem(35,item(Material.TNT,"§cPartiyi Dağıt"));
        }
        p.openInventory(inv);
    }
    private void openKitEditor(Player p) {
        Inventory inv=Bukkit.createInventory(null,54,"HarbourPVP • Kit Editor"); fill(inv);
        int x=0; for(String k:kits) inv.setItem(x++,item(Material.CHEST,"§e"+k,"§7Kiti düzenlemek için tıkla."));
        inv.setItem(49,item(Material.BARRIER,"§cKapat")); p.openInventory(inv);
    }
    private void openStats(Player p) {
        Inventory inv=Bukkit.createInventory(null,27,"HarbourPVP • İstatistikler"); fill(inv); int x=0;
        for(String k:kits) { int r=rating(p,k); inv.setItem(x++,kitIcon(k,"§f"+k,"§7Rating: §b"+r,"§7Tier: §e"+tier(p,k),"§7Placement: §f"+placements(p,k)+"/5")); }
        inv.setItem(22,item(Material.ARROW,"§cGeri")); p.openInventory(inv);
    }

    @EventHandler public void onJoin(PlayerJoinEvent e) { giveLobbyItems(e.getPlayer()); }
    private void giveLobbyItems(Player p) { p.getInventory().setItem(0,item(Material.DIAMOND_SWORD,"§bSıraya Gir")); p.getInventory().setItem(1,item(Material.CHEST,"§eParti Aç")); p.getInventory().setItem(4,item(Material.PAPER,"§aİstatistikler")); p.getInventory().setItem(8,item(Material.BOOK,"§dKit Düzenleme")); }
    @EventHandler public void onQuit(PlayerQuitEvent e) { leaveQueue(e.getPlayer()); matches.remove(e.getPlayer().getUniqueId()); }
    @EventHandler public void death(PlayerDeathEvent e){ Player dead=e.getEntity(); Match m=matches.get(dead.getUniqueId()); if(m==null)return; getServer().getScheduler().runTask(this,()->finishMatch(m, m.a.contains(dead)?m.b:m.a)); }
    @EventHandler public void blockPlace(BlockPlaceEvent e){ Match m=matches.get(e.getPlayer().getUniqueId()); if(m!=null && !allowsBlocks(m.kit)) e.setCancelled(true); }
    @EventHandler public void blockBreak(BlockBreakEvent e){ Match m=matches.get(e.getPlayer().getUniqueId()); if(m!=null && !allowsBlocks(m.kit)) e.setCancelled(true); }
    private boolean allowsBlocks(String kit){ return kit.equalsIgnoreCase("UHC")||kit.equalsIgnoreCase("SMP"); }

    @EventHandler public void click(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        String t = e.getView().getTitle();
        if (!t.startsWith("HarbourPVP")) return;
        e.setCancelled(true);
        if (e.getClickedInventory() == null || e.getClickedInventory() != e.getView().getTopInventory()) return;
        ItemStack i = e.getCurrentItem();
        if (i == null || !i.hasItemMeta()) return;
        int slot = e.getRawSlot();

        if (t.equals("HarbourPVP • Play")) {
            if (slot == 0) openKitMenu(p, false);
            else if (slot == 1) { createParty(p); openParty(p); }
            else if (slot == 4) openStats(p);
            else if (slot == 8) openKitEditor(p);
        } else if (t.equals("HarbourPVP • Ranked Queue")) {
            if (slot < kits.size()) joinQueue(p, kits.get(slot), false);
            else if (slot == 22) openLobby(p);
        } else if (t.equals("HarbourPVP • Party Queue")) {
            if (slot < kits.size()) {
                Party q = parties.get(partyOf.get(p.getUniqueId()));
                if (q != null) { q.kit = kits.get(slot); joinPartyQueue(q); }
            } else if (slot == 22) openParty(p);
        } else if (t.equals("HarbourPVP • Party")) {
            if (slot == 13 && !partyOf.containsKey(p.getUniqueId())) { createParty(p); openParty(p); }
            else if (slot == 27) inviteMenu(p);
            else if (slot == 30) openKitMenu(p, true);
            else if (slot == 32) { leaveParty(p); openParty(p); }
            else if (slot == 35) { disbandParty(p); openParty(p); }
        } else if (t.equals("HarbourPVP • İstatistikler")) {
            if (slot == 22) openLobby(p);
        } else if (t.equals("HarbourPVP • Kit Editor")) {
            if (slot < kits.size()) startEdit(p, kits.get(slot));
        } else if (t.startsWith("HarbourPVP • Edit ")) {
            if (slot == 49) saveEdit(p, t.substring("HarbourPVP • Edit ".length()));
        } else if (t.startsWith("HarbourPVP • Invite")) {
            String name = ChatColor.stripColor(i.getItemMeta().getDisplayName());
            Player target = Bukkit.getPlayerExact(name);
            if (target != null) invite(p, target);
        }
    }

    @EventHandler public void interactLobby(org.bukkit.event.player.PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (e.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_AIR && e.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK
                && e.getAction() != org.bukkit.event.block.Action.LEFT_CLICK_AIR && e.getAction() != org.bukkit.event.block.Action.LEFT_CLICK_BLOCK) return;
        ItemStack it = e.getItem();
        if (it == null || !it.hasItemMeta() || it.getItemMeta().getDisplayName() == null) return;
        if (matches.containsKey(p.getUniqueId())) return;
        String n = ChatColor.stripColor(it.getItemMeta().getDisplayName());
        if (n.equals("Sıraya Gir")) { e.setCancelled(true); openKitMenu(p, false); }
        else if (n.equals("Parti Aç")) { e.setCancelled(true); createParty(p); openParty(p); }
        else if (n.equals("İstatistikler")) { e.setCancelled(true); openStats(p); }
        else if (n.equals("Kit Düzenleme")) { e.setCancelled(true); openKitEditor(p); }
    }

    private void inviteMenu(Player p) { Inventory inv=Bukkit.createInventory(null,54,"HarbourPVP • Invite"); fill(inv); int s=0; for(Player x:Bukkit.getOnlinePlayers()) if(!x.equals(p)) inv.setItem(s++,item(Material.PLAYER_HEAD,"§e"+x.getName(),"§7Tıkla: davet et")); p.openInventory(inv); }

    private void startEdit(Player p,String kit) {
        inEditor.add(p.getUniqueId());
        Inventory inv = Bukkit.createInventory(null,54,"HarbourPVP • Edit "+kit);
        List<?> saved = getConfig().getList("kits."+kit+".items");
        if (saved != null) {
            for (int j=0; j<Math.min(36, saved.size()); j++) if (saved.get(j) instanceof ItemStack item) inv.setItem(j, item.clone());
        } else {
            ItemStack[] current=p.getInventory().getContents();
            for(int j=0;j<36;j++) inv.setItem(j,current[j]);
        }
        inv.setItem(49,item(Material.EMERALD,"§aKAYDET","§7Bu menüdeki itemleri kaydet."));
        p.openInventory(inv);
    }
    private void saveEdit(Player p,String kit) { ItemStack[] c=p.getOpenInventory().getTopInventory().getContents(); getConfig().set("kits."+kit+".items",Arrays.asList(Arrays.copyOf(c,36))); saveConfig(); inEditor.remove(p.getUniqueId()); p.closeInventory(); p.sendMessage("§a[HarbourPVP] "+kit+" kiti kaydedildi."); }

    private void createParty(Player p) { if(partyOf.containsKey(p.getUniqueId())) return; String id=p.getUniqueId().toString(); Party q=new Party(id,p.getUniqueId()); parties.put(id,q); partyOf.put(p.getUniqueId(),id); p.sendMessage("§aParti oluşturuldu!"); }
    private void invite(Player p,Player target) { String id=partyOf.get(p.getUniqueId()); Party q=parties.get(id); if(q==null||!q.leader.equals(p.getUniqueId())) return; q.invites.add(target.getUniqueId()); target.sendMessage("§e"+p.getName()+" §7seni partiye davet etti. §a/party accept "+p.getName()); p.sendMessage("§aDavet gönderildi."); }
    private void leaveParty(Player p) { String id=partyOf.remove(p.getUniqueId()); if(id==null)return; Party q=parties.get(id); if(q==null)return; q.members.remove(p.getUniqueId()); if(q.members.isEmpty()){parties.remove(id);return;} if(q.leader.equals(p.getUniqueId())) q.leader=q.members.iterator().next(); p.sendMessage("§cPartiden ayrıldın."); }
    private void disbandParty(Player p) { String id=partyOf.get(p.getUniqueId()); Party q=parties.get(id); if(q==null||!q.leader.equals(p.getUniqueId()))return; for(UUID u:q.members) partyOf.remove(u); parties.remove(id); p.sendMessage("§cParti dağıtıldı."); }

    private void joinQueue(Player p,String kit,boolean party) { if(matches.containsKey(p.getUniqueId())) return; LinkedList<UUID> q=queues.computeIfAbsent(kit,k->new LinkedList<>()); if(q.contains(p.getUniqueId())) {q.remove(p.getUniqueId());p.sendMessage("§cSıradan çıktın.");} else {q.add(p.getUniqueId());p.sendMessage("§a"+kit+" ranked queue'ya girdin. §7Oyuncu: "+q.size());} }
    private void leaveQueue(Player p){for(LinkedList<UUID> q:queues.values())q.remove(p.getUniqueId());}
    private void joinPartyQueue(Party party) { if(party.kit==null)return; if(party.members.size()<2){Player l=Bukkit.getPlayer(party.leader);if(l!=null)l.sendMessage("§cPartide en az 2 oyuncu olmalı.");return;} String key="PARTY:"+party.kit; LinkedList<UUID> q=queues.computeIfAbsent(key,k->new LinkedList<>()); if(!q.contains(party.leader)){q.add(party.leader);for(UUID u:party.members){Player p=Bukkit.getPlayer(u);if(p!=null)p.sendMessage("§aPartiniz "+party.kit+" party queue'ya girdi!");}} }
    private void matchQueues(){ for(String kit:new ArrayList<>(queues.keySet())) { if(kit.startsWith("PARTY:")){matchParties(kit.substring(6));}else{LinkedList<UUID> q=queues.get(kit); if(q==null)continue; while(q.size()>=2){UUID a=q.poll(),b=q.poll();Player p1=Bukkit.getPlayer(a),p2=Bukkit.getPlayer(b);if(p1!=null&&p2!=null)startMatch(List.of(p1),List.of(p2),kit);}} } }
    private void matchParties(String kit){ LinkedList<UUID> q=queues.get("PARTY:"+kit); if(q==null)return; while(q.size()>=2){Party a=parties.get(q.poll()),b=parties.get(q.poll()); if(a!=null&&b!=null) {List<Player> t1=online(a.members),t2=online(b.members); if(!t1.isEmpty()&&!t2.isEmpty())startMatch(t1,t2,kit);}} }
    private List<Player> online(Collection<UUID> ids){List<Player> l=new ArrayList<>();for(UUID u:ids){Player p=Bukkit.getPlayer(u);if(p!=null)l.add(p);}return l;}

    private void startMatch(List<Player> a,List<Player> b,String kit){Arena ar=findArena(kit); if(ar==null){for(Player p:a) p.sendMessage("§cArena bulunamadı: "+kit);for(Player p:b)p.sendMessage("§cArena bulunamadı: "+kit);return;} Match m=new Match(kit,a,b,ar);for(Player p:a){matches.put(p.getUniqueId(),m);p.teleport(ar.a);p.sendMessage("§eMaç bulunuyor! Rakip bulundu.");}for(Player p:b){matches.put(p.getUniqueId(),m);p.teleport(ar.b);p.sendMessage("§eMaç bulunuyor! Rakip bulundu.");} giveKit(a,kit);giveKit(b,kit); countdown(m); }
    private void countdown(Match m){
        final int[] n={3};
        final BukkitTask[] task=new BukkitTask[1];
        task[0]=getServer().getScheduler().runTaskTimer(this,()->{
            if(n[0]>0){for(Player p:m.all())p.sendTitle("§e"+n[0],"§7Hazırlan!",0,20,0);n[0]--;}
            else {for(Player p:m.all())p.sendTitle("§cFIGHT!","§7"+m.kit,0,20,5);task[0].cancel();}
        },0,20);
    }
    private void giveKit(List<Player> ps,String kit){for(Player p:ps){p.getInventory().clear();List<?> list=getConfig().getList("kits."+kit+".items");if(list!=null){int i=0;for(Object o:list)if(o instanceof ItemStack it&&i<36)p.getInventory().setItem(i++,it.clone());}}}
    private Arena findArena(String kit){for(Arena a:arenas.values())if(a.kit.equalsIgnoreCase(kit)&&a.a!=null&&a.b!=null)return a;return null;}


    private void finishMatch(Match m,List<Player> winners){
        if(m.all().stream().noneMatch(p->matches.get(p.getUniqueId())==m)) return;
        Set<UUID> win=new HashSet<>(); for(Player p:winners)win.add(p.getUniqueId());
        for(Player p:m.all()){ int old=rating(p,m.kit); String before=tier(p,m.kit); int delta=win.contains(p.getUniqueId())?25:-20; int nr=Math.max(0,old+delta); ratings.computeIfAbsent(m.kit,k->new HashMap<>()).put(p.getUniqueId(),nr); int pl=placements(p,m.kit); if(pl<5) placements.computeIfAbsent(m.kit,k->new HashMap<>()).put(p.getUniqueId(),pl+1); String after=tier(p,m.kit); String msg=win.contains(p.getUniqueId())?"§aVICTORY":"§cDEFEAT"; String rankText=after.equals("Unranked")?"§eUnranked §7(Placement "+(pl+1)+"/5)":"§e"+after+" §7("+nr+")"; p.sendActionBar(msg+" §8• §f"+m.kit+" §8• "+(delta>0?"§a+":"§c")+delta+" Rating §8• "+rankText); if(!before.equals(after)&&!after.equals("Unranked"))p.sendMessage("§6§lTIER PLACEMENT §e"+m.kit+" §7→ §a"+after); p.getInventory().clear(); giveLobbyItems(p); p.teleport(p.getWorld().getSpawnLocation()); matches.remove(p.getUniqueId()); } saveRatings();
    }
    private int rating(Player p,String kit){return ratings.computeIfAbsent(kit,k->new HashMap<>()).getOrDefault(p.getUniqueId(),getConfig().getInt("starting-rating",0));}
    private int placements(Player p,String kit){return placements.computeIfAbsent(kit,k->new HashMap<>()).getOrDefault(p.getUniqueId(),0);}
    private String tier(Player p,String kit){if(placements(p,kit)<5)return"Unranked";return tierFromRating(rating(p,kit));}
    private String tierFromRating(int r){if(r>=2400)return"LT1";if(r>=2200)return"MT1";if(r>=2000)return"HT1";if(r>=1800)return"LT2";if(r>=1650)return"MT2";if(r>=1500)return"HT2";if(r>=1350)return"LT3";if(r>=1200)return"MT3";if(r>=1100)return"HT3";if(r>=1000)return"LT4";if(r>=900)return"MT4";if(r>=800)return"LT5";if(r>=700)return"MT5";return"HT5";}
    private ItemStack kitIcon(String kit,String name,String... lore){Material m; if(kit.equalsIgnoreCase("Pot")){ItemStack i=new ItemStack(Material.POTION);PotionMeta pm=(PotionMeta)i.getItemMeta();pm.setBasePotionType(PotionType.INSTANT_HEALTH);pm.setDisplayName(name);pm.setLore(Arrays.asList(lore));i.setItemMeta(pm);return i;} if(kit.equalsIgnoreCase("Spear")){m=Material.matchMaterial("NETHERITE_SPEAR");if(m==null)m=Material.TRIDENT;}else if(kit.equalsIgnoreCase("Sword"))m=Material.DIAMOND_SWORD;else if(kit.equalsIgnoreCase("Axe"))m=Material.DIAMOND_AXE;else if(kit.equalsIgnoreCase("Mace"))m=Material.MACE;else if(kit.equalsIgnoreCase("NethPot"))m=Material.NETHERITE_SWORD;else if(kit.equalsIgnoreCase("SMP"))m=Material.SHIELD;else if(kit.equalsIgnoreCase("UHC"))m=Material.GOLDEN_APPLE;else if(kit.equalsIgnoreCase("Vanilla"))m=Material.END_CRYSTAL;else m=Material.BOOK;return item(m,name,lore);}
    private String tier(int r){return tierFromRating(r);}

    private void loadArenas(){if(!getConfig().isConfigurationSection("arenas"))return;for(String n:getConfig().getConfigurationSection("arenas").getKeys(false)){String k=getConfig().getString("arenas."+n+".kit",n);Location a=loc(getConfig().getString("arenas."+n+".spawn1")),b=loc(getConfig().getString("arenas."+n+".spawn2"));arenas.put(n,new Arena(n,k,a,b));}}
    private void saveArenas(){for(Arena a:arenas.values()){String p="arenas."+a.name;getConfig().set(p+".kit",a.kit);getConfig().set(p+".spawn1",s(a.a));getConfig().set(p+".spawn2",s(a.b));}saveConfig();}
    private Location loc(String x){if(x==null)return null;String[] q=x.split(",");return Bukkit.getWorld(q[0])==null?null:new Location(Bukkit.getWorld(q[0]),Double.parseDouble(q[1]),Double.parseDouble(q[2]),Double.parseDouble(q[3]),Float.parseFloat(q[4]),Float.parseFloat(q[5]));}
    private String s(Location l){return l==null?null:l.getWorld().getName()+","+l.getX()+","+l.getY()+","+l.getZ()+","+l.getYaw()+","+l.getPitch();}
    private void loadRatings(){
        if(!getConfig().isConfigurationSection("players"))return;
        for(String us:getConfig().getConfigurationSection("players").getKeys(false)){try{UUID u=UUID.fromString(us);for(String k:kits){String base="players."+us+"."+k;int r=getConfig().getInt(base+".rating",0),pl=getConfig().getInt(base+".placements",0);ratings.computeIfAbsent(k,x->new HashMap<>()).put(u,r);placements.computeIfAbsent(k,x->new HashMap<>()).put(u,pl);}}catch(IllegalArgumentException ignored){}}
    }
    private void saveRatings(){
        for(String k:kits){Map<UUID,Integer> rm=ratings.getOrDefault(k,Collections.emptyMap());Map<UUID,Integer> pm=placements.getOrDefault(k,Collections.emptyMap());for(UUID u:rm.keySet()){String base="players."+u+"."+k;getConfig().set(base+".rating",rm.get(u));getConfig().set(base+".placements",pm.getOrDefault(u,0));}} saveConfig();
    }

    @Override public boolean onCommand(CommandSender s,Command c,String label,String[] a){
        if(c.getName().equalsIgnoreCase("play")){if(s instanceof Player p)openLobby(p);return true;}
        if(c.getName().equalsIgnoreCase("party")){if(!(s instanceof Player p))return true;if(a.length==0){openParty(p);return true;}if(a[0].equalsIgnoreCase("accept")&&a.length>1){Player l=Bukkit.getPlayerExact(a[1]);if(l!=null){String id=partyOf.get(l.getUniqueId());Party q=parties.get(id);if(q!=null&&q.invites.remove(p.getUniqueId())){partyOf.put(p.getUniqueId(),id);q.members.add(p.getUniqueId());p.sendMessage("§aPartiye katıldın!");}}}return true;}
        if(c.getName().equalsIgnoreCase("ht")){if(!(s instanceof Player p)&&a.length==0)return true;if(a.length>=1&&a[0].equalsIgnoreCase("arena")){if(a.length>=2&&a[1].equalsIgnoreCase("create")&&a.length>=3){arenas.put(a[2],new Arena(a[2],a[2]));saveArenas();s.sendMessage("§aArena oluşturuldu: "+a[2]);return true;}if(a.length>=2&&a[1].equalsIgnoreCase("set")&&a.length>=4&&s instanceof Player p){Arena ar=arenas.get(a[2]);if(ar==null){s.sendMessage("§cArena yok.");return true;}if(a[3].equals("1"))ar.a=p.getLocation();else if(a[3].equals("2"))ar.b=p.getLocation();saveArenas();s.sendMessage("§aSpawn "+a[3]+" ayarlandı.");return true;}if(a.length>=2&&a[1].equalsIgnoreCase("delete")&&a.length>=3){arenas.remove(a[2]);getConfig().set("arenas."+a[2],null);saveConfig();s.sendMessage("§cArena silindi.");return true;}if(a.length>=2&&a[1].equalsIgnoreCase("list")){s.sendMessage("§eArenalar: §f"+String.join(", ",arenas.keySet()));return true;}}
        if(a.length>=3&&a[0].equalsIgnoreCase("kit")&&a[1].equalsIgnoreCase("edit")&&s instanceof Player p){startEdit(p,a[2]);return true;}if(a.length>=3&&a[0].equalsIgnoreCase("kit")&&a[1].equalsIgnoreCase("save")&&s instanceof Player p){saveEdit(p,a[2]);return true;}if(a.length>=1&&a[0].equalsIgnoreCase("reload")){reloadConfig();loadArenas();s.sendMessage("§aReloaded.");return true;}return true;}
        return false;
    }
    @Override public List<String> onTabComplete(CommandSender s,Command c,String l,String[] a){if(c.getName().equalsIgnoreCase("ht")&&a.length==1)return List.of("arena","kit","reload");if(c.getName().equalsIgnoreCase("ht")&&a.length==2&&a[0].equalsIgnoreCase("arena"))return List.of("create","set","delete","list");if(c.getName().equalsIgnoreCase("ht")&&a.length==2&&a[0].equalsIgnoreCase("kit"))return List.of("edit","save","clear");return List.of();}

    static class Party {String id; UUID leader; Set<UUID> members=new LinkedHashSet<>();Set<UUID> invites=new HashSet<>();String kit;Party(String i,UUID l){id=i;leader=l;members.add(l);}}
    static class Arena {String name,kit;Location a,b;Arena(String n){this(n,n,null,null);}Arena(String n,String k){this(n,k,null,null);}Arena(String n,String k,Location a,Location b){name=n;kit=k;this.a=a;this.b=b;}}
    static class Match {String kit;List<Player>a,b;Arena ar;Match(String k,List<Player>x,List<Player>y,Arena z){kit=k;a=x;b=y;ar=z;}List<Player>all(){List<Player>l=new ArrayList<>(a);l.addAll(b);return l;}}
}
