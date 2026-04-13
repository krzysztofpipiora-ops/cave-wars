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
        
        // --- MONITOR BORDERU ---
        // Sprawdza co 20 ticków (1 sekunda), czy gracze są blisko borderu
        Bukkit.getScheduler().runTaskTimer(this, this::checkBorderDistance, 20L, 20L);
        
        getLogger().info("CaveWars (Mala Arena + Ostrzezenia) gotowy!");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (command.getName().equalsIgnoreCase("cwstart") && sender.isOp()) {
            generateSmallArena();
            startMatch();
            return true;
        }
        return false;
    }

    private void checkBorderDistance() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getGameMode() != GameMode.SURVIVAL) continue;

            World world = p.getWorld();
            WorldBorder border = world.getWorldBorder();
            double size = border.getSize() / 2;
            Location center = border.getCenter();
            Location loc = p.getLocation();

            // Obliczanie dystansu do krawędzi (kwadratowy border)
            double distToPosX = (center.getX() + size) - loc.getX();
            double distToNegX = loc.getX() - (center.getX() - size);
            double distToPosZ = (center.getZ() + size) - loc.getZ();
            double distToNegZ = loc.getZ() - (center.getZ() - size);

            double minDist = Math.min(Math.min(distToPosX, distToNegX), Math.min(distToPosZ, distToNegZ));

            // Jeśli gracz jest 15 kratek od borderu lub bliżej
            if (minDist <= 15.0 && minDist > 0) {
                p.sendMessage(ChatColor.RED + "⚠ " + ChatColor.BOLD + "UWAGA: Border jest tylko " + (int)minDist + " bloków od Ciebie!");
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f, 2.0f);
            }
        }
    }

    private void generateSmallArena() {
        World world = Bukkit.getWorlds().get(0);
        int radius = 50; 
        int ceilingY = 40;
        int floorY = -60;

        Bukkit.broadcastMessage(ChatColor.YELLOW + "⛏ Przygotowywanie areny...");

        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                world.getBlockAt(x, ceilingY, z).setType(Material.BEDROCK);

                for (int y = floorY; y < ceilingY; y++) {
                    Block block = world.getBlockAt(x, y, z);
                    double chance = random.nextDouble();

                    if (chance < 0.05) block.setType(Material.ANCIENT_DEBRIS);
                    else if (chance < 0.15) block.setType(Material.DIAMOND_ORE);
                    else if (chance < 0.35) block.setType(Material.GOLD_ORE);
                    else if (chance < 0.60) block.setType(Material.IRON_ORE);
                    else if (chance < 0.80) block.setType(Material.COAL_ORE);
                    else block.setType(Material.LAPIS_ORE);
                }
            }
        }
        Bukkit.broadcastMessage(ChatColor.GREEN + "✅ Arena gotowa!");
    }

    private void startMatch() {
        World world = Bukkit.getWorlds().get(0);
        WorldBorder border = world.getWorldBorder();
        
        border.setCenter(0, 0);
        border.setSize(100); 

        for (Player p : Bukkit.getOnlinePlayers()) {
            int x = random.nextInt(80) - 40;
            int z = random.nextInt(80) - 40;
            Location loc = new Location(world, x, 35, z);
            
            loc.getBlock().setType(Material.AIR);
            loc.clone().add(0, 1, 0).getBlock().setType(Material.AIR);
            
            p.teleport(loc);
            p.setGameMode(GameMode.SURVIVAL);
            p.getInventory().clear();
            
            p.getInventory().addItem(new ItemStack(Material.IRON_PICKAXE));
            p.getInventory().addItem(new ItemStack(Material.BREAD, 32));
            p.getInventory().addItem(new ItemStack(Material.CRAFTING_TABLE));
            p.getInventory().addItem(new ItemStack(Material.TORCH, 32));
            
            p.sendMessage(ChatColor.GOLD + "⭐ Masz 1 MINUTĘ na kopanie przed ruszeniem borderu!");
            p.playSound(p.getLocation(), Sound.EVENT_RAID_HORN, 1.0f, 1.0f);
        }

        Bukkit.getScheduler().runTaskLater(this, () -> {
            border.setSize(6, 480);
            Bukkit.broadcastMessage(ChatColor.RED + "⚠ Border zaczął się kurczyć!");
        }, 1200L); 
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Block b = event.getBlock();
        if (b.getType() == Material.IRON_ORE || b.getType() == Material.DEEPSLATE_IRON_ORE) {
            event.setDropItems(false);
            b.getWorld().dropItemNaturally(b.getLocation(), new ItemStack(Material.IRON_INGOT));
        }
        if (b.getType() == Material.GOLD_ORE || b.getType() == Material.DEEPSLATE_GOLD_ORE) {
            event.setDropItems(false);
            b.getWorld().dropItemNaturally(b.getLocation(), new ItemStack(Material.GOLD_INGOT));
        }
        if (b.getType() == Material.ANCIENT_DEBRIS) {
            event.getPlayer().playSound(event.getPlayer().getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 1.0f);
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        event.getEntity().setGameMode(GameMode.SPECTATOR);
    }
}
