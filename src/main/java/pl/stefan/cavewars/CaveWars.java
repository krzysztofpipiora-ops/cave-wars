package pl.stefan.cavewars;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.stream.Collectors;

public class CaveWars extends JavaPlugin implements Listener {

    private final Random random = new Random();
    private final Map<UUID, ArenaData> arenas = new HashMap<>();
    private final Map<UUID, BossBar> playerBossBars = new HashMap<>();
    private final List<UUID> registeredWorlds = new ArrayList<>();
    private final String SPAWN_WORLD_NAME = "world";

    private static class ArenaData {
        World world;
        Map<UUID, Integer> kills = new HashMap<>();
        boolean active = false;
        int countdown = -1;
        int pvpGraceTime = 0; 
        List<Location> spawnPoints = new ArrayList<>();
        ArenaData(World world) { this.world = world; }
    }

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        registerCustomRecipes();
        
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (ArenaData arena : arenas.values()) {
                if (arena.active) {
                    updateActiveArena(arena);
                    if (arena.pvpGraceTime > 0) arena.pvpGraceTime--;
                } else if (registeredWorlds.contains(arena.world.getUID())) {
                    handleLobbyCountdown(arena);
                }
            }
        }, 20L, 20L);
    }

    // --- SYSTEM CRAFTINGU NETHERITE (BEZ TEMPLATE) ---
    private void registerCustomRecipes() {
        addNetheriteUpgrade(Material.NETHERITE_INGOT, Material.NETHERITE_SCRAP, Material.DIAMOND, "cw_n_ing");
        addNetheriteUpgrade(Material.NETHERITE_SWORD, Material.DIAMOND_SWORD, Material.NETHERITE_INGOT, "cw_n_sw");
        addNetheriteUpgrade(Material.NETHERITE_PICKAXE, Material.DIAMOND_PICKAXE, Material.NETHERITE_INGOT, "cw_n_pi");
        addNetheriteUpgrade(Material.NETHERITE_AXE, Material.DIAMOND_AXE, Material.NETHERITE_INGOT, "cw_n_ax");
        addNetheriteUpgrade(Material.NETHERITE_SHOVEL, Material.DIAMOND_SHOVEL, Material.NETHERITE_INGOT, "cw_n_sh");
        addNetheriteUpgrade(Material.NETHERITE_HELMET, Material.DIAMOND_HELMET, Material.NETHERITE_INGOT, "cw_n_he");
        addNetheriteUpgrade(Material.NETHERITE_CHESTPLATE, Material.DIAMOND_CHESTPLATE, Material.NETHERITE_INGOT, "cw_n_ch");
        addNetheriteUpgrade(Material.NETHERITE_LEGGINGS, Material.DIAMOND_LEGGINGS, Material.NETHERITE_INGOT, "cw_n_le");
        addNetheriteUpgrade(Material.NETHERITE_BOOTS, Material.DIAMOND_BOOTS, Material.NETHERITE_INGOT, "cw_n_bo");
    }

    private void addNetheriteUpgrade(Material res, Material i1, Material i2, String k) {
        ShapedRecipe r = new ShapedRecipe(new NamespacedKey(this, k), new ItemStack(res));
        r.shape("AB"); 
        r.setIngredient('A', i1); 
        r.setIngredient('B', i2);
        Bukkit.addRecipe(r);
    }

    // --- GENERATOR ARENY (PEŁNA PULA BLOKÓW) ---
    private void generateSolidArena(World world) {
        int radius = 50;
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                world.getBlockAt(x, 20, z).setType(Material.BEDROCK);
                world.getBlockAt(x, -31, z).setType(Material.BEDROCK);
                for (int y = -30; y < 20; y++) {
                    Block b = world.getBlockAt(x, y, z);
                    double c = random.nextDouble();
                    if (c < 0.006) b.setType(Material.ANCIENT_DEBRIS);
                    else if (c < 0.05) b.setType(Material.DIAMOND_ORE);
                    else if (c < 0.12) b.setType(Material.GOLD_ORE);
                    else if (c < 0.25) b.setType(Material.IRON_ORE);
                    else if (c < 0.30) b.setType(Material.OBSIDIAN);
                    else if (c < 0.35) b.setType(Material.BOOKSHELF);
                    else if (c < 0.45) b.setType(Material.OAK_LOG);
                    else if (c < 0.55) b.setType(Material.OAK_LEAVES);
                    else if (c < 0.60) b.setType(Material.GLOWSTONE);
                    else if (c < 0.80) b.setType(Material.COAL_ORE);
                    else if (c < 0.90) b.setType(Material.LAPIS_ORE);
                    else b.setType(Material.STONE);
                }
            }
        }
    }

    @EventHandler
    public void onPvP(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Player && event.getDamager() instanceof Player) {
            ArenaData arena = arenas.get(event.getEntity().getWorld().getUID());
            if (arena != null && arena.active && arena.pvpGraceTime > 0) {
                event.setCancelled(true);
                event.getDamager().sendMessage(ChatColor.RED + "Ochrona startowa! PvP rusza za: " + arena.pvpGraceTime + "s");
            }
        }
    }

    private void handleLobbyCountdown(ArenaData arena) {
        int count = arena.world.getPlayers().size();
        if (count < 2) { arena.countdown = -1; return; }
        if (arena.countdown == -1) arena.countdown = 80;
        if (count >= 8 && arena.countdown > 15) arena.countdown = 15;

        if (arena.countdown > 0) {
            if (arena.countdown % 10 == 0 || arena.countdown <= 5)
                broadcastToWorld(arena.world, ChatColor.YELLOW + "Start za: " + ChatColor.WHITE + arena.countdown + "s");
            arena.countdown--;
        } else if (arena.countdown == 0) {
            arena.countdown = -1;
            generateSolidArena(arena.world);
            startMatch(arena);
        }
    }

    private void startMatch(ArenaData arena) {
        arena.active = true;
        arena.pvpGraceTime = 180; 
        arena.spawnPoints.clear();
        arena.world.getWorldBorder().setCenter(0, 0);
        arena.world.getWorldBorder().setSize(100);

        for (Player p : arena.world.getPlayers()) {
            Location loc = findSafeSpawn(arena);
            arena.spawnPoints.add(loc);
            for(int x=-1; x<=1; x++) for(int y=-1; y<=1; y++) for(int z=-1; z<=1; z++) 
                loc.clone().add(x,y,z).getBlock().setType(Material.AIR);
            
            p.teleport(loc.add(0.5, 0, 0.5));
            p.setGameMode(GameMode.SURVIVAL);
            p.getInventory().clear();
            p.setLevel(30);
            p.getInventory().addItem(new ItemStack(Material.STONE_PICKAXE), new ItemStack(Material.BREAD, 16));
        }
        
        broadcastToWorld(arena.world, ChatColor.GREEN + "Gra rozpoczęta! 3 minuty ochrony i spokoju borderu.");
        
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (arena.active) {
                arena.world.getWorldBorder().setSize(6, 900);
                broadcastToWorld(arena.world, ChatColor.RED + "Border zaczyna się kurczyć!");
            }
        }, 3600L); // 180s * 20t = 3600
    }

    private Location findSafeSpawn(ArenaData arena) {
        for (int i = 0; i < 200; i++) {
            Location loc = new Location(arena.world, random.nextInt(80)-40, -5, random.nextInt(80)-40);
            boolean far = true;
            for (Location o : arena.spawnPoints) if (loc.distance(o) < 30) { far = false; break; }
            if (far) return loc;
        }
        return new Location(arena.world, 0, -5, 0);
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        ArenaData arena = arenas.get(event.getBlock().getWorld().getUID());
        if (arena == null || !arena.active) return;
        
        Block b = event.getBlock();
        if (random.nextDouble() < 0.005) {
            b.setType(Material.CHEST);
            fillChest((Chest) b.getState());
            event.getPlayer().sendMessage(ChatColor.GOLD + "Znalazłeś skrzynię w bloku!");
            event.setCancelled(true);
            return;
        }
        
        Material t = b.getType();
        ItemStack d = null;
        if (t == Material.IRON_ORE || t == Material.DEEPSLATE_IRON_ORE) d = new ItemStack(Material.IRON_INGOT);
        else if (t == Material.GOLD_ORE || t == Material.DEEPSLATE_GOLD_ORE) d = new ItemStack(Material.GOLD_INGOT);
        else if (t == Material.ANCIENT_DEBRIS) d = new ItemStack(Material.NETHERITE_SCRAP);
        
        if (d != null) { 
            event.getPlayer().getInventory().addItem(d); 
            b.setType(Material.AIR); 
            event.setDropItems(false); 
        }
    }

    private void fillChest(Chest c) {
        Inventory inv = c.getInventory(); 
        inv.clear();
        Material[] loot = {
            Material.IRON_SWORD, Material.GOLDEN_APPLE, Material.DIAMOND, Material.BOW, 
            Material.ARROW, Material.SHIELD, Material.DIAMOND_PICKAXE, Material.IRON_INGOT,
            Material.GOLDEN_CARROT, Material.COOKED_BEEF, Material.POTION
        };
        int count = random.nextInt(3) + 2;
        for (int i = 0; i < count; i++) {
            Material m = loot[random.nextInt(loot.length)];
            int amt = (m == Material.ARROW || m == Material.COOKED_BEEF) ? 8 : 1;
            inv.setItem(random.nextInt(inv.getSize()), new ItemStack(m, amt));
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        Player v = e.getEntity();
        ArenaData a = arenas.get(v.getWorld().getUID());
        if (a == null || !a.active) return;
        
        e.getDrops().clear(); 
        v.getInventory().clear();
        v.setExp(0); v.setLevel(0);
        
        Bukkit.getScheduler().runTaskLater(this, () -> checkWinner(a), 1L);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        World w = Bukkit.getWorld(SPAWN_WORLD_NAME);
        if (w != null) e.setRespawnLocation(w.getSpawnLocation());
        e.getPlayer().setGameMode(GameMode.ADVENTURE);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        ArenaData a = arenas.get(p.getWorld().getUID());
        if (a != null && a.active && p.getGameMode() == GameMode.SURVIVAL) p.setHealth(0);
        removeBossBar(p);
    }

    private void checkWinner(ArenaData a) {
        List<Player> alive = a.world.getPlayers().stream()
                .filter(p -> p.getGameMode() == GameMode.SURVIVAL).collect(Collectors.toList());
        if (alive.size() <= 1 && a.active) endGame(a, alive.isEmpty() ? null : alive.get(0));
    }

    private void endGame(ArenaData a, Player winner) {
        a.active = false;
        broadcastToWorld(a.world, ChatColor.GOLD + "=== KONIEC GRY ===");
        broadcastToWorld(a.world, ChatColor.YELLOW + "Zwycięzca: " + (winner != null ? winner.getName() : "Brak"));
        
        Bukkit.getScheduler().runTaskLater(this, () -> {
            World w = Bukkit.getWorld(SPAWN_WORLD_NAME);
            Location loc = (w != null) ? w.getSpawnLocation() : a.world.getSpawnLocation();
            for (Player p : a.world.getPlayers()) {
                p.getInventory().clear(); 
                p.setExp(0); p.setLevel(0);
                p.teleport(loc); 
                p.setGameMode(GameMode.ADVENTURE);
                removeBossBar(p);
            }
        }, 100L);
    }

    private void updateActiveArena(ArenaData a) {
        for (Player p : a.world.getPlayers()) {
            if (p.getGameMode() == GameMode.SURVIVAL) {
                updateBossBar(p, a);
                updateCompass(p, a);
            } else removeBossBar(p);
        }
    }

    private void updateBossBar(Player p, ArenaData a) {
        WorldBorder b = a.world.getWorldBorder();
        double size = b.getSize() / 2;
        double dist = Math.min(Math.min((b.getCenter().getX() + size) - p.getLocation().getX(), p.getLocation().getX() - (b.getCenter().getX() - size)), 
                               Math.min((b.getCenter().getZ() + size) - p.getLocation().getZ(), p.getLocation().getZ() - (b.getCenter().getZ() - size)));
        
        BossBar bar = playerBossBars.computeIfAbsent(p.getUniqueId(), k -> Bukkit.createBossBar("Border", BarColor.GREEN, BarStyle.SOLID));
        bar.addPlayer(p);
        bar.setProgress(Math.max(0, Math.min(1, dist / 50.0)));
        bar.setColor(dist <= 10 ? BarColor.RED : BarColor.GREEN);
    }

    private void updateCompass(Player p, ArenaData a) {
        Player near = null; double dMin = 999;
        for (Player t : a.world.getPlayers()) if (!p.equals(t) && t.getGameMode() == GameMode.SURVIVAL) {
            double d = p.getLocation().distance(t.getLocation());
            if (d < dMin) { dMin = d; near = t; }
        }
        String pvpStatus = a.pvpGraceTime > 0 ? ChatColor.GREEN + "Ochrona: " + a.pvpGraceTime + "s " : ChatColor.RED + "PvP: ON ";
        if (near != null) p.sendActionBar(pvpStatus + ChatColor.GOLD + "| Najbliższy: " + near.getName() + " (" + (int)dMin + "m)");
        else p.sendActionBar(pvpStatus);
    }

    private void joinBestArena(Player p) {
        for (UUID id : registeredWorlds) {
            ArenaData a = arenas.get(id);
            if (a != null && !a.active && a.world.getPlayers().size() < 8) { 
                p.teleport(a.world.getSpawnLocation()); 
                p.setGameMode(GameMode.ADVENTURE); 
                return; 
            }
        }
        p.sendMessage(ChatColor.RED + "Brak wolnych aren!");
    }

    @Override
    public boolean onCommand(CommandSender s, Command c, String l, String[] args) {
        if (!(s instanceof Player p)) return false;
        if (c.getName().equalsIgnoreCase("cw") || c.getName().equalsIgnoreCase("cavewars")) { joinBestArena(p); return true; }
        if (!p.isOp()) return false;
        if (c.getName().equalsIgnoreCase("cwcreate")) { 
            registeredWorlds.add(p.getWorld().getUID()); 
            arenas.put(p.getWorld().getUID(), new ArenaData(p.getWorld())); 
            p.sendMessage(ChatColor.GREEN + "Arena utworzona na " + p.getWorld().getName()); 
            return true; 
        }
        return false;
    }

    private void broadcastToWorld(World w, String m) { for (Player p : w.getPlayers()) p.sendMessage(m); }
    private void removeBossBar(Player p) { BossBar b = playerBossBars.remove(p.getUniqueId()); if (b != null) b.removeAll(); }
}
