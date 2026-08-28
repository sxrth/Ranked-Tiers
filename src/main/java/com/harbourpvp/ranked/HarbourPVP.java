package com.harbourpvp.ranked;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scoreboard.*;
import java.util.*;

public class HarbourPVP extends org.bukkit.plugin.java.JavaPlugin implements Listener, CommandExecutor, TabCompleter {
    private DataStore store;
    private final EnumMap<Kit, Deque<UUID>> queues = new EnumMap<>(Kit.class);
    private final List<Match> matches = new ArrayList<>();
    private final Map<UUID, Location> returnLocations = new HashMap<>();

    @Override public void onEnable() {
        saveDefaultConfig(); store = new DataStore(this);
        for (Kit kit : Kit.values()) queues.put(kit, new ArrayDeque<>());
        getServer().getPluginManager().registerEvents(this, this);
        for (String cmd : List.of("play","stats","leaderboard","history","queue","ht")) {
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
        if (c.getName().equalsIgnoreCase("ht")) return admin(s,a);
        return false;
    }
    private boolean play(CommandSender s, String[] a) {
        if (!(s instanceof Player p)) { s.sendMessage("Players only."); return true; }
        if (a.length == 0) { p.sendMessage("§6HarbourPVP §7kits: §e" + String.join("§7, §e", names())); p.sendMessage("§7Use §e/play <kit>§7."); return true; }
        Kit kit = Kit.from(a[0]); if (kit == null) { p.sendMessage("§cUnknown kit."); return true; }
        if (matches.stream().anyMatch(m -> m.contains(p.getUniqueId()))) { p.sendMessage("§cYou are already in a match."); return true; }
        Deque<UUID> q = queues.get(kit); q.remove(p.getUniqueId());
        UUID opponent = null;
        for (UUID id : q) if (!id.equals(p.getUniqueId()) && Bukkit.getPlayer(id) != null) { opponent=id; break; }
        if (opponent == null) { q.addLast(p.getUniqueId()); p.sendMessage("§aQueued for §e"+kit+"§a. Waiting for an opponent..."); }
        else { q.remove(opponent); startMatch(kit, p.getUniqueId(), opponent); }
        return true;
    }
    private void startMatch(Kit kit, UUID one, UUID two) {
        Player a=Bukkit.getPlayer(one), b=Bukkit.getPlayer(two); if(a==null||b==null)return;
        if (returnLocations.put(one,a.getLocation())==null) returnLocations.put(two,b.getLocation()); else returnLocations.put(two,b.getLocation());
        Location l1=location("kits."+kit.name()+".position1"), l2=location("kits."+kit.name()+".position2");
        if(l1==null||l2==null){a.sendMessage("§cArena positions are not configured for "+kit+".");b.sendMessage("§cArena positions are not configured for "+kit+".");return;}
        prepare(a,kit); prepare(b,kit); a.teleport(l1); b.teleport(l2); matches.add(new Match(kit,one,two));
        a.sendMessage("§6Ranked §8» §e"+kit+" §7match started against §f"+b.getName()+"§7!");
        b.sendMessage("§6Ranked §8» §e"+kit+" §7match started against §f"+a.getName()+"§7!");
    }
    private void prepare(Player p, Kit kit){ p.setHealth(p.getMaxHealth()); p.setFoodLevel(20); p.setSaturation(20); p.getInventory().clear();
        switch(kit){
            case Sword, Vanilla -> {p.getInventory().addItem(new ItemStack(Material.NETHERITE_SWORD));p.getInventory().addItem(new ItemStack(Material.SHIELD));p.getInventory().addItem(new ItemStack(Material.GOLDEN_APPLE,4));}
            case Axe -> {p.getInventory().addItem(new ItemStack(Material.NETHERITE_AXE));p.getInventory().addItem(new ItemStack(Material.NETHERITE_SWORD));p.getInventory().addItem(new ItemStack(Material.GOLDEN_APPLE,4));}
            case Mace -> {p.getInventory().addItem(new ItemStack(Material.MACE));p.getInventory().addItem(new ItemStack(Material.ENDER_PEARL,4));p.getInventory().addItem(new ItemStack(Material.GOLDEN_APPLE,4));}
            case Pot, NethPot -> {p.getInventory().addItem(new ItemStack(Material.NETHERITE_SWORD));p.getInventory().addItem(new ItemStack(Material.SPLASH_POTION,16));p.getInventory().addItem(new ItemStack(Material.ENDER_PEARL,4));}
            case UHC -> {p.getInventory().addItem(new ItemStack(Material.NETHERITE_SWORD));p.getInventory().addItem(new ItemStack(Material.BOW));p.getInventory().addItem(new ItemStack(Material.ARROW,32));p.getInventory().addItem(new ItemStack(Material.GOLDEN_APPLE,8));}
            case SMP -> {p.getInventory().addItem(new ItemStack(Material.NETHERITE_SWORD));p.getInventory().addItem(new ItemStack(Material.ENDER_PEARL,8));p.getInventory().addItem(new ItemStack(Material.GOLDEN_APPLE,8));}
        }
    }
    @EventHandler public void death(PlayerDeathEvent e){ Player loser=e.getEntity(); Match m=find(loser.getUniqueId()); if(m!=null) Bukkit.getScheduler().runTask(this,()->finish(m,m.opponent(loser.getUniqueId()))); }
    @EventHandler public void quit(PlayerQuitEvent e){ UUID id=e.getPlayer().getUniqueId(); queues.values().forEach(q->q.remove(id)); Match m=find(id); if(m!=null) Bukkit.getScheduler().runTask(this,()->finish(m,m.opponent(id))); }
    private void finish(Match m, UUID winner){ if(!matches.remove(m))return; Player w=Bukkit.getPlayer(winner), loser=Bukkit.getPlayer(m.opponent(winner));
        PlayerData wp=store.get(winner,w==null?"Unknown":w.getName()), lp=store.get(m.opponent(winner),loser==null?"Unknown":loser.getName());
        int win=wp.rating(m.kit()), lose=lp.rating(m.kit()); int wr=getConfig().getInt("win-rating",25), lr=getConfig().getInt("loss-rating",20);
        int newW=win+wr,newL=Math.max(getConfig().getInt("min-rating",0),lose-lr); wp.rating(m.kit(),newW); lp.rating(m.kit(),newL);
        String stamp=Long.toString(System.currentTimeMillis()); wp.history().add(0,stamp+"|"+m.kit()+"|WIN|"+newW+"|"+lp.name()); lp.history().add(0,stamp+"|"+m.kit()+"|LOSS|"+newL+"|"+wp.name()); trim(wp);trim(lp); store.save();
        if(w!=null){w.sendMessage("§aWIN! §7"+m.kit()+" §8» §a+"+wr+" rating §7(§e"+newW+"§7) §8» §e"+tier(newW)); returnPlayer(w);}
        if(loser!=null){loser.sendMessage("§cLOSS! §7"+m.kit()+" §8» §c-"+lr+" rating §7(§e"+newL+"§7) §8» §e"+tier(newL)); returnPlayer(loser);}
        updateTag(winner); updateTag(m.opponent(winner));
    }
    private void trim(PlayerData p){while(p.history().size()>20)p.history().remove(p.history().size()-1);}
    private void returnPlayer(Player p){Location l=returnLocations.remove(p.getUniqueId()); if(l!=null)p.teleport(l); p.getInventory().clear(); updateTag(p.getUniqueId());}
    private Match find(UUID id){return matches.stream().filter(m->m.contains(id)).findFirst().orElse(null);}
    private boolean stats(CommandSender s,String[] a){Player p=a.length==0&&s instanceof Player?(Player)s:null; if(a.length>0)p=Bukkit.getPlayerExact(a[0]); if(p==null){s.sendMessage("§cPlayer not found.");return true;} PlayerData d=store.get(p.getUniqueId(),p.getName()); s.sendMessage("§6§lHarbourPVP §8» §f"+d.name()); for(Kit k:Kit.values())s.sendMessage("§e"+k+" §7» §f"+d.rating(k)+" §8» §e"+tier(d.rating(k))); return true;}
    private boolean leaderboard(CommandSender s,String[] a){Kit k=a.length>0?Kit.from(a[0]):Kit.Sword;if(k==null){s.sendMessage("§cUnknown kit.");return true;}List<PlayerData> list=new ArrayList<>(store.all());list.sort((x,y)->Integer.compare(y.rating(k),x.rating(k)));s.sendMessage("§6§l"+k+" Leaderboard");int i=1;for(PlayerData p:list){if(i>10)break;s.sendMessage("§e#"+i+" §f"+p.name()+" §7» §a"+p.rating(k)+" §8("+tier(p.rating(k))+")");i++;}return true;}
    private boolean history(CommandSender s,String[] a){if(!(s instanceof Player p)){s.sendMessage("Players only.");return true;}PlayerData d=store.get(p.getUniqueId(),p.getName());s.sendMessage("§6Recent matches");if(d.history().isEmpty()){s.sendMessage("§7No matches yet.");return true;}d.history().stream().limit(10).forEach(x->{String[] z=x.split("\\|",5);if(z.length>=5)s.sendMessage("§e"+z[1]+" §7» "+(z[2].equals("WIN")?"§aWIN":"§cLOSS")+" §7» §f"+z[3]+" §8vs §f"+z[4]);});return true;}
    private boolean queue(CommandSender s){s.sendMessage("§6§lRanked Queues");for(Kit k:Kit.values())s.sendMessage("§e"+k+" §7» §f"+queues.get(k).size());return true;}
    private boolean admin(CommandSender s,String[] a){if(!s.hasPermission("harbourpvp.admin")){s.sendMessage("§cNo permission.");return true;}if(a.length==0){s.sendMessage("§e/ht setrating <player> <kit> <rating>");s.sendMessage("§e/ht settier <player> <kit> <tier>");s.sendMessage("§e/ht reset <player> <kit>");s.sendMessage("§e/ht forcematch <player1> <player2> <kit>");s.sendMessage("§e/ht reload");return true;}try{switch(a[0].toLowerCase()){case"setrating"->{if(a.length<4)break;Player p=Bukkit.getPlayerExact(a[1]);Kit k=Kit.from(a[2]);if(p==null||k==null)break;store.get(p.getUniqueId(),p.getName()).rating(k,Integer.parseInt(a[3]));store.save();updateTag(p.getUniqueId());s.sendMessage("§aRating updated.");return true;}case"settier"->{if(a.length<4)break;Player p=Bukkit.getPlayerExact(a[1]);Kit k=Kit.from(a[2]);if(p==null||k==null)break;Integer r=threshold(a[3]);if(r==null){s.sendMessage("§cUnknown tier.");return true;}store.get(p.getUniqueId(),p.getName()).rating(k,r);store.save();updateTag(p.getUniqueId());s.sendMessage("§aTier updated to §e"+a[3]+"§a.");return true;}case"reset"->{if(a.length<3)break;Player p=Bukkit.getPlayerExact(a[1]);Kit k=Kit.from(a[2]);if(p==null||k==null)break;store.get(p.getUniqueId(),p.getName()).rating(k,getConfig().getInt("starting-rating",1000));store.save();updateTag(p.getUniqueId());s.sendMessage("§aReset.");return true;}case"forcematch"->{if(a.length<4)break;Player p1=Bukkit.getPlayerExact(a[1]),p2=Bukkit.getPlayerExact(a[2]);Kit k=Kit.from(a[3]);if(p1==null||p2==null||k==null){s.sendMessage("§cInvalid player/kit.");return true;}startMatch(k,p1.getUniqueId(),p2.getUniqueId());return true;}case"reload"-> {reloadConfig();s.sendMessage("§aConfig reloaded.");return true;}}}catch(Exception ex){s.sendMessage("§cInvalid command arguments.");}return true;}
    private String tier(int rating){String best="HT5";int br=-1;for(Map.Entry<String,Object> e:getConfig().getConfigurationSection("tiers").getValues(false).entrySet()){int t=getConfig().getInt("tiers."+e.getKey());if(t<=rating&&t>=br){br=t;best=e.getKey();}}return best;}
    private Integer threshold(String tier){if(getConfig().contains("tiers."+tier))return getConfig().getInt("tiers."+tier);return null;}
    private Location location(String path){String raw=getConfig().getString(path);if(raw==null)return null;String[] p=raw.split(",");if(p.length<4)return null;World w=Bukkit.getWorld(p[0]);if(w==null)return null;try{return new Location(w,Double.parseDouble(p[1]),Double.parseDouble(p[2]),Double.parseDouble(p[3]),p.length>4?Float.parseFloat(p[4]):0,p.length>5?Float.parseFloat(p[5]):0);}catch(Exception e){return null;}}
    private List<String> names(){return Arrays.stream(Kit.values()).map(Enum::name).toList();}
    private void updateTag(UUID id){Player p=Bukkit.getPlayer(id);if(p==null)return;Scoreboard sb=Bukkit.getScoreboardManager().getMainScoreboard();PlayerData d=store.get(id,p.getName());String teamName="hp_"+tier(d.rating(Kit.Sword));Team old=sb.getEntryTeam(p.getName());if(old!=null)old.removeEntry(p.getName());Team team=sb.getTeam(teamName);if(team==null)team=sb.registerNewTeam(teamName);team.setPrefix("§e["+tier(d.rating(Kit.Sword))+"] §f");team.addEntry(p.getName());p.setScoreboard(sb);}
    @Override public List<String> onTabComplete(CommandSender s,Command c,String l,String[] a){if(c.getName().equals("play")||c.getName().equals("leaderboard")){if(a.length==1)return Arrays.stream(Kit.values()).map(Enum::name).filter(x->x.toLowerCase().startsWith(a[0].toLowerCase())).toList();}return List.of();}
}
