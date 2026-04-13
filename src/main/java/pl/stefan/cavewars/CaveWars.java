package pl.stefan.cavewars;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Random;
import java.util.UUID;

public class CaveWars extends JavaPlugin implements Listener {

    private final Random random = new Random();
    private final HashMap<UUID, Long> lastWarningTime = new HashMap<>();

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        Bukkit.getScheduler().runTaskTimer(this, this::checkBorderDistance, 20L, 20L);
        getLogger().info("CaveWars 1.21.4 (Low Arena & Rare Debris) aktywowany!");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (command.getName().equalsIgnoreCase("cwstart") && sender.isOp()) {
            generateBalancedArena();
            startMatchWithRooms();
            return true;
        }
        return false;
    }

    private void generateBalancedArena() {
        World world = Bukkit.getWorlds().get(0);
        int radius = 50; 
        int ceilingY = 20; // Obniżony sufit
        int floorY = -30;   // Podniesiona podłoga (łącznie 50 kratek wysokości)

        Bukkit.broadcastMessage(ChatColor.GOLD + "⛏ Generowanie areny (Wysokość 50, Rzadszy Debris)...");

        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                world.getBlockAt(x, ceilingY, z).setType(Material.BEDROCK);
                for (int y = floorY; y < ceilingY; y++) {
                    Block block = world.getBlockAt(x, y, z);
                    double chance = random.nextDouble();

                    // NOWY ZBALANSOWANY GENERATOR
                    if (chance < 0.015) block.setType(Material.ANCIENT_DEBRIS); // RZADKI (1.5%)
                    else if (chance < 0.085) block.setType(Material.DIAMOND_ORE); // LEKKO MNIEJ (7%)
                    else if (chance < 0.20) block.setType(Material.GOLD_ORE);
                    else if (chance < 0.35) block.setType(Material.IRON_ORE);
                    else if (chance < 0.45) block.setType(Material.OAK_LOG);
                    else if (chance < 0.55) block.setType(Material.OAK_LEAVES);
                    else if (chance < 0.60) block.setType(Material.GLASS);
                    else if (chance < 0.65) block.setType(Material.GLOWSTONE);
                    else if (chance < 0.80) block.setType(Material.COAL_ORE);
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
            int y = -5; // Środek nowej, niższej areny

            // Pokój 3x3x3
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
            
            p.sendMessage(ChatColor.AQUA + "Start! Debris jest teraz rzadszy, szukaj uważnie!");
            p.playSound(p.getLocation(), Sound.EVENT_RAID_HORN, 1.0f, 1.0f);
        }

        // Spowolniony border (15 min)
        Bukkit.getScheduler().runTaskLater(this, () -> {
            border.setSize(6, 900); 
            Bukkit.broadcastMessage(ChatColor.RED + "⚠ Border ruszył!");
        }, 1200L); 
    }

    private void checkBorderDistance() {
        long currentTime = System.currentTimeMillis();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getGameMode() != GameMode.SURVIVAL) continue;

            WorldBorder border = p.getWorld().getWorldBorder();
            double size = border.getSize() / 2;
            Location center = border.getCenter();
            Location loc = p.getLocation();

            double minDist = Math.min(Math.min((center.getX() + size) - loc.getX(), loc.getX() - (center.getX() - size)), 
                                     Math.min((center.getZ() + size) - loc.getZ(), loc.getZ() - (center.getZ() - size)));

            if (minDist <= 15.0 && minDist > 0) {
                long lastNotify = lastWarningTime.getOrDefault(p.getUniqueId(), 0L);
                if (currentTime - lastNotify >= 12000) { // Max 5 razy na minutę
                    p.sendMessage(ChatColor.RED + "⚠ Border jest blisko! (" + (int)minDist + " bloków)");
                    p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f, 2.0f);
                    lastWarningTime.put(p.getUniqueId(), currentTime);
                }
            }
        }
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
    public void onDeath(PlayerDeathEvent event) {
        event.getEntity().setGameMode(GameMode.SPECTATOR);
    }
}
