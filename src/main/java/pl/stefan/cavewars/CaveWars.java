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
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Random;
import java.util.UUID;

public class CaveWars extends JavaPlugin implements Listener {

    private final Random random = new Random();
    private final HashMap<UUID, BossBar> playerBossBars = new HashMap<>();
    private final String WORLD_NAME = "cavewars"; // Nazwa świata Multiverse

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        // Odświeżanie BossBaru co 10 ticków (0.5 sekundy)
        Bukkit.getScheduler().runTaskTimer(this, this::updateBorderBossBar, 10L, 10L);
        getLogger().info("CaveWars 1.21.4 (Multiverse Ready) aktywowany!");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (command.getName().equalsIgnoreCase("cwstart") && sender.isOp()) {
            World gameWorld = Bukkit.getWorld(WORLD_NAME);
            if (gameWorld == null) {
                sender.sendMessage(ChatColor.RED + "BŁĄD: Świat '" + WORLD_NAME + "' nie istnieje! Użyj: /mv create " + WORLD_NAME + " normal -t flat");
                return true;
            }
            
            generateAdvancedArena(gameWorld);
            startMatchWithRooms(gameWorld);
            return true;
        }
        return false;
    }

    private void updateBorderBossBar() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            // Pasek pokazujemy tylko jeśli gracz jest na świecie gry i żyje
            if (!p.getWorld().getName().equalsIgnoreCase(WORLD_NAME) || p.getGameMode() == GameMode.SPECTATOR) {
                removeBossBar(p);
                continue;
            }

            WorldBorder border = p.getWorld().getWorldBorder();
            double size = border.getSize() / 2;
            Location loc = p.getLocation();
            Location center = border.getCenter();

            // Obliczanie dystansu do najbliższej krawędzi borderu
            double distToPosX = (center.getX() + size) - loc.getX();
            double distToNegX = loc.getX() - (center.getX() - size);
            double distToPosZ = (center.getZ() + size) - loc.getZ();
            double distToNegZ = loc.getZ() - (center.getZ() - size);
            double minDist = Math.min(Math.min(distToPosX, distToNegX), Math.min(distToPosZ, distToNegZ));

            BossBar bar = playerBossBars.computeIfAbsent(p.getUniqueId(), k -> 
                Bukkit.createBossBar(ChatColor.WHITE + "Dystans do borderu", BarColor.GREEN, BarStyle.SOLID));
            
            bar.addPlayer(p);
            bar.setVisible(true);

            // Postęp paska (0.0 - 1.0) bazujący na dystansie 50 kratek
            double progress = Math.max(0, Math.min(1, minDist / 50.0));
            bar.setProgress(progress);

            if (minDist <= 10) {
                bar.setColor(BarColor.RED);
                bar.setTitle(ChatColor.DARK_RED + "" + ChatColor.BOLD + "BARDZO BLISKO BORDERU: " + (int)minDist + "m");
            } else if (minDist <= 25) {
                bar.setColor(BarColor.YELLOW);
                bar.setTitle(ChatColor.YELLOW + "Zbliżasz się do krawędzi: " + (int)minDist + "m");
            } else {
                bar.setColor(BarColor.GREEN);
                bar.setTitle(ChatColor.GREEN + "Dystans do borderu: " + (int)minDist + "m");
            }
        }
    }

    private void generateAdvancedArena(World world) {
        int radius = 50; 
        int ceilingY = 20; 
        int floorY = -30;

        Bukkit.broadcastMessage(ChatColor.DARK_AQUA + "⛏ Generowanie areny na świecie " + WORLD_NAME + "...");

        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                world.getBlockAt(x, ceilingY, z).setType(Material.BEDROCK);
                for (int y = floorY; y < ceilingY; y++) {
                    Block block = world.getBlockAt(x, y, z);
                    double chance = random.nextDouble();

                    // Pula bloków
                    if (chance < 0.015) block.setType(Material.ANCIENT_DEBRIS);
                    else if (chance < 0.08) block.setType(Material.DIAMOND_ORE);
                    else if (chance < 0.18) block.setType(Material.GOLD_ORE);
                    else if (chance < 0.30) block.setType(Material.IRON_ORE);
                    else if (chance < 0.35) block.setType(Material.OBSIDIAN);
                    else if (chance < 0.40) block.setType(Material.BOOKSHELF);
                    else if (chance < 0.50) block.setType(Material.OAK_LOG);
                    else if (chance < 0.60) block.setType(Material.OAK_LEAVES);
                    else if (chance < 0.65) block.setType(Material.GLASS);
                    else if (chance < 0.70) block.setType(Material.GLOWSTONE);
                    else if (chance < 0.85) block.setType(Material.COAL_ORE);
                    else block.setType(Material.LAPIS_ORE);
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

            // Tworzenie pokoju startowego 3x3x3
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
            
            p.sendMessage(ChatColor.AQUA + "Witaj w CaveWars! Masz minutę zanim border ruszy.");
            p.playSound(p.getLocation(), Sound.EVENT_RAID_HORN, 1.0f, 1.0f);
        }

        // Powolne kurczenie borderu (15 minut)
        Bukkit.getScheduler().runTaskLater(this, () -> {
            border.setSize(6, 900); 
            Bukkit.broadcastMessage(ChatColor.RED + "⚠ Border zaczął się kurczyć!");
        }, 1200L); 
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Block b = event.getBlock();
        if (b.getType() == Material.OAK_LEAVES && random.nextDouble() < 0.33) {
            b.getWorld().dropItemNaturally(b.getLocation(), new ItemStack(Material.APPLE));
        }
        if (b.getType() == Material.IRON_ORE || b.getType() == Material.DEEPSLATE_IRON_ORE) {
            event.setDropItems(false);
            b.getWorld().dropItemNaturally(b.getLocation(), new ItemStack(Material.IRON_INGOT));
        }
        if (b.getType() == Material.GOLD_ORE || b.getType() == Material.DEEPSLATE_GOLD_ORE) {
            event.setDropItems(false);
            b.getWorld().dropItemNaturally(b.getLocation(), new ItemStack(Material.GOLD_INGOT));
        }
    }

    @EventHandler
    public void onLeavesDecay(LeavesDecayEvent event) {
        // Blokada samoczynnego znikania liści
        event.setCancelled(true);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player p = event.getEntity();
        p.setGameMode(GameMode.SPECTATOR);
        removeBossBar(p);
        Bukkit.broadcastMessage(ChatColor.RED + "☠ Gracz " + p.getName() + " odpadł!");
    }

    private void removeBossBar(Player p) {
        if (playerBossBars.containsKey(p.getUniqueId())) {
            playerBossBars.get(p.getUniqueId()).removeAll();
            playerBossBars.remove(p.getUniqueId());
        }
    }
}
