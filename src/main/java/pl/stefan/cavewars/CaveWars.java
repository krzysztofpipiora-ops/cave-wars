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
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public class CaveWars extends JavaPlugin implements Listener {

    private final Random random = new Random();
    private final HashMap<UUID, BossBar> playerBossBars = new HashMap<>();
    private final String WORLD_NAME = "cavewars";

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            updateBorderBossBar();
            maintainLevelThirty();
        }, 20L, 20L);
        getLogger().info("CaveWars 1.21.4 (Auto-Pickup & Hardcore) gotowy!");
    }

    // --- MECHANIZM AUTO-PICKUP ---
    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player p = event.getPlayer();
        Block b = event.getBlock();
        
        // Działaj tylko na świecie areny
        if (!b.getWorld().getName().equalsIgnoreCase(WORLD_NAME)) return;

        Material type = b.getType();
        ItemStack itemToAdd = null;

        // 1. Logika specjalnych dropów (Auto-Smelt)
        if (type == Material.IRON_ORE || type == Material.DEEPSLATE_IRON_ORE) {
            itemToAdd = new ItemStack(Material.IRON_INGOT);
        } else if (type == Material.GOLD_ORE || type == Material.DEEPSLATE_GOLD_ORE) {
            itemToAdd = new ItemStack(Material.GOLD_INGOT);
        } else if (type == Material.OAK_LEAVES) {
            if (random.nextDouble() < 0.33) {
                itemToAdd = new ItemStack(Material.APPLE);
            }
        } else {
            // 2. Standardowy drop dla reszty bloków (np. diamenty, węgiel, kamień)
            // Pobieramy to, co naturalnie wypadłoby z bloku
            for (ItemStack drop : b.getDrops(p.getInventory().getItemInMainHand())) {
                addItemToPlayer(p, drop, b.getLocation());
            }
            event.setDropItems(false); // Blokujemy naturalny drop
            return;
        }

        // 3. Dodawanie specjalnego przedmiotu do ekwipunku
        if (itemToAdd != null) {
            addItemToPlayer(p, itemToAdd, b.getLocation());
        }
        
        event.setDropItems(false); // Blokujemy naturalny drop
    }

    // Pomocnicza metoda: dodaje do EQ, a jeśli pełne - wyrzuca na ziemię
    private void addItemToPlayer(Player p, ItemStack item, Location loc) {
        Map<Integer, ItemStack> leftOver = p.getInventory().addItem(item);
        
        // Jeśli mapa nie jest pusta, oznacza to, że ekwipunek jest pełny
        if (!leftOver.isEmpty()) {
            for (ItemStack is : leftOver.values()) {
                loc.getWorld().dropItemNaturally(loc, is);
            }
        }
    }

    // --- RESZTA KODU (BossBar, Generator, itp.) ---

    private void maintainLevelThirty() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getWorld().getName().equalsIgnoreCase(WORLD_NAME) && p.getGameMode() == GameMode.SURVIVAL) {
                if (p.getLevel() != 30) {
                    p.setLevel(30);
                    p.setExp(0);
                }
            }
        }
    }

    private void updateBorderBossBar() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!p.getWorld().getName().equalsIgnoreCase(WORLD_NAME) || p.getGameMode() == GameMode.SPECTATOR) {
                removeBossBar(p);
                continue;
            }

            WorldBorder border = p.getWorld().getWorldBorder();
            double size = border.getSize() / 2;
            Location loc = p.getLocation();
            Location center = border.getCenter();

            double minDist = Math.min(Math.min((center.getX() + size) - loc.getX(), loc.getX() - (center.getX() - size)), 
                                     Math.min((center.getZ() + size) - loc.getZ(), loc.getZ() - (center.getZ() - size)));

            BossBar bar = playerBossBars.computeIfAbsent(p.getUniqueId(), k -> 
                Bukkit.createBossBar(ChatColor.WHITE + "Dystans do borderu", BarColor.GREEN, BarStyle.SOLID));
            
            bar.addPlayer(p);
            bar.setVisible(true);

            double progress = Math.max(0, Math.min(1, minDist / 50.0));
            bar.setProgress(progress);

            if (minDist <= 10) {
                bar.setColor(BarColor.RED);
                bar.setTitle(ChatColor.DARK_RED + "⚠ BARDZO BLISKO BORDERU: " + (int)minDist + "m");
            } else if (minDist <= 25) {
                bar.setColor(BarColor.YELLOW);
                bar.setTitle(ChatColor.YELLOW + "Zbliżasz się do krawędzi: " + (int)minDist + "m");
            } else {
                bar.setColor(BarColor.GREEN);
                bar.setTitle(ChatColor.GREEN + "Dystans do borderu: " + (int)minDist + "m");
            }
        }
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (command.getName().equalsIgnoreCase("cwstart") && sender.isOp()) {
            World gameWorld = Bukkit.getWorld(WORLD_NAME);
            if (gameWorld == null) {
                sender.sendMessage(ChatColor.RED + "BŁĄD: Świat '" + WORLD_NAME + "' nie istnieje!");
                return true;
            }
            generateHardcoreArena(gameWorld);
            startMatchWithRooms(gameWorld);
            return true;
        }
        return false;
    }

    private void generateHardcoreArena(World world) {
        int radius = 50; 
        int ceilingY = 20; 
        int floorY = -30;

        broadcastToArena(ChatColor.DARK_PURPLE + "⛏ Generowanie areny... Pamiętaj: przedmioty trafiają od razu do EQ!");

        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                world.getBlockAt(x, ceilingY, z).setType(Material.BEDROCK);
                for (int y = floorY; y < ceilingY; y++) {
                    Block block = world.getBlockAt(x, y, z);
                    double chance = random.nextDouble();

                    if (chance < 0.004) block.setType(Material.ANCIENT_DEBRIS);
                    else if (chance < 0.034) block.setType(Material.DIAMOND_ORE);
                    else if (chance < 0.114) block.setType(Material.GOLD_ORE);
                    else if (chance < 0.25) block.setType(Material.IRON_ORE);
                    else if (chance < 0.28) block.setType(Material.OBSIDIAN);
                    else if (chance < 0.31) block.setType(Material.BOOKSHELF);
                    else if (chance < 0.38) block.setType(Material.OAK_LOG);
                    else if (chance < 0.43) block.setType(Material.OAK_LEAVES);
                    else if (chance < 0.46) block.setType(Material.GLOWSTONE);
                    else if (chance < 0.55) block.setType(Material.COAL_ORE);
                    else block.setType(Material.STONE);
                }
            }
        }
    }

    private void startMatchWithRooms(World world) {
        WorldBorder border = world.getWorldBorder();
        border.setCenter(0, 0);
        border.setSize(100); 

        for (Player p : Bukkit.getOnlinePlayers()) {
            int x = random.nextInt(70) - 35;
            int z = random.nextInt(70) - 35;
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
            p.getInventory().addItem(new ItemStack(Material.STONE_PICKAXE));
            p.getInventory().addItem(new ItemStack(Material.STONE_AXE));
            p.getInventory().addItem(new ItemStack(Material.BREAD, 32));
            p.getInventory().addItem(new ItemStack(Material.CRAFTING_TABLE));
        }

        Bukkit.getScheduler().runTaskLater(this, () -> {
            border.setSize(6, 900);
            broadcastToArena(ChatColor.RED + "⚠ Border ruszył!");
        }, 1200L);
    }

    private void broadcastToArena(String message) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getWorld().getName().equalsIgnoreCase(WORLD_NAME)) {
                p.sendMessage(message);
            }
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player p = event.getEntity();
        if (p.getWorld().getName().equalsIgnoreCase(WORLD_NAME)) {
            p.setGameMode(GameMode.SPECTATOR);
            removeBossBar(p);
            event.setDeathMessage(null);
            broadcastToArena(ChatColor.RED + "☠ Gracz " + p.getName() + " został wyeliminowany!");
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player p = event.getPlayer();
        if (p.getWorld().getName().equalsIgnoreCase(WORLD_NAME) && p.getGameMode() == GameMode.SURVIVAL) {
            p.setGameMode(GameMode.SPECTATOR);
            removeBossBar(p);
            broadcastToArena(ChatColor.RED + "❌ Gracz " + p.getName() + " wyszedł z gry i został wyeliminowany!");
        }
    }

    @EventHandler
    public void onLeavesDecay(LeavesDecayEvent event) {
        event.setCancelled(true);
    }

    private void removeBossBar(Player p) {
        BossBar bar = playerBossBars.remove(p.getUniqueId());
        if (bar != null) {
            bar.removeAll();
        }
    }
}
