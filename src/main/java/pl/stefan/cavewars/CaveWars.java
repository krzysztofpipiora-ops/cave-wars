package pl.stefan.cavewars;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Random;

public class CaveWars extends JavaPlugin implements Listener {

    private final Random random = new Random();

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        getLogger().info("Plugin CaveWars 1.21.4 z generatorem areny gotowy!");
    }

    @Override
    public boolean onCommand(org.bukkit.command.CommandSender sender, org.bukkit.command.Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("cwstart") && sender.isOp()) {
            generateArena(); // Najpierw generujemy/przygotowujemy teren
            startMatch();    // Potem odpalamy border i graczy
            return true;
        }
        return false;
    }

    /**
     * Mechanizm generowania areny:
     * Tworzy sufit z Bedrocka na poziomie Y=40 i podłogę na Y=-64,
     * aby gracze nie mogli uciec na powierzchnię.
     */
    public void generateArena() {
        World world = Bukkit.getWorlds().get(0);
        int radius = 200; // Wielkość areny (od -200 do 200)

        Bukkit.broadcastMessage(ChatColor.YELLOW + "⌛ Przygotowywanie jaskiniowej areny... Może to chwilę potrwać.");

        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                // Sufit - blokuje ucieczkę na górę
                world.getBlockAt(x, 40, z).setType(Material.BEDROCK);
                
                // Czyścimy wszystko powyżej sufitu, żeby nie było widać gór
                for (int y = 41; y <= 100; y++) {
                    if (world.getBlockAt(x, y, z).getType() != Material.AIR) {
                        world.getBlockAt(x, y, z).setType(Material.AIR);
                    }
                }
                
                // Dodajemy trochę "debris" w losowych miejscach w ścianach na starcie
                if (random.nextDouble() < 0.001) { 
                    world.getBlockAt(x, random.nextInt(40) - 20, z).setType(Material.ANCIENT_DEBRIS);
                }
            }
        }
        Bukkit.broadcastMessage(ChatColor.GREEN + "✅ Arena gotowa!");
    }

    public void startMatch() {
        World world = Bukkit.getWorlds().get(0);
        WorldBorder border = world.getWorldBorder();
        
        border.setCenter(0, 0);
        border.setSize(400); 
        border.setSize(10, 600); // 10 minut do końca

        for (Player p : Bukkit.getOnlinePlayers()) {
            // Teleportacja graczy w losowe miejsca na arenie pod ziemią
            int x = random.nextInt(300) - 150;
            int z = random.nextInt(300) - 150;
            Location spawnLoc = new Location(world, x, 10, z);
            
            // Szukamy bezpiecznego miejsca (powietrza)
            while (spawnLoc.getBlock().getType() != Material.AIR) {
                spawnLoc.add(0, 1, 0);
                if(spawnLoc.getY() > 35) break; 
            }

            p.teleport(spawnLoc);
            p.setGameMode(GameMode.SURVIVAL);
            p.getInventory().clear();
            
            // Zestaw startowy
            p.getInventory().addItem(new ItemStack(Material.STONE_PICKAXE));
            p.getInventory().addItem(new ItemStack(Material.BREAD, 16));
            
            p.sendMessage(ChatColor.DARK_RED + "☠ CAVE WARS: Jesteś uwięziony w jaskiniach! Kop Debris i przetrwaj!");
        }
    }

    @EventHandler
    public void onMining(BlockBreakEvent event) {
        Block b = event.getBlock();
        // Auto-smelt
        if (b.getType() == Material.IRON_ORE || b.getType() == Material.DEEPSLATE_IRON_ORE) {
            event.setDropItems(false);
            b.getWorld().dropItemNaturally(b.getLocation(), new ItemStack(Material.IRON_INGOT));
        }
        // Drop debris
        if ((b.getType() == Material.STONE || b.getType() == Material.DEEPSLATE) && b.getY() < 0) {
            if (random.nextDouble() < 0.01) {
                b.getWorld().dropItemNaturally(b.getLocation(), new ItemStack(Material.ANCIENT_DEBRIS));
            }
        }
    }
}
