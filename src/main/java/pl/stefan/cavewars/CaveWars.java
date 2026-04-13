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
        getLogger().info("Plugin CaveWars 1.21.4 załadowany!");
    }

    @Override
    public boolean onCommand(org.bukkit.command.CommandSender sender, org.bukkit.command.Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("cwstart") && sender.isOp()) {
            World world = Bukkit.getWorlds().get(0);
            WorldBorder border = world.getWorldBorder();
            
            border.setCenter(0, 0);
            border.setSize(400); 
            border.setSize(15, 600); // Zmniejsza do 15 kratek w 10 minut

            Bukkit.broadcastMessage(ChatColor.DARK_RED + "⚠ CAVE WARS WYSTARTOWAŁO! Border rusza!");
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.playSound(p.getLocation(), Sound.ENTITY_DRAGON_FIREBALL_EXPLODE, 1, 1);
            }
            return true;
        }
        return false;
    }

    @EventHandler
    public void onMining(BlockBreakEvent event) {
        Block b = event.getBlock();
        Player p = event.getPlayer();

        // AUTO-SMELT (Złoto i Żelazo)
        if (b.getType() == Material.IRON_ORE || b.getType() == Material.DEEPSLATE_IRON_ORE) {
            event.setDropItems(false);
            b.getWorld().dropItemNaturally(b.getLocation(), new ItemStack(Material.IRON_INGOT));
        }

        // NOWY BLOK: Debris (Szansa z kamienia poniżej Y=0)
        if ((b.getType() == Material.STONE || b.getType() == Material.DEEPSLATE) && b.getY() < 0) {
            if (random.nextDouble() < 0.01) { // 1% szansy
                b.getWorld().dropItemNaturally(b.getLocation(), new ItemStack(Material.ANCIENT_DEBRIS));
                p.sendMessage(ChatColor.GOLD + "✨ Znalazłeś rzadki Debris!");
            }
        }
    }
}
