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
        getLogger().info("Plugin CaveWars 1.21.4 (Full Ore Edition) aktywowany!");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (command.getName().equalsIgnoreCase("cwstart")) {
            if (!sender.isOp()) {
                sender.sendMessage(ChatColor.RED + "Nie masz uprawnień!");
                return true;
            }
            
            // 1. Generowanie areny pełnej rud
            generateFullOreArena();
            
            // 2. Start meczu (Teleportacja i Border)
            startMatch();
            
            return true;
        }
        return false;
    }

    private void generateFullOreArena() {
        World world = Bukkit.getWorlds().get(0);
        int radius = 100; 
        int ceilingY = 40;
        int floorY = -60;

        Bukkit.broadcastMessage(ChatColor.GOLD + "⛏ TRWA GENEROWANIE MAPY RUD... SERWER MOŻE SIĘ ZAMROZIĆ NA CHWILĘ!");

        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                
                // Tworzenie niezniszczalnego sufitu
                world.getBlockAt(x, ceilingY, z).setType(Material.BEDROCK);

                // Wypełnianie przestrzeni pod sufitem
                for (int y = floorY; y < ceilingY; y++) {
                    Block block = world.getBlockAt(x, y, z);
                    
                    if (block.getType() == Material.AIR) continue;

                    double chance = random.nextDouble();

                    // Generator rud (Full Ore World)
                    if (chance < 0.03) { 
                        block.setType(Material.ANCIENT_DEBRIS);
                    } else if (chance < 0.10) { 
                        block.setType(Material.DIAMOND_ORE);
                    } else if (chance < 0.25) { 
                        block.setType(Material.GOLD_ORE);
                    } else if (chance < 0.55) { 
                        block.setType(Material.IRON_ORE);
                    } else if (chance < 0.80) { 
                        block.setType(Material.COAL_ORE);
                    } else {
                        block.setType(Material.LAPIS_ORE);
                    }
                }
            }
        }
        Bukkit.broadcastMessage(ChatColor.GREEN + "✅ Arena wygenerowana!");
    }

    private void startMatch() {
        World world = Bukkit.getWorlds().get(0);
        WorldBorder border = world.getWorldBorder();
        
        border.setCenter(0, 0);
        border.setSize(200); 
        border.setSize(10, 600); // 10 minut do końca

        for (Player p : Bukkit.getOnlinePlayers()) {
            int x = random.nextInt(160) - 80;
            int z = random.nextInt(160) - 80;
            Location loc = new Location(world, x, 30, z);
            
            p.teleport(loc);
            p.setGameMode(GameMode.SURVIVAL);
            p.getInventory().clear();
            
            p.getInventory().addItem(new ItemStack(Material.IRON_PICKAXE));
            p.getInventory().addItem(new ItemStack(Material.BREAD, 32));
            p.getInventory().addItem(new ItemStack(Material.CRAFTING_TABLE));
            
            p.sendMessage(ChatColor.DARK_RED + "" + ChatColor.BOLD + "CAVE WARS WYSTARTOWAŁO!");
            
            // POPRAWIONY DŹWIĘK (Naprawia błąd kompilacji z obrazka)
            p.playSound(p.getLocation(), Sound.EVENT_RAID_HORN, 1.0f, 1.0f);
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Block b = event.getBlock();
        Player p = event.getPlayer();

        // Auto-smelt dla Żelaza
        if (b.getType() == Material.IRON_ORE || b.getType() == Material.DEEPSLATE_IRON_ORE) {
            event.setDropItems(false);
            b.getWorld().dropItemNaturally(b.getLocation(), new ItemStack(Material.IRON_INGOT));
            p.giveExp(1);
        }
        
        // Auto-smelt dla Złota
        if (b.getType() == Material.GOLD_ORE || b.getType() == Material.DEEPSLATE_GOLD_ORE) {
            event.setDropItems(false);
            b.getWorld().dropItemNaturally(b.getLocation(), new ItemStack(Material.GOLD_INGOT));
            p.giveExp(2);
        }

        // Efekt dla Debris
        if (b.getType() == Material.ANCIENT_DEBRIS) {
            p.sendMessage(ChatColor.LIGHT_PURPLE + "⛏ Znalazłeś cenny Debris!");
            p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 1.0f);
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        player.setGameMode(GameMode.SPECTATOR);
        Bukkit.broadcastMessage(ChatColor.RED + "☠ Gracz " + player.getName() + " został wyeliminowany!");
    }
}
