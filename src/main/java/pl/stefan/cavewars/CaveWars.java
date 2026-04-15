package pl.stefan.cavewars;

import org.bukkit.*;
import org.bukkit.block.Block;
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

    private static class ArenaData {
        World world;
        Map<UUID, Integer> kills = new HashMap<>();
        boolean active = false;
        ArenaData(World world) { this.world = world; }
    }

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        registerNetheriteRecipe();
        
        // Główny procesor wszystkich aktywnych aren (20 ticków = 1 sekunda)
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (ArenaData arena : arenas.values()) {
                if (arena.active) updateArena(arena);
            }
        }, 20L, 20L);
        
        getLogger().info("CaveWars (Multi-Arena & Clean Bar) gotowy!");
    }

    private void registerNetheriteRecipe() {
        ItemStack result = new ItemStack(Material.NETHERITE_INGOT);
        NamespacedKey key = new NamespacedKey(this, "custom_netherite_ingot");
        ShapedRecipe recipe = new ShapedRecipe(key, result);
        recipe.shape("DDD", "DSD", "DDD");
        recipe.setIngredient('D', Material.DIAMOND);
        recipe.setIngredient('S', Material.NETHERITE_SCRAP);
        Bukkit.addRecipe(recipe);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p) || !p.isOp()) return false;

        if (command.getName().equalsIgnoreCase("cwstart")) {
            World world = p.getWorld();
            ArenaData arena = arenas.computeIfAbsent(world.getUID(), k -> new ArenaData(world));
            if (arena.active) {
                p.sendMessage(ChatColor.RED + "Gra w tym świecie już trwa!");
                return true;
            }
            p.sendMessage(ChatColor.GREEN + "Generowanie areny i startowanie gry...");
            generateSolidArena(world);
            startMatch(arena);
            return true;
        }

        if (command.getName().equalsIgnoreCase("cwstop")) {
            ArenaData arena = arenas.get(p.getWorld().getUID());
            if (arena == null || !arena.active) {
                p.sendMessage(ChatColor.RED + "W tym świecie nie ma aktywnej gry.");
                return true;
            }
            endGame(arena, null);
            return true;
        }
        return false;
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
                    if (chance < 0.006) block.setType(Material.ANCIENT_DEBRIS);
                    else if (chance < 0.07) block.setType(Material.DIAMOND_ORE);
                    else if (chance < 0.17) block.setType(Material.GOLD_ORE);
                    else if (chance < 0.38) block.setType(Material.IRON_ORE);
                    else if (chance < 0.43) block.setType(Material.OBSIDIAN);
                    else if (chance < 0.48) block.setType(Material.BOOKSHELF);
                    else if (chance < 0.58) block.setType(Material.OAK_LOG);
                    else if (chance < 0.68) block.setType(Material.OAK_LEAVES);
                    else if (chance < 0.73) block.setType(Material.GLOWSTONE);
                    else if (chance < 0.88) block.setType(Material.COAL_ORE);
                    else block.setType(Material.LAPIS_ORE);
                }
            }
        }
    }

    private void startMatch(ArenaData arena) {
        arena.active = true;
        arena.kills.clear();
        arena.world.getWorldBorder().setCenter(0, 0);
        arena.world.getWorldBorder().setSize(100);

        for (Player p : arena.world.getPlayers()) {
            if (p.getGameMode() == GameMode.SPECTATOR) continue;

            arena.kills.put(p.getUniqueId(), 0);
            
            // Pusta przestrzeń 3x3x3
            Location loc = new Location(arena.world, random.nextInt(60)-30, -5, random.nextInt(60)-30);
            for(int x=-1; x<=1; x++) for(int y=-1; y<=1; y++) for(int z=-1; z<=1; z++) 
                loc.clone().add(x,y,z).getBlock().setType(Material.AIR);

            p.teleport(loc.add(0.5, -0.5, 0.5));
            p.setGameMode(GameMode.SURVIVAL);
            p.getInventory().clear();
            p.getInventory().addItem(new ItemStack(Material.STONE_PICKAXE), new ItemStack(Material.STONE_AXE), 
                                     new ItemStack(Material.BREAD, 32), new ItemStack(Material.CRAFTING_TABLE));
            
            ItemStack tracker = new ItemStack(Material.COMPASS);
            ItemMeta m = tracker.getItemMeta();
            m.setDisplayName(ChatColor.RED + "Wykrywacz Graczy");
            tracker.setItemMeta(m); 
            p.getInventory().addItem(tracker);
        }
        Bukkit.getScheduler().runTaskLater(this, () -> arena.world.getWorldBorder().setSize(6, 900), 1200L);
    }

    private void updateArena(ArenaData arena) {
        List<Player> players = arena.world.getPlayers();
        
        for (Player p : players) {
            if (p.getGameMode() != GameMode.SURVIVAL) {
                removeBossBar(p);
                continue;
            }
            if (p.getLevel() != 30) p.setLevel(30);
            
            // --- KOMPAS ---
            Player nearest = null; double dMin = Double.MAX_VALUE;
            for (Player target : players) {
                if (p.equals(target) || target.getGameMode() != GameMode.SURVIVAL) continue;
                double d = p.getLocation().distance(target.getLocation());
                if (d < dMin) { dMin = d; nearest = target; }
            }
            if (nearest != null) {
                p.setCompassTarget(nearest.getLocation());
                p.sendActionBar(ChatColor.GOLD + "Cel: " + ChatColor.WHITE + nearest.getName() + " (" + (int)dMin + "m)");
            }

            // --- BOSSBAR (Naprawione duplikaty) ---
            WorldBorder b = arena.world.getWorldBorder();
            double size = b.getSize() / 2;
            Location l = p.getLocation();
            double dist = Math.min(Math.min((b.getCenter().getX() + size) - l.getX(), l.getX() - (b.getCenter().getX() - size)), 
                                   Math.min((b.getCenter().getZ() + size) - l.getZ(), l.getZ() - (b.getCenter().getZ() - size)));
            
            BossBar bar = playerBossBars.computeIfAbsent(p.getUniqueId(), k -> Bukkit.createBossBar("Border", BarColor.GREEN, BarStyle.SOLID));
            bar.addPlayer(p);
            bar.setProgress(Math.max(0, Math.min(1, dist / 50.0)));
            bar.setColor(dist <= 10 ? BarColor.RED : BarColor.GREEN);
            bar.setTitle(ChatColor.WHITE + "Dystans do borderu: " + (dist <= 10 ? ChatColor.RED : ChatColor.GREEN) + (int)dist + "m");
        }

        // Czyszczenie pasków graczy, którzy opuścili ten świat
        Iterator<Map.Entry<UUID, BossBar>> it = playerBossBars.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, BossBar> entry = it.next();
            Player p = Bukkit.getPlayer(entry.getKey());
            if (p == null || !p.getWorld().equals(arena.world)) {
                entry.getValue().removeAll();
                it.remove();
            }
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        ArenaData arena = arenas.get(victim.getWorld().getUID());
        if (arena == null || !arena.active) return;

        event.setDeathMessage(null);
        removeBossBar(victim);
        
        Player killer = victim.getKiller();
        if (killer != null) arena.kills.put(killer.getUniqueId(), arena.kills.getOrDefault(killer.getUniqueId(), 0) + 1);

        Bukkit.getScheduler().runTaskLater(this, () -> checkWinner(arena), 1L);
    }

    private void checkWinner(ArenaData arena) {
        List<Player> alive = arena.world.getPlayers().stream()
                .filter(p -> p.getGameMode() == GameMode.SURVIVAL).collect(Collectors.toList());
        if (alive.size() <= 1 && arena.active) endGame(arena, alive.isEmpty() ? null : alive.get(0));
    }

    private void endGame(ArenaData arena, Player winner) {
        if (!arena.active) return;
        arena.active = false;
        
        String winnerName = (winner != null) ? winner.getName() : "Brak";
        List<Map.Entry<UUID, Integer>> top = arena.kills.entrySet().stream()
                .sorted((a, b) -> b.getValue().compareTo(a.getValue())).limit(3).collect(Collectors.toList());

        for (Player p : arena.world.getPlayers()) {
            p.sendMessage(ChatColor.GOLD + "=== KONIEC ARENY ===");
            p.sendMessage(ChatColor.YELLOW + "Zwycięzca: " + ChatColor.WHITE + winnerName);
            for (int i = 0; i < top.size(); i++) {
                String name = Bukkit.getOfflinePlayer(top.get(i).getKey()).getName();
                p.sendMessage(ChatColor.GRAY + "" + (i+1) + ". " + name + " - " + top.get(i).getValue() + " zabójstw");
            }
        }

        Bukkit.getScheduler().runTaskLater(this, () -> {
            Location spawn = Bukkit.getWorlds().get(0).getSpawnLocation();
            for (Player p : arena.world.getPlayers()) {
                p.teleport(spawn);
                p.setGameMode(GameMode.ADVENTURE);
                p.getInventory().clear();
                removeBossBar(p);
            }
        }, 100L); // 100 ticków = 5 sekund
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        ArenaData arena = arenas.get(event.getBlock().getWorld().getUID());
        if (arena == null || !arena.active) return;

        Block b = event.getBlock(); Player p = event.getPlayer(); Material t = b.getType();
        ItemStack drop = null;

        if (t == Material.IRON_ORE || t == Material.DEEPSLATE_IRON_ORE) drop = new ItemStack(Material.IRON_INGOT);
        else if (t == Material.GOLD_ORE || t == Material.DEEPSLATE_GOLD_ORE) drop = new ItemStack(Material.GOLD_INGOT);
        else if (t == Material.ANCIENT_DEBRIS) drop = new ItemStack(Material.NETHERITE_SCRAP);
        else if (t == Material.OAK_LEAVES && random.nextDouble() < 0.3) drop = new ItemStack(Material.APPLE);
        
        if (drop != null) {
            p.getInventory().addItem(drop);
            event.setDropItems(false);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        removeBossBar(event.getPlayer());
    }

    private void removeBossBar(Player p) {
        BossBar b = playerBossBars.remove(p.getUniqueId());
        if (b != null) b.removeAll();
    }
}
