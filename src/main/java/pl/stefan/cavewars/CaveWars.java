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

import java.util.Random;

public class CaveWars extends JavaPlugin implements Listener {

    private final Random random = new Random();

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        Bukkit.getScheduler().runTaskTimer(this, this::checkBorderDistance, 20L, 20L);
        getLogger().info("CaveWars 1.21.4 (Pokoje 3x3x3 + Start Gear) gotowy!");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (command.getName().equalsIgnoreCase("cwstart") && sender.isOp()) {
            generateFullResourceArena();
            startMatchWithRooms();
            return true;
        }
        return false;
    }

    private void generateFullResourceArena() {
        World world = Bukkit.getWorlds().get(0);
        int radius = 50; 
        int ceilingY = 40;
        int floorY = -60;

        Bukkit.broadcastMessage(ChatColor.GOLD + "⛏ Generowanie mapy zasobów...");

        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                world.getBlockAt(x, ceilingY, z).setType(Material.BEDROCK);
                for (int y = floorY; y < ceilingY; y++) {
                    Block block = world.getBlockAt(x, y, z);
                    double chance = random.nextDouble();

                    if (chance < 0.04) block.setType(Material.ANCIENT_DEBRIS);
                    else if (chance < 0.10) block.setType(Material.DIAMOND_ORE);
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
        Bukkit.broadcastMessage(ChatColor.GREEN + "✅ Arena gotowa!");
    }

    private void startMatchWithRooms() {
        World world = Bukkit.getWorlds().get(0);
        WorldBorder border = world.getWorldBorder();
        border.setCenter(0, 0);
        border.setSize(100); 

        for (Player p : Bukkit.getOnlinePlayers()) {
            // Losowanie centrum pokoju 3x3x3
            int x = random.nextInt(70) - 35;
            int z = random.nextInt(70) - 35;
            int y = 20; // Środek areny w pionie

            // Tworzenie pokoju 3x3x3 (czyszczenie bloków)
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        world.getBlockAt(x + dx, y + dy, z + dz).setType(Material.AIR);
                    }
                }
            }

            // Teleportacja gracza na środek pokoju
            Location spawnLoc = new Location(world, x + 0.5, y - 0.5, z + 0.5);
            p.teleport(spawnLoc);
            
            p.setGameMode(GameMode.SURVIVAL);
            p.getInventory().clear();
            
            // Startowy ekwipunek
            p.getInventory().addItem(new ItemStack(Material.STONE_PICKAXE));
            p.getInventory().addItem(new ItemStack(Material.STONE_AXE));
            p.getInventory().addItem(new ItemStack(Material.BREAD, 32));
            p.getInventory().addItem(new ItemStack(Material.CRAFTING_TABLE));
            p.getInventory().addItem(new ItemStack(Material.TORCH, 16));
            
            p.sendMessage(ChatColor.AQUA + "Jesteś w swoim pokoju startowym! Powodzenia!");
            p.playSound(p.getLocation(), Sound.EVENT_RAID_HORN, 1.0f, 1.0f);
        }

        // Opóźnienie borderu o 1 minutę
        Bukkit.getScheduler().runTaskLater(this, () -> {
            border.setSize(6, 480);
            Bukkit.broadcastMessage(ChatColor.RED + "⚠ Border ruszył!");
        }, 1200L); 
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Block b = event.getBlock();
        // Szansa na jabłko z liści (33%)
        if (b.getType() == Material.OAK_LEAVES && random.nextDouble() < 0.33) {
            b.getWorld().dropItemNaturally(b.getLocation(), new ItemStack(Material.APPLE));
        }
        // Auto-smelt
        if (b.getType() == Material.IRON_ORE || b.getType() == Material.DEEPSLATE_IRON_ORE) {
            event.setDropItems(false);
            b.getWorld().dropItemNaturally(b.getLocation(), new ItemStack(Material.IRON_INGOT));
        }
        if (b.getType() == Material.GOLD_ORE || b.getType() == Material.DEEPSLATE_GOLD_ORE) {
            event.setDropItems(false);
            b.getWorld().dropItemNaturally(b.getLocation(), new ItemStack(Material.GOLD_INGOT));
        }
    }

    private void checkBorderDistance() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getGameMode() != GameMode.SURVIVAL) continue;
            WorldBorder border = p.getWorld().getWorldBorder();
            double size = border.getSize() / 2;
            Location center = border.getCenter();
            Location loc = p.getLocation();
            double minDist = Math.min(Math.min((center.getX() + size) - loc.getX(), loc.getX() - (center.getX() - size)), 
                                     Math.min((center.getZ() + size) - loc.getZ(), loc.getZ() - (center.getZ() - size)));

            if (minDist <= 15.0 && minDist > 0) {
                p.sendMessage(ChatColor.RED + "⚠ Border jest blisko! (" + (int)minDist + " bloków)");
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f, 2.0f);
            }
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        event.getEntity().setGameMode(GameMode.SPECTATOR);
    }
}
