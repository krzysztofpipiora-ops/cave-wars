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
        getLogger().info("CaveWars 8-osobowe (Mala Arena) aktywowany!");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (command.getName().equalsIgnoreCase("cwstart") && sender.isOp()) {
            
            // 1. Mala arena dla 8 osob
            generateSmallArena();
            
            // 2. Start (Teleportacja i Szybki Border)
            startMatch();
            
            return true;
        }
        return false;
    }

    private void generateSmallArena() {
        World world = Bukkit.getWorlds().get(0);
        int radius = 50; // Mniejszy promien idealny dla 8 graczy
        int ceilingY = 40;
        int floorY = -60;

        Bukkit.broadcastMessage(ChatColor.YELLOW + "⛏ Generowanie malej areny (100x100)...");

        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                
                // Sufit z bedrocka
                world.getBlockAt(x, ceilingY, z).setType(Material.BEDROCK);

                for (int y = floorY; y < ceilingY; y++) {
                    Block block = world.getBlockAt(x, y, z);
                    double chance = random.nextDouble();

                    // Wypelnienie surowcami (bez jaskin)
                    if (chance < 0.05) block.setType(Material.ANCIENT_DEBRIS);
                    else if (chance < 0.15) block.setType(Material.DIAMOND_ORE);
                    else if (chance < 0.35) block.setType(Material.GOLD_ORE);
                    else if (chance < 0.60) block.setType(Material.IRON_ORE);
                    else if (chance < 0.80) block.setType(Material.COAL_ORE);
                    else block.setType(Material.LAPIS_ORE);
                }
            }
        }
        Bukkit.broadcastMessage(ChatColor.GREEN + "✅ Mala arena gotowa!");
    }

    private void startMatch() {
        World world = Bukkit.getWorlds().get(0);
        WorldBorder border = world.getWorldBorder();
        
        border.setCenter(0, 0);
        border.setSize(100); // Startowa wielkosc zgodna z arena
        border.setSize(6, 480); // Kurczenie do 6 kratek w 8 minut (szybsza gra)

        for (Player p : Bukkit.getOnlinePlayers()) {
            // Losowanie pozycji wewnatrz malej areny
            int x = random.nextInt(80) - 40;
            int z = random.nextInt(80) - 40;
            Location loc = new Location(world, x, 35, z);
            
            // Bezpieczny start
            loc.getBlock().setType(Material.AIR);
            loc.clone().add(0, 1, 0).getBlock().setType(Material.AIR);
            
            p.teleport(loc);
            p.setGameMode(GameMode.SURVIVAL);
            p.getInventory().clear();
            
            // Wybalansowany zestaw startowy
            p.getInventory().addItem(new ItemStack(Material.IRON_PICKAXE));
            p.getInventory().addItem(new ItemStack(Material.BREAD, 32));
            p.getInventory().addItem(new ItemStack(Material.CRAFTING_TABLE));
            p.getInventory().addItem(new ItemStack(Material.TORCH, 32));
            
            p.sendMessage(ChatColor.RED + "Mala arena! Walka zaraz sie zacznie!");
            p.playSound(p.getLocation(), Sound.EVENT_RAID_HORN, 1.0f, 1.0f);
        }
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
