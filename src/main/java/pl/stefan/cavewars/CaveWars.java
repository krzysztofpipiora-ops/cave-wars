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
        getLogger().info("Plugin CaveWars 1.21.4 (Total Ore - No Caves) aktywowany!");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (command.getName().equalsIgnoreCase("cwstart")) {
            if (!sender.isOp()) {
                sender.sendMessage(ChatColor.RED + "Brak uprawnien!");
                return true;
            }
            
            generateTotalOreArena();
            startMatch();
            
            return true;
        }
        return false;
    }

    private void generateTotalOreArena() {
        World world = Bukkit.getWorlds().get(0);
        int radius = 100; 
        int ceilingY = 40;
        int floorY = -60;

        Bukkit.broadcastMessage(ChatColor.DARK_PURPLE + "⛏ TRWA TOTALNE WYPELNIANIE MAPY RUDAMI... Moze to potrwac dluzej!");

        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                
                // Sufit z bedrocka
                world.getBlockAt(x, ceilingY, z).setType(Material.BEDROCK);

                // Wypełnianie KAŻDEGO bloku (brak jaskiń, brak wody)
                for (int y = floorY; y < ceilingY; y++) {
                    Block block = world.getBlockAt(x, y, z);
                    
                    double chance = random.nextDouble();

                    // 100% bloków pod sufitem staje się rudami:
                    if (chance < 0.05) { 
                        block.setType(Material.ANCIENT_DEBRIS); // 5% na Debris
                    } else if (chance < 0.15) { 
                        block.setType(Material.DIAMOND_ORE);    // 10% na Diamenty
                    } else if (chance < 0.35) { 
                        block.setType(Material.GOLD_ORE);       // 20% na Złoto
                    } else if (chance < 0.65) { 
                        block.setType(Material.IRON_ORE);       // 30% na Żelazo
                    } else if (chance < 0.85) { 
                        block.setType(Material.COAL_ORE);       // 20% na Węgiel
                    } else {
                        block.setType(Material.LAPIS_ORE);      // 15% na Lapis
                    }
                }
            }
        }
        Bukkit.broadcastMessage(ChatColor.GREEN + "✅ Arena gotowa! Swiat jest litym blokiem surowcow.");
    }

    private void startMatch() {
        World world = Bukkit.getWorlds().get(0);
        WorldBorder border = world.getWorldBorder();
        
        border.setCenter(0, 0);
        border.setSize(200); 
        border.setSize(10, 600); 

        for (Player p : Bukkit.getOnlinePlayers()) {
            int x = random.nextInt(160) - 80;
            int z = random.nextInt(160) - 80;
            
            // Teleportujemy gracza na poziom Y=35 (zaraz pod sufitem), 
            // bo skoro nie ma jaskiń, muszą zacząć kopać od góry.
            Location loc = new Location(world, x, 35, z);
            
            // Czyscimy maly obszar 1x2x1 żeby gracz nie zginal w bloku od razu
            loc.getBlock().setType(Material.AIR);
            loc.clone().add(0, 1, 0).getBlock().setType(Material.AIR);
            
            p.teleport(loc);
            p.setGameMode(GameMode.SURVIVAL);
            p.getInventory().clear();
            
            p.getInventory().addItem(new ItemStack(Material.IRON_PICKAXE));
            p.getInventory().addItem(new ItemStack(Material.BREAD, 64));
            p.getInventory().addItem(new ItemStack(Material.CRAFTING_TABLE));
            p.getInventory().addItem(new ItemStack(Material.TORCH, 64));
            
            p.sendMessage(ChatColor.RED + "Kop w dol! Caly swiat to rudy!");
            p.playSound(p.getLocation(), Sound.EVENT_RAID_HORN, 1.0f, 1.0f);
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Block b = event.getBlock();
        Player p = event.getPlayer();

        if (b.getType() == Material.IRON_ORE || b.getType() == Material.DEEPSLATE_IRON_ORE) {
            event.setDropItems(false);
            b.getWorld().dropItemNaturally(b.getLocation(), new ItemStack(Material.IRON_INGOT));
        }
        
        if (b.getType() == Material.GOLD_ORE || b.getType() == Material.DEEPSLATE_GOLD_ORE) {
            event.setDropItems(false);
            b.getWorld().dropItemNaturally(b.getLocation(), new ItemStack(Material.GOLD_INGOT));
        }

        if (b.getType() == Material.ANCIENT_DEBRIS) {
            p.sendMessage(ChatColor.LIGHT_PURPLE + "⛏ Debris!");
            p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 1.0f);
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        event.getEntity().setGameMode(GameMode.SPECTATOR);
        Bukkit.broadcastMessage(ChatColor.RED + "☠ " + event.getEntity().getName() + " odpadl!");
    }
}
