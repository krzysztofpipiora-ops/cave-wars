package pl.stefan.cavewars;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.stream.Collectors;

public class CaveWars extends JavaPlugin implements Listener {

    private final Random random = new Random();
    private final Map<UUID, ArenaData> arenas = new HashMap<>();
    private final Map<UUID, BossBar> playerBossBars = new HashMap<>();
    private final List<UUID> registeredWorlds = new ArrayList<>();
    private final String SPAWN_WORLD_NAME = "world";

    private static class ArenaData {
        World world;
        boolean active = false;
        int countdown = -1;
        int pvpGraceTime = 0; 
        List<Location> spawnPoints = new ArrayList<>();
        Set<UUID> eliminated = new HashSet<>();
        ArenaData(World world) { this.world = world; }
    }

    @Override
    public void onEnable() {
        try {
            if (!getDataFolder().exists()) getDataFolder().mkdirs();
            saveDefaultConfig(); // PAMIĘTAJ O PLIKU config.yml W RESOURCES!
            
            Bukkit.getPluginManager().registerEvents(this, this);
            try { registerCustomRecipes(); } catch (Exception ignored) {}

            Bukkit.getScheduler().runTaskLater(this, () -> {
                loadArenas();
                getLogger().info("Zaladowano areny z configu.");
            }, 40L);

            startMainTask();
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void startMainTask() {
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (ArenaData arena : arenas.values()) {
                if (arena.active) {
                    updateActiveArena(arena);
                    if (arena.pvpGraceTime > 0) arena.pvpGraceTime--;
                    checkWinner(arena);
                } else if (registeredWorlds.contains(arena.world.getUID())) {
                    handleLobbyCountdown(arena);
                }
            }
        }, 60L, 20L);
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        ArenaData arena = arenas.get(event.getBlock().getWorld().getUID());
        if (arena == null || !arena.active) return;

        Block b = event.getBlock();
        Player p = event.getPlayer();
        Material type = b.getType();

        // 20% szans na jabłko z liści
        if (type == Material.OAK_LEAVES) {
            if (random.nextDouble() < 0.20) p.getInventory().addItem(new ItemStack(Material.APPLE));
            b.setType(Material.AIR);
            event.setCancelled(true);
            return;
        }

        // Ukryta skrzynia 0.5%
        if (random.nextDouble() < 0.005) {
            b.setType(Material.CHEST);
            fillChest((Chest) b.getState());
            p.sendMessage(ChatColor.GOLD + "Znalazles ukryta skrzynie!");
            event.setCancelled(true);
            return;
        }

        // Auto-przepalanie rud
        ItemStack drop = null;
        if (type == Material.IRON_ORE || type == Material.DEEPSLATE_IRON_ORE) drop = new ItemStack(Material.IRON_INGOT);
        else if (type == Material.GOLD_ORE || type == Material.DEEPSLATE_GOLD_ORE) drop = new ItemStack(Material.GOLD_INGOT);

        if (drop != null) {
            p.getInventory().addItem(drop);
            b.setType(Material.AIR);
            event.setDropItems(false);
            return;
        }

        // Drop wszystkiego innego do EQ
        Collection<ItemStack> drops = b.getDrops(p.getInventory().getItemInMainHand());
        for (ItemStack item : drops) p.getInventory().addItem(item);
        b.setType(Material.AIR);
        event.setDropItems(false);
    }

    private void handleLobbyCountdown(ArenaData arena) {
        int count = arena.world.getPlayers().size();
        if (count < 2) { arena.countdown = -1; return; }
        if (arena.countdown == -1) arena.countdown = 80;
        if (count >= 8 && arena.countdown > 15) arena.countdown = 15;

        if (arena.countdown > 0) {
            if (arena.countdown % 10 == 0 || arena.countdown <= 5)
                broadcastToWorld(arena.world, ChatColor.YELLOW + "Start za: " + ChatColor.WHITE + arena.countdown + "s");
            arena.countdown--;
        } else if (arena.countdown == 0) {
            arena.countdown = -1;
            generateSolidArena(arena.world);
            startMatch(arena);
        }
    }

    private void startMatch(ArenaData arena) {
        arena.active = true;
        arena.pvpGraceTime = 180;
        arena.eliminated.clear();
        arena.spawnPoints.clear();
        arena.world.getWorldBorder().setCenter(0, 0);
        arena.world.getWorldBorder().setSize(100);

        for (Player p : arena.world.getPlayers()) {
            Location loc = findSafeSpawn(arena);
            arena.spawnPoints.add(loc);
            // Tworzenie wolnej przestrzeni na spawn
            for(int x=-1; x<=1; x++) for(int y=-1; y<=2; y++) for(int z=-1; z<=1; z++)
                loc.clone().add(x,y,z).getBlock().setType(Material.AIR);
                
            p.teleport(loc.add(0.5, 0, 0.5));
            p.setGameMode(GameMode.SURVIVAL);
            p.getInventory().clear();
            p.setLevel(30);
            p.getInventory().addItem(new ItemStack(Material.STONE_PICKAXE), new ItemStack(Material.BREAD, 16));
        }
        
        // Border rusza po godzinie (jak w oryginalnym planie)
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (arena.active) {
                arena.world.getWorldBorder().setSize(6, 900);
                broadcastToWorld(arena.world, ChatColor.RED + "Granica ruszyla!");
            }
        }, 3600L * 20L);
    }

    private Location findSafeSpawn(ArenaData arena) {
        for (int i = 0; i < 200; i++) {
            Location loc = new Location(arena.world, random.nextInt(80)-40, -5, random.nextInt(80)-40);
            boolean far = true;
            for (Location o : arena.spawnPoints) if (loc.distance(o) < 15) { far = false; break; }
            if (far) return loc;
        }
        return new Location(arena.world, 0, -5, 0);
    }

    private void checkWinner(ArenaData a) {
        List<Player> alive = a.world.getPlayers().stream()
                .filter(p -> p.getGameMode() == GameMode.SURVIVAL && !a.eliminated.contains(p.getUniqueId()))
                .collect(Collectors.toList());
        if (alive.size() <= 1) endGame(a, alive.isEmpty() ? null : alive.get(0));
    }

    private void endGame(ArenaData a, Player winner) {
        a.active = false;
        String name = (winner != null) ? winner.getName() : "Brak";
        broadcastToWorld(a.world, ChatColor.GOLD + "Zwyciezca: " + ChatColor.WHITE + name);
        
        Bukkit.getScheduler().runTaskLater(this, () -> {
            World w = Bukkit.getWorld(SPAWN_WORLD_NAME);
            for (Player p : a.world.getPlayers()) {
                p.getInventory().clear();
                p.teleport(w != null ? w.getSpawnLocation() : a.world.getSpawnLocation());
                p.setGameMode(GameMode.ADVENTURE);
                removeBossBar(p);
            }
            a.world.getWorldBorder().setSize(100);
            a.eliminated.clear();
        }, 100L);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        Player v = e.getEntity();
        ArenaData a = arenas.get(v.getWorld().getUID());
        if (a == null || !a.active) return;
        a.eliminated.add(v.getUniqueId());
        e.getDrops().clear();
        Bukkit.getScheduler().runTaskLater(this, () -> v.setGameMode(GameMode.SPECTATOR), 1L);
    }

    @Override
    public boolean onCommand(CommandSender s, Command c, String l, String[] args) {
        if (!(s instanceof Player p)) return false;
        if (c.getName().equalsIgnoreCase("cwcreate")) {
            if (!p.isOp()) return true;
            UUID id = p.getWorld().getUID();
            if (!registeredWorlds.contains(id)) {
                registeredWorlds.add(id);
                arenas.put(id, new ArenaData(p.getWorld()));
                saveArenas();
                p.sendMessage(ChatColor.GREEN + "Arena stworzona!");
            }
            return true;
        }
        if (c.getName().equalsIgnoreCase("cw")) {
            for (ArenaData a : arenas.values()) {
                if (!a.active && a.world.getPlayers().size() < 8) {
                    p.teleport(a.world.getSpawnLocation());
                    p.sendMessage(ChatColor.GREEN + "Dolaczono do lobby!");
                    return true;
                }
            }
            p.sendMessage(ChatColor.RED + "Brak wolnych aren!");
            return true;
        }
        return false;
    }

    // Metody pomocnicze (Receptury, Zapis, BossBar itd.)
    private void loadArenas() {
        List<String> worldNames = getConfig().getStringList("arenas");
        if (worldNames != null) {
            for (String name : worldNames) {
                World w = Bukkit.getWorld(name);
                if (w != null) {
                    registeredWorlds.add(w.getUID());
                    arenas.put(w.getUID(), new ArenaData(w));
                }
            }
        }
    }

    private void saveArenas() {
        List<String> names = registeredWorlds.stream()
                .map(uuid -> Bukkit.getWorld(uuid)).filter(Objects::nonNull)
                .map(World::getName).collect(Collectors.toList());
        getConfig().set("arenas", names);
        saveConfig();
    }

    private void registerCustomRecipes() {
        addRecipe(Material.NETHERITE_INGOT, Material.NETHERITE_SCRAP, Material.DIAMOND, "cw_ing");
        addRecipe(Material.NETHERITE_SWORD, Material.DIAMOND_SWORD, Material.NETHERITE_INGOT, "cw_sw");
        addRecipe(Material.NETHERITE_PICKAXE, Material.DIAMOND_PICKAXE, Material.NETHERITE_INGOT, "cw_pi");
        // ... (analogicznie reszta narzędzi)
    }

    private void addRecipe(Material res, Material i1, Material i2, String k) {
        NamespacedKey key = new NamespacedKey(this, k);
        if (Bukkit.getRecipe(key) != null) return;
        ShapedRecipe r = new ShapedRecipe(key, new ItemStack(res));
        r.shape("AB"); r.setIngredient('A', i1); r.setIngredient('B', i2);
        Bukkit.addRecipe(r);
    }

    private void generateSolidArena(World world) {
        int r = 50;
        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                world.getBlockAt(x, 20, z).setType(Material.BEDROCK);
                world.getBlockAt(x, -31, z).setType(Material.BEDROCK);
                for (int y = -30; y < 20; y++) {
                    Block b = world.getBlockAt(x, y, z);
                    double c = random.nextDouble();
                    if (c < 0.006) b.setType(Material.ANCIENT_DEBRIS);
                    else if (c < 0.05) b.setType(Material.DIAMOND_ORE);
                    else if (c < 0.25) b.setType(Material.IRON_ORE);
                    else if (c < 0.45) b.setType(Material.OAK_LOG);
                    else if (c < 0.55) b.setType(Material.OAK_LEAVES);
                    else b.setType(Material.STONE);
                }
            }
        }
    }

    private void updateActiveArena(ArenaData a) {
        for (Player p : a.world.getPlayers()) {
            if (p.getGameMode() == GameMode.SURVIVAL && !a.eliminated.contains(p.getUniqueId())) {
                updateBossBar(p, a);
                updateCompass(p, a);
            } else removeBossBar(p);
        }
    }

    private void updateBossBar(Player p, ArenaData a) {
        BossBar bar = playerBossBars.computeIfAbsent(p.getUniqueId(), k -> Bukkit.createBossBar("Granica", BarColor.RED, BarStyle.SOLID));
        bar.addPlayer(p);
        double size = a.world.getWorldBorder().getSize();
        bar.setTitle(ChatColor.RED + "Granica: " + (int)size + "x" + (int)size);
    }

    private void updateCompass(Player p, ArenaData a) {
        String pvp = a.pvpGraceTime > 0 ? ChatColor.GREEN + "Ochrona: " + a.pvpGraceTime + "s " : ChatColor.RED + "PvP: ON ";
        p.sendActionBar(pvp);
    }

    private void fillChest(Chest c) {
        Inventory inv = c.getInventory();
        Material[] loot = {Material.IRON_SWORD, Material.GOLDEN_APPLE, Material.DIAMOND, Material.BOW, Material.ARROW};
        for (int i = 0; i < 3; i++) inv.setItem(random.nextInt(inv.getSize()), new ItemStack(loot[random.nextInt(loot.length)], 1));
    }

    private void broadcastToWorld(World w, String m) { for (Player p : w.getPlayers()) p.sendMessage(m); }
    private void removeBossBar(Player p) { BossBar b = playerBossBars.remove(p.getUniqueId()); if (b != null) b.removeAll(); }
}
