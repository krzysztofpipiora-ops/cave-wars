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
    private final List<UUID> registeredWorlds = new ArrayList<>(); // Światy z cwcreate

    private static class ArenaData {
        World world;
        Map<UUID, Integer> kills = new HashMap<>();
        boolean active = false;
        int countdown = -1; // -1 oznacza brak odliczania

        ArenaData(World world) { this.world = world; }
    }

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        registerNetheriteRecipe();
        
        // Główny Timer (Logika Aren i Odliczania)
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
        int playerCount = arena.world.getPlayers().size();

        if (playerCount < 2) {
            arena.countdown = -1;
            return;
        }

        // Pierwszy start odliczania
        if (arena.countdown == -1) arena.countdown = 80;

        // Skrócenie czasu przy pełnym serwerze (8 osób)
        if (playerCount >= 8 && arena.countdown > 15) {
            arena.countdown = 15;
            broadcastToWorld(arena.world, ChatColor.LIGHT_PURPLE + "Arena pełna! Start za 15 sekund.");
        }

        if (arena.countdown > 0) {
            if (arena.countdown % 10 == 0 || arena.countdown <= 5) {
                broadcastToWorld(arena.world, ChatColor.YELLOW + "Start gry za: " + ChatColor.WHITE + arena.countdown + "s");
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

        // Komendy Gracza: /cw lub /cavewars
        if (command.getName().equalsIgnoreCase("cw") || command.getName().equalsIgnoreCase("cavewars")) {
            joinBestArena(p);
            return true;
        }

        // Komendy Administratora
        if (!p.isOp()) return false;

        if (command.getName().equalsIgnoreCase("cwcreate")) {
            UUID worldID = p.getWorld().getUID();
            if (!registeredWorlds.contains(worldID)) {
                registeredWorlds.add(worldID);
                arenas.computeIfAbsent(worldID, k -> new ArenaData(p.getWorld()));
                p.sendMessage(ChatColor.GREEN + "Ten świat został zarejestrowany jako mapa CaveWars!");
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
        if (registeredWorlds.isEmpty()) {
            p.sendMessage(ChatColor.RED + "Brak skonfigurowanych aren! (Użyj /cwcreate)");
            return;
        }

        for (UUID worldID : registeredWorlds) {
            World world = Bukkit.getWorld(worldID);
            if (world == null) continue;

            ArenaData arena = arenas.get(worldID);
            int count = world.getPlayers().size();

            // Szukaj areny, która nie trwa i ma miejsce (max 8)
            if (arena != null && !arena.active && count < 8) {
                p.teleport(world.getSpawnLocation());
                p.setGameMode(GameMode.ADVENTURE);
                p.sendMessage(ChatColor.GREEN + "Dołączyłeś do areny na świecie: " + world.getName());
                p.sendMessage(ChatColor.GRAY + "Graczy: " + (count + 1) + "/8");
                return;
            }
        }
        p.sendMessage(ChatColor.RED + "Wszystkie areny są obecnie pełne lub w trakcie gry!");
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
            p.getInventory().addItem(new ItemStack(Material.STONE_PICKAXE), new ItemStack(Material.STONE_AXE), 
                                     new ItemStack(Material.BREAD, 32), new ItemStack(Material.CRAFTING_TABLE));
            
            ItemStack tracker = new ItemStack(Material.COMPASS);
            ItemMeta m = tracker.getItemMeta(); m.setDisplayName(ChatColor.RED + "Wykrywacz Graczy");
            tracker.setItemMeta(m); p.getInventory().addItem(tracker);
        }
        broadcastToWorld(arena.world, ChatColor.GREEN + "GRA WYSTARTOWAŁA!");
        Bukkit.getScheduler().runTaskLater(this, () -> arena.world.getWorldBorder().setSize(6, 900), 1200L);
    }

    // --- LOGIKA MECHANIK PODCZAS GRY ---

    private void updateActiveArena(ArenaData arena) {
        for (Player p : arena.world.getPlayers()) {
            if (p.getGameMode() != GameMode.SURVIVAL) { removeBossBar(p); continue; }
            if (p.getLevel() != 30) p.setLevel(30);
            
            // Kompas & BossBar
            updateCompass(p, arena);
            updateBossBar(p, arena);
        }
        
        // Czyszczenie BossBarów osób spoza świata
        playerBossBars.entrySet().removeIf(entry -> {
            Player p = Bukkit.getPlayer(entry.getKey());
            if (p == null || !p.getWorld().equals(arena.world)) {
                entry.getValue().removeAll();
                return true;
            }
            return false;
        });
    }

    private void endGame(ArenaData arena, Player winner) {
        arena.active = false;
        String winnerName = (winner != null) ? winner.getName() : "Brak";
        
        broadcastToWorld(arena.world, ChatColor.GOLD + "=== KONIEC ARENY ===");
        broadcastToWorld(arena.world, ChatColor.YELLOW + "Zwycięzca: " + winnerName);

        Bukkit.getScheduler().runTaskLater(this, () -> {
            Location spawn = Bukkit.getWorlds().get(0).getSpawnLocation();
            for (Player p : arena.world.getPlayers()) {
                p.teleport(spawn);
                p.setGameMode(GameMode.ADVENTURE);
                p.getInventory().clear();
                removeBossBar(p);
            }
        }, 100L);
    }

    // --- METODY POMOCNICZE (RECEPTURY, GENERATOR, EVENTY) ---

    private void registerNetheriteRecipe() {
        ItemStack result = new ItemStack(Material.NETHERITE_INGOT);
        NamespacedKey key = new NamespacedKey(this, "custom_netherite_ingot");
        ShapedRecipe recipe = new ShapedRecipe(key, result);
        recipe.shape("DDD", "DSD", "DDD");
        recipe.setIngredient('D', Material.DIAMOND);
        recipe.setIngredient('S', Material.NETHERITE_SCRAP);
        Bukkit.addRecipe(recipe);
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
                    else if (chance < 0.58) block.setType(Material.OAK_LOG);
                    else if (chance < 0.68) block.setType(Material.OAK_LEAVES);
                    else if (chance < 0.88) block.setType(Material.COAL_ORE);
                    else block.setType(Material.LAPIS_ORE);
                }
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

        Bukkit.getScheduler().runTaskLater(this, () -> {
            long alive = arena.world.getPlayers().stream().filter(p -> p.getGameMode() == GameMode.SURVIVAL).count();
            if (alive <= 1) endGame(arena, arena.world.getPlayers().stream().filter(p -> p.getGameMode() == GameMode.SURVIVAL).findFirst().orElse(null));
        }, 1L);
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        ArenaData arena = arenas.get(event.getBlock().getWorld().getUID());
        if (arena == null || !arena.active) return;
        Block b = event.getBlock(); Material t = b.getType();
        ItemStack drop = null;
        if (t == Material.IRON_ORE || t == Material.DEEPSLATE_IRON_ORE) drop = new ItemStack(Material.IRON_INGOT);
        else if (t == Material.GOLD_ORE || t == Material.DEEPSLATE_GOLD_ORE) drop = new ItemStack(Material.GOLD_INGOT);
        else if (t == Material.ANCIENT_DEBRIS) drop = new ItemStack(Material.NETHERITE_SCRAP);
        else if (t == Material.OAK_LEAVES && random.nextDouble() < 0.3) drop = new ItemStack(Material.APPLE);
        if (drop != null) { event.getPlayer().getInventory().addItem(drop); event.setDropItems(false); }
    }

    private void updateCompass(Player p, ArenaData arena) {
        Player nearest = null; double dMin = Double.MAX_VALUE;
        for (Player target : arena.world.getPlayers()) {
            if (p.equals(target) || target.getGameMode() != GameMode.SURVIVAL) continue;
            double d = p.getLocation().distance(target.getLocation());
            if (d < dMin) { dMin = d; nearest = target; }
        }
        if (nearest != null) {
            p.setCompassTarget(nearest.getLocation());
            p.sendActionBar(ChatColor.GOLD + "Cel: " + ChatColor.WHITE + nearest.getName() + " (" + (int)dMin + "m)");
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
    }

    private void broadcastToWorld(World world, String msg) {
        for (Player p : world.getPlayers()) p.sendMessage(msg);
    }

    private void removeBossBar(Player p) {
        BossBar b = playerBossBars.remove(p.getUniqueId());
        if (b != null) b.removeAll();
    }
}
