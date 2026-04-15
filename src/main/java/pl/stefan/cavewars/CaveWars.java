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
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public class CaveWars extends JavaPlugin implements Listener {

    private final Random random = new Random();
    private final HashMap<UUID, BossBar> playerBossBars = new HashMap<>();
    // Mapa przechowująca aktywny świat gry, aby inne eventy wiedziały, gdzie działa logika
    private World activeGameWorld = null;

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        registerNetheriteRecipe();

        Bukkit.getScheduler().runTaskTimer(this, () -> {
            if (activeGameWorld != null) {
                updateBorderBossBar();
                maintainLevelThirty();
                updateCompassTarget();
            }
        }, 20L, 20L);
        
        getLogger().info("CaveWars (Multi-World Support) gotowy!");
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
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (command.getName().equalsIgnoreCase("cwstart") && sender.isOp()) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("Tę komendę musi wpisać gracz będący w świecie, który ma stać się areną!");
                return true;
            }

            Player admin = (Player) sender;
            activeGameWorld = admin.getWorld(); // Pobieramy świat, w którym stoi admin
            
            sender.sendMessage(ChatColor.GREEN + "Uruchamiam CaveWars w świecie: " + activeGameWorld.getName());
            
            generateSolidArena(activeGameWorld);
            startMatchInAirRooms(activeGameWorld);
            return true;
        }
        return false;
    }

    private void generateSolidArena(World world) {
        int radius = 50; 
        int ceilingY = 20; 
        int floorY = -30;
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                world.getBlockAt(x, ceilingY, z).setType(Material.BEDROCK);
                world.getBlockAt(x, floorY - 1, z).setType(Material.BEDROCK);
                for (int y = floorY; y < ceilingY; y++) {
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

    private void startMatchInAirRooms(World world) {
        WorldBorder border = world.getWorldBorder();
        border.setCenter(0, 0);
        border.setSize(100); 

        int playersJoined = 0;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!p.getWorld().equals(world)) continue;

            playersJoined++;
            int x = random.nextInt(60) - 30;
            int z = random.nextInt(60) - 30;
            int y = -5;

            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        world.getBlockAt(x + dx, y + dy, z + dz).setType(Material.AIR);
                    }
                }
            }

            p.teleport(new Location(world, x + 0.5, y - 0.5, z + 0.5));
            p.setGameMode(GameMode.SURVIVAL);
            p.getInventory().clear();
            p.getInventory().setArmorContents(null);
            
            // Podstawowy zestaw
            p.getInventory().addItem(new ItemStack(Material.STONE_PICKAXE));
            p.getInventory().addItem(new ItemStack(Material.STONE_AXE));
            p.getInventory().addItem(new ItemStack(Material.BREAD, 32));
            p.getInventory().addItem(new ItemStack(Material.CRAFTING_TABLE));
            
            // Kompas
            ItemStack tracker = new ItemStack(Material.COMPASS);
            ItemMeta meta = tracker.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(ChatColor.RED + "Wykrywacz Graczy");
                tracker.setItemMeta(meta);
            }
            p.getInventory().addItem(tracker);
        }

        broadcastToWorld(world, ChatColor.YELLOW + "Zaczynamy! Graczy: " + playersJoined);

        Bukkit.getScheduler().runTaskLater(this, () -> {
            border.setSize(6, 900);
            broadcastToWorld(world, ChatColor.RED + "⚠ Border ruszył!");
        }, 1200L);
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (activeGameWorld == null || !event.getBlock().getWorld().equals(activeGameWorld)) return;
        
        Block b = event.getBlock();
        Player p = event.getPlayer();
        Material type = b.getType();
        ItemStack itemToAdd = null;

        if (type == Material.IRON_ORE || type == Material.DEEPSLATE_IRON_ORE) itemToAdd = new ItemStack(Material.IRON_INGOT);
        else if (type == Material.GOLD_ORE || type == Material.DEEPSLATE_GOLD_ORE) itemToAdd = new ItemStack(Material.GOLD_INGOT);
        else if (type == Material.ANCIENT_DEBRIS) itemToAdd = new ItemStack(Material.NETHERITE_SCRAP);
        else if (type == Material.OAK_LEAVES && random.nextDouble() < 0.33) itemToAdd = new ItemStack(Material.APPLE);
        else {
            for (ItemStack drop : b.getDrops(p.getInventory().getItemInMainHand())) {
                addItemToPlayer(p, drop, b.getLocation());
            }
            event.setDropItems(false);
            return;
        }

        if (itemToAdd != null) addItemToPlayer(p, itemToAdd, b.getLocation());
        event.setDropItems(false);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player p = event.getEntity();
        if (activeGameWorld != null && p.getWorld().equals(activeGameWorld)) {
            removeBossBar(p);
            event.setDeathMessage(null);
            broadcastToWorld(activeGameWorld, ChatColor.RED + "☠ " + p.getName() + " odpadł!");
        }
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Player p = event.getPlayer();
        if (activeGameWorld != null && event.getRespawnLocation().getWorld().equals(activeGameWorld)) {
            // Respawn do lobby (pierwszy świat na liście serwera)
            event.setRespawnLocation(Bukkit.getWorlds().get(0).getSpawnLocation());
            Bukkit.getScheduler().runTaskLater(this, () -> {
                p.setGameMode(GameMode.ADVENTURE);
                p.getInventory().clear();
            }, 5L);
        }
    }

    private void updateCompassTarget() {
        if (activeGameWorld == null) return;
        for (Player p : activeGameWorld.getPlayers()) {
            if (p.getGameMode() != GameMode.SURVIVAL) continue;
            Player nearest = null;
            double nearestDist = Double.MAX_VALUE;
            for (Player target : activeGameWorld.getPlayers()) {
                if (p.equals(target) || target.getGameMode() != GameMode.SURVIVAL) continue;
                double dist = p.getLocation().distance(target.getLocation());
                if (dist < nearestDist) {
                    nearestDist = dist;
                    nearest = target;
                }
            }
            if (nearest != null) {
                p.setCompassTarget(nearest.getLocation());
                p.sendActionBar(ChatColor.GOLD + "Najbliższy: " + ChatColor.WHITE + nearest.getName() + " (" + (int)nearestDist + "m)");
            }
        }
    }

    private void updateBorderBossBar() {
        if (activeGameWorld == null) return;
        for (Player p : activeGameWorld.getPlayers()) {
            WorldBorder border = activeGameWorld.getWorldBorder();
            double size = border.getSize() / 2;
            Location loc = p.getLocation();
            Location center = border.getCenter();
            double minDist = Math.min(Math.min((center.getX() + size) - loc.getX(), loc.getX() - (center.getX() - size)), 
                                     Math.min((center.getZ() + size) - loc.getZ(), loc.getZ() - (center.getZ() - size)));
            
            BossBar bar = playerBossBars.computeIfAbsent(p.getUniqueId(), k -> 
                Bukkit.createBossBar(ChatColor.WHITE + "Dystans do borderu", BarColor.GREEN, BarStyle.SOLID));
            bar.addPlayer(p);
            bar.setProgress(Math.max(0, Math.min(1, minDist / 50.0)));
            bar.setColor(minDist <= 10 ? BarColor.RED : BarColor.GREEN);
        }
    }

    private void maintainLevelThirty() {
        if (activeGameWorld == null) return;
        for (Player p : activeGameWorld.getPlayers()) {
            if (p.getGameMode() == GameMode.SURVIVAL && p.getLevel() != 30) p.setLevel(30);
        }
    }

    private void broadcastToWorld(World world, String message) {
        for (Player p : world.getPlayers()) p.sendMessage(message);
    }

    private void addItemToPlayer(Player p, ItemStack item, Location loc) {
        Map<Integer, ItemStack> leftOver = p.getInventory().addItem(item);
        if (!leftOver.isEmpty()) {
            for (ItemStack is : leftOver.values()) loc.getWorld().dropItemNaturally(loc, is);
        }
    }

    @EventHandler
    public void onLeavesDecay(LeavesDecayEvent event) {
        if (activeGameWorld != null && event.getBlock().getWorld().equals(activeGameWorld)) event.setCancelled(true);
    }

    private void removeBossBar(Player p) {
        BossBar bar = playerBossBars.remove(p.getUniqueId());
        if (bar != null) bar.removeAll();
    }
}
