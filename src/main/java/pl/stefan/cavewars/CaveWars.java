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

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        // Aktualizacja BossBaru co 10 ticków (0.5s) dla płynności
        Bukkit.getScheduler().runTaskTimer(this, this::updateBorderBossBar, 10L, 10L);
        getLogger().info("CaveWars 1.21.4 (BossBar & Obsidian) gotowy!");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (command.getName().equalsIgnoreCase("cwstart") && sender.isOp()) {
            generateAdvancedArena();
            startMatchWithRooms();
            return true;
        }
        return false;
    }

    private void updateBorderBossBar() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getGameMode() != GameMode.SURVIVAL) {
                if (playerBossBars.containsKey(p.getUniqueId())) {
                    playerBossBars.get(p.getUniqueId()).removeAll();
                    playerBossBars.remove(p.getUniqueId());
                }
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

            // Logika koloru i postępu paska (max 50 kratek do monitorowania)
            double progress = Math.max(0, Math.min(1, minDist / 50.0));
            bar.setProgress(progress);

            if (minDist <= 10) {
                bar.setColor(BarColor.RED);
                bar.setTitle(ChatColor.DARK_RED + "" + ChatColor.BOLD + "BARDZO BLISKO BORDERU: " + (int)minDist + "m");
            } else if (minDist <= 25) {
                bar.setColor(BarColor.YELLOW);
                bar.setTitle(ChatColor.YELLOW + "Zbliżasz się do borderu: " + (int)minDist + "m");
            } else {
                bar.setColor(BarColor.GREEN);
                bar.setTitle(ChatColor.GREEN + "Jesteś bezpieczny: " + (int)minDist + "m");
            }
        }
    }

    private void generateAdvancedArena() {
        World world = Bukkit.getWorlds().get(0);
        int radius = 50; 
        int ceilingY = 20; 
        int floorY = -30;

        Bukkit.broadcastMessage(ChatColor.DARK_AQUA + "⛏ Generowanie areny z Obsydianem i Biblioteczkami...");

        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                world.getBlockAt(x, ceilingY, z).setType(Material.BEDROCK);
                for (int y = floorY; y < ceilingY; y++) {
                    Block block = world.getBlockAt(x, y, z);
                    double chance = random.nextDouble();

                    if (chance < 0.015) block.setType(Material.ANCIENT_DEBRIS);
                    else if (chance < 0.08) block.setType(Material.DIAMOND_ORE);
                    else if (chance < 0.18) block.setType(Material.GOLD_ORE);
                    else if (chance < 0.30) block.setType(Material.IRON_ORE);
                    else if (chance < 0.35) block.setType(Material.OBSIDIAN);    // OBSYDIAN
                    else if (chance < 0.40) block.setType(Material.BOOKSHELF);   // BIBLIOTECZKI
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

    private void startMatchWithRooms() {
        World world = Bukkit.getWorlds().get(0);
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
            
            p.playSound(p.getLocation(), Sound.EVENT_RAID_HORN, 1.0f, 1.0f);
        }

        Bukkit.getScheduler().runTaskLater(this, () -> {
            border.setSize(6, 900); 
            Bukkit.broadcastMessage(ChatColor.RED + "⚠ Border ruszył!");
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

    // BLOKADA ZNIKANIA LIŚCI
    @EventHandler
    public void onLeavesDecay(LeavesDecayEvent event) {
        event.setCancelled(true);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player p = event.getEntity();
        p.setGameMode(GameMode.SPECTATOR);
        if (playerBossBars.containsKey(p.getUniqueId())) {
            playerBossBars.get(p.getUniqueId()).removeAll();
            playerBossBars.remove(p.getUniqueId());
        }
    }
}
