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
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.stream.Collectors;

public class CaveWars extends JavaPlugin implements Listener {

    private final Random random = new Random();
    private final Map<UUID, ArenaData> arenas = new HashMap<>();
    private final Map<UUID, BossBar> playerBossBars = new HashMap<>();
    private final List<UUID> registeredWorlds = new ArrayList<>();
    private final String SPAWN_WORLD_NAME = "world"; // Twoja nazwa świata spawna

    private static class ArenaData {
        World world;
        Map<UUID, Integer> kills = new HashMap<>();
        boolean active = false;
        int countdown = -1;
        ArenaData(World world) { this.world = world; }
    }

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        registerNetheriteRecipe();
        
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (ArenaData arena : arenas.values()) {
                if (arena.active) {
                    updateActiveArena(arena);
                } else if (registeredWorlds.contains(arena.world.getUID())) {
                    handleLobbyCountdown(arena);
                }
            }
        }, 20L, 20L);
    }

    private void handleLobbyCountdown(ArenaData arena) {
        int count = arena.world.getPlayers().size();
        if (count < 2) {
            arena.countdown = -1;
            return;
        }

        if (arena.countdown == -1) arena.countdown = 80;
        if (count >= 8 && arena.countdown > 15) {
            arena.countdown = 15;
            broadcastToWorld(arena.world, ChatColor.LIGHT_PURPLE + "Arena pełna! Start za 15 sekund.");
        }

        if (arena.countdown > 0) {
            if (arena.countdown % 10 == 0 || arena.countdown <= 5) {
                broadcastToWorld(arena.world, ChatColor.YELLOW + "Start za: " + ChatColor.WHITE + arena.countdown + "s");
            }
            arena.countdown--;
        } else if (arena.countdown == 0) {
            arena.countdown = -1;
            generateSolidArena(arena.world);
            startMatch(arena);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) return false;

        if (command.getName().equalsIgnoreCase("cw") || command.getName().equalsIgnoreCase("cavewars")) {
            joinBestArena(p);
            return true;
        }

        if (!p.isOp()) return false;

        if (command.getName().equalsIgnoreCase("cwcreate")) {
            UUID id = p.getWorld().getUID();
            if (!registeredWorlds.contains(id)) {
                registeredWorlds.add(id);
                arenas.put(id, new ArenaData(p.getWorld()));
                p.sendMessage(ChatColor.GREEN + "Świat " + p.getWorld().getName() + " zarejestrowany!");
            }
            return true;
        }

        if (command.getName().equalsIgnoreCase("cwstop")) {
            ArenaData arena = arenas.get(p.getWorld().getUID());
            if (arena != null && arena.active) endGame(arena, null);
            return true;
        }
        return false;
    }

    private void joinBestArena(Player p) {
        for (UUID id : registeredWorlds) {
            ArenaData arena = arenas.get(id);
            if (arena != null && !arena.active && arena.world.getPlayers().size() < 8) {
                p.teleport(arena.world.getSpawnLocation());
                p.setGameMode(GameMode.ADVENTURE);
                p.sendMessage(ChatColor.GREEN + "Dołączyłeś do " + arena.world.getName());
                return;
            }
        }
        p.sendMessage(ChatColor.RED + "Brak wolnych miejsc!");
    }

    private void generateSolidArena(World world) {
        int radius = 50;
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                world.getBlockAt(x, 20, z).setType(Material.BEDROCK);
                world.getBlockAt(x, -31, z).setType(Material.BEDROCK);
                for (int y = -30; y < 20; y++) {
                    Block block = world.getBlockAt(x, y, z);
                    double chance = random.nextDouble();
                    
                    if (chance < 0.015) { 
                        block.setType(Material.CHEST);
                        fillChest((Chest) block.getState());
                    } 
                    else if (chance < 0.12) block.setType(Material.IRON_ORE);
                    else if (chance < 0.20) block.setType(Material.GOLD_ORE);
                    else if (chance < 0.25) block.setType(Material.DIAMOND_ORE);
                    else if (chance < 0.30) block.setType(Material.ANCIENT_DEBRIS);
                    else if (chance < 0.45) block.setType(Material.OAK_LOG);
                    else block.setType(Material.COAL_ORE);
                }
            }
        }
    }

    private void fillChest(Chest chest) {
        Inventory inv = chest.getInventory();
        Material[] loot = {
            Material.IRON_SWORD, Material.GOLDEN_APPLE, Material.DIAMOND, Material.IRON_CHESTPLATE,
            Material.BOW, Material.ARROW, Material.GOLDEN_CARROT, Material.BREAD, 
            Material.SHIELD, Material.DIAMOND_PICKAXE, Material.EXPERIENCE_BOTTLE, 
            Material.POTION, Material.SPLASH_POTION, Material.IRON_INGOT
        };

        int itemsCount = random.nextInt(4) + 3; 
        for (int i = 0; i < itemsCount; i++) {
            inv.setItem(random.nextInt(inv.getSize()), new ItemStack(loot[random.nextInt(loot.length)], random.nextInt(3) + 1));
        }
    }

    private void startMatch(ArenaData arena) {
        arena.active = true;
        arena.kills.clear();
        arena.world.getWorldBorder().setCenter(0, 0);
        arena.world.getWorldBorder().setSize(100);

        for (Player p : arena.world.getPlayers()) {
            arena.kills.put(p.getUniqueId(), 0);
            Location loc = new Location(arena.world, random.nextInt(60)-30, -5, random.nextInt(60)-30);
            
            for(int x=-1; x<=1; x++) for(int y=-1; y<=1; y++) for(int z=-1; z<=1; z++) 
                loc.clone().add(x,y,z).getBlock().setType(Material.AIR);

            p.teleport(loc.add(0.5, -0.5, 0.5));
            p.setGameMode(GameMode.SURVIVAL);
            p.getInventory().clear();
            p.setExp(0); p.setLevel(30);
            p.getInventory().addItem(new ItemStack(Material.STONE_PICKAXE), new ItemStack(Material.BREAD, 16));
        }
        Bukkit.getScheduler().runTaskLater(this, () -> arena.world.getWorldBorder().setSize(6, 900), 1200L);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        ArenaData arena = arenas.get(victim.getWorld().getUID());
        if (arena == null || !arena.active) return;

        event.getDrops().clear(); 
        victim.getInventory().clear();
        victim.setExp(0); victim.setLevel(0);
        
        Player killer = victim.getKiller();
        if (killer != null) arena.kills.put(killer.getUniqueId(), arena.kills.getOrDefault(killer.getUniqueId(), 0) + 1);

        Bukkit.getScheduler().runTaskLater(this, () -> checkWinner(arena), 1L);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        World spawnWorld = Bukkit.getWorld(SPAWN_WORLD_NAME);
        if (spawnWorld != null) {
            event.setRespawnLocation(spawnWorld.getSpawnLocation());
        }
        event.getPlayer().setGameMode(GameMode.ADVENTURE);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player p = event.getPlayer();
        ArenaData arena = arenas.get(p.getWorld().getUID());
        if (arena != null && arena.active && p.getGameMode() == GameMode.SURVIVAL) {
            p.setHealth(0); 
        }
        removeBossBar(p);
    }

    private void checkWinner(ArenaData arena) {
        List<Player> alive = arena.world.getPlayers().stream()
                .filter(p -> p.getGameMode() == GameMode.SURVIVAL).collect(Collectors.toList());
        
        if (alive.size() <= 1 && arena.active) {
            endGame(arena, alive.isEmpty() ? null : alive.get(0));
        }
    }

    private void endGame(ArenaData arena, Player winner) {
        if (!arena.active) return;
        arena.active = false;
        
        String name = (winner != null) ? winner.getName() : "Brak";
        broadcastToWorld(arena.world, ChatColor.GOLD + "WYGRANA: " + ChatColor.WHITE + name);
        
        List<Map.Entry<UUID, Integer>> top = arena.kills.entrySet().stream()
                .sorted((a, b) -> b.getValue().compareTo(a.getValue())).limit(3).collect(Collectors.toList());

        for (Player p : arena.world.getPlayers()) {
            p.sendMessage(ChatColor.AQUA + "--- TOP ZABÓJSTW ---");
            for (int i = 0; i < top.size(); i++) {
                p.sendMessage((i+1) + ". " + Bukkit.getOfflinePlayer(top.get(i).getKey()).getName() + " - " + top.get(i).getValue());
            }
        }

        Bukkit.getScheduler().runTaskLater(this, () -> {
            World spawnWorld = Bukkit.getWorld(SPAWN_WORLD_NAME);
            Location spawnLoc = (spawnWorld != null) ? spawnWorld.getSpawnLocation() : arena.world.getSpawnLocation();
            
            for (Player p : arena.world.getPlayers()) {
                p.getInventory().clear();
                p.setExp(0); p.setLevel(0);
                p.teleport(spawnLoc);
                p.setGameMode(GameMode.ADVENTURE);
                removeBossBar(p);
            }
        }, 100L); 
    }

    private void updateActiveArena(ArenaData arena) {
        for (Player p : arena.world.getPlayers()) {
            if (p.getGameMode() != GameMode.SURVIVAL) {
                removeBossBar(p);
                continue;
            }
            if (p.getLevel() != 30) p.setLevel(30);
            updateCompass(p, arena);
            updateBossBar(p, arena);
        }
    }

    private void updateCompass(Player p, ArenaData arena) {
        Player near = null; double dMin = Double.MAX_VALUE;
        for (Player t : arena.world.getPlayers()) {
            if (!p.equals(t) && t.getGameMode() == GameMode.SURVIVAL) {
                double d = p.getLocation().distance(t.getLocation());
                if (d < dMin) { dMin = d; near = t; }
            }
        }
        if (near != null) {
            p.setCompassTarget(near.getLocation());
            p.sendActionBar(ChatColor.GOLD + "Najbliższy gracz: " + ChatColor.WHITE + near.getName() + " (" + (int)dMin + "m)");
        }
    }

    private void updateBossBar(Player p, ArenaData arena) {
        WorldBorder b = arena.world.getWorldBorder();
        double size = b.getSize() / 2;
        Location l = p.getLocation();
        double dist = Math.min(Math.min((b.getCenter().getX() + size) - l.getX(), l.getX() - (b.getCenter().getX() - size)), 
                               Math.min((b.getCenter().getZ() + size) - l.getZ(), l.getZ() - (b.getCenter().getZ() - size)));
        
        BossBar bar = playerBossBars.computeIfAbsent(p.getUniqueId(), k -> Bukkit.createBossBar("Border", BarColor.GREEN, BarStyle.SOLID));
        bar.addPlayer(p);
        bar.setProgress(Math.max(0, Math.min(1, dist / 50.0)));
        bar.setColor(dist <= 10 ? BarColor.RED : BarColor.GREEN);
        bar.setTitle(ChatColor.WHITE + "Granica za: " + (int)dist + "m");
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        ArenaData arena = arenas.get(event.getBlock().getWorld().getUID());
        if (arena == null || !arena.active) return;
        
        Block b = event.getBlock();
        if (b.getType() == Material.CHEST) return; 

        Material t = b.getType();
        ItemStack drop = null;
        if (t == Material.IRON_ORE || t == Material.DEEPSLATE_IRON_ORE) drop = new ItemStack(Material.IRON_INGOT);
        else if (t == Material.GOLD_ORE || t == Material.DEEPSLATE_GOLD_ORE) drop = new ItemStack(Material.GOLD_INGOT);
        else if (t == Material.ANCIENT_DEBRIS) drop = new ItemStack(Material.NETHERITE_SCRAP);
        else if (t == Material.OAK_LEAVES && random.nextDouble() < 0.2) drop = new ItemStack(Material.APPLE);
        
        if (drop != null) {
            event.getPlayer().getInventory().addItem(drop);
            event.setDropItems(false);
        }
    }

    private void registerNetheriteRecipe() {
        ItemStack result = new ItemStack(Material.NETHERITE_INGOT);
        NamespacedKey key = new NamespacedKey(this, "custom_netherite_ing");
        ShapedRecipe recipe = new ShapedRecipe(key, result);
        recipe.shape("DDD", "DSD", "DDD");
        recipe.setIngredient('D', Material.DIAMOND);
        recipe.setIngredient('S', Material.NETHERITE_SCRAP);
        Bukkit.addRecipe(recipe);
    }

    private void broadcastToWorld(World w, String m) {
        for (Player p : w.getPlayers()) p.sendMessage(m);
    }

    private void removeBossBar(Player p) {
        BossBar b = playerBossBars.remove(p.getUniqueId());
        if (b != null) b.removeAll();
    }
}
