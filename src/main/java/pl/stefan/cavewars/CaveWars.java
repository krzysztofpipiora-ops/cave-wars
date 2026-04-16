package pl.stefan.cavewars;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.FireworkMeta;
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

        ArenaData(World world) {
            this.world = world;
        }
    }

    @Override
    public void onEnable() {
        try {
            if (!getDataFolder().exists()) getDataFolder().mkdirs();
            try { saveDefaultConfig(); } catch (Exception ignored) {}

            Bukkit.getPluginManager().registerEvents(this, this);
            registerCustomRecipes();

            Bukkit.getScheduler().runTaskLater(this, () -> {
                loadArenas();
                getLogger().info("System aren CaveWars zostal zainicjalizowany.");
            }, 40L);

            startMainTask();
        } catch (Exception e) {
            e.printStackTrace();
        }
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

    // --- SYSTEM DROPÓW I PRZEPALANIA ---
    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        ArenaData arena = arenas.get(event.getBlock().getWorld().getUID());
        if (arena == null || !arena.active) return;

        Block b = event.getBlock();
        Player p = event.getPlayer();
        Material type = b.getType();

        if (type == Material.OAK_LEAVES) {
            if (random.nextDouble() < 0.20) {
                p.getInventory().addItem(new ItemStack(Material.APPLE));
            }
            b.setType(Material.AIR);
            event.setCancelled(true);
            return;
        }

        if (type == Material.MELON) {
            int ilosc = random.nextInt(5) + 3;
            p.getInventory().addItem(new ItemStack(Material.MELON_SLICE, ilosc));
            b.setType(Material.AIR);
            event.setCancelled(true);
            return;
        }

        // Ukryta skrzynia + dźwięk
        if (random.nextDouble() < 0.005) {
            b.setType(Material.CHEST);
            fillChest((Chest) b.getState());
            p.sendMessage(ChatColor.GOLD + "Znalazles ukryta skrzynie w skale!");
            
            // Dźwięk znalezienia skrzyni
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
            p.playSound(p.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.8f, 1.0f);
            
            event.setCancelled(true);
            return;
        }

        // Automatyczne przepalanie rud
        ItemStack drop = null;
        if (type == Material.IRON_ORE || type == Material.DEEPSLATE_IRON_ORE)
            drop = new ItemStack(Material.IRON_INGOT);
        else if (type == Material.GOLD_ORE || type == Material.DEEPSLATE_GOLD_ORE)
            drop = new ItemStack(Material.GOLD_INGOT);

        if (drop != null) {
            p.getInventory().addItem(drop);
            b.setType(Material.AIR);
            event.setDropItems(false);
            return;
        }

        if (type == Material.ANCIENT_DEBRIS) {
            p.getInventory().addItem(new ItemStack(Material.NETHERITE_SCRAP));
            b.setType(Material.AIR);
            p.sendMessage(ChatColor.DARK_RED + "Wydobyłeś i oczyściłeś starożytny gruz!");
            event.setDropItems(false);
            return;
        }

        // Wszystkie inne bloki trafiają bezpośrednio do EQ
        Collection<ItemStack> drops = b.getDrops(p.getInventory().getItemInMainHand());
        for (ItemStack item : drops) {
            p.getInventory().addItem(item);
        }
        b.setType(Material.AIR);
        event.setDropItems(false);
    }

    // --- SYSTEM LOBBY I STARTU ---
    private void handleLobbyCountdown(ArenaData arena) {
        int count = arena.world.getPlayers().size();
        if (count < 2) {
            arena.countdown = -1;
            return;
        }
        if (arena.countdown == -1) arena.countdown = 60;
        if (count >= 8 && arena.countdown > 15) arena.countdown = 15;

        if (arena.countdown > 0) {
            if (arena.countdown % 10 == 0 || arena.countdown <= 5)
                broadcastToWorld(arena.world, ChatColor.YELLOW + "Gra wystartuje za: " + ChatColor.WHITE + arena.countdown + "s");
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
        arena.world.getWorldBorder().setSize(10, 600);

        broadcastToWorld(arena.world, ChatColor.RED + "Border zaczął się kurczyć do rozmiaru 10x10!");

        for (Player p : arena.world.getPlayers()) {
            Location loc = findSafeSpawn(arena);
            arena.spawnPoints.add(loc);

            for (int x = -1; x <= 1; x++)
                for (int y = 0; y <= 2; y++)
                    for (int z = -1; z <= 1; z++)
                        loc.clone().add(x, y, z).getBlock().setType(Material.AIR);

            p.teleport(loc.add(0.5, 0.1, 0.5));
            p.setGameMode(GameMode.SURVIVAL);
            p.getInventory().clear();
            p.setLevel(30);
            p.getInventory().addItem(new ItemStack(Material.STONE_PICKAXE), new ItemStack(Material.BREAD, 32));
            p.sendMessage(ChatColor.GREEN + "POWODZENIA! Masz 3 minuty ochrony pvp.");
        }

        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (arena.active) {
                arena.world.getWorldBorder().setSize(6, 600);
                broadcastToWorld(arena.world, ChatColor.RED + "UWAGA! Granica swiata zaczela sie kurczyc!");
            }
        }, 12000L);
    }

    private Location findSafeSpawn(ArenaData arena) {
        for (int i = 0; i < 250; i++) {
            Location loc = new Location(arena.world, random.nextInt(70) - 35, -10, random.nextInt(70) - 35);
            boolean far = true;
            for (Location o : arena.spawnPoints)
                if (loc.distance(o) < 15) {
                    far = false;
                    break;
                }
            if (far) return loc;
        }
        return new Location(arena.world, 0, -10, 0);
    }

    // --- WINNER & ELIMINATION ---
    private void checkWinner(ArenaData a) {
        List<Player> alive = a.world.getPlayers().stream()
                .filter(p -> p.getGameMode() == GameMode.SURVIVAL && !a.eliminated.contains(p.getUniqueId()))
                .collect(Collectors.toList());
        if (alive.size() <= 1)
            endGame(a, alive.isEmpty() ? null : alive.get(0));
    }

    private void endGame(ArenaData a, Player winner) {
        if (!a.active) return;
        a.active = false;

        String name = (winner != null) ? winner.getName() : "Remis";
        broadcastToWorld(a.world, ChatColor.GOLD + "=== KONIEC GRY ===");
        broadcastToWorld(a.world, ChatColor.YELLOW + "Zwyciezca: " + ChatColor.WHITE + name);

        // Wystrzelenie fajerwerków
        launchFireworks(a, winner);

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
        }, 160L);
    }

    // Nowa metoda - fajerwerki po zakończeniu gry
    private void launchFireworks(ArenaData arena, Player winner) {
        Location center = arena.world.getSpawnLocation().clone().add(0, 15, 0);

        if (winner != null) {
            // Fajerwerki nad zwycięzcą
            Location winnerLoc = winner.getLocation().clone().add(0, 8, 0);
            for (int i = 0; i < 5; i++) {
                Bukkit.getScheduler().runTaskLater(this, () -> spawnFirework(winnerLoc), i * 8L);
            }
        } else {
            // Remis - fajerwerki w centrum areny
            for (int i = 0; i < 8; i++) {
                Bukkit.getScheduler().runTaskLater(this, () -> spawnFirework(center), i * 6L);
            }
        }
    }

    private void spawnFirework(Location loc) {
        Firework fw = loc.getWorld().spawn(loc, Firework.class);
        FireworkMeta meta = fw.getFireworkMeta();
        meta.addEffect(FireworkEffect.builder()
                .withColor(Color.RED, Color.YELLOW, Color.ORANGE)
                .with(FireworkEffect.Type.BURST)
                .trail(true)
                .flicker(true)
                .build());
        meta.setPower(2);
        fw.setFireworkMeta(meta);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        Player v = e.getEntity();
        ArenaData a = arenas.get(v.getWorld().getUID());
        if (a == null || !a.active) return;

        a.eliminated.add(v.getUniqueId());
        e.getDrops().clear();
        v.sendMessage(ChatColor.RED + "Zostales wyeliminowany!");

        Bukkit.getScheduler().runTaskLater(this, () -> {
            v.setGameMode(GameMode.SPECTATOR);
            checkWinner(a);
        }, 1L);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent e) {
        removeBossBar(e.getPlayer());
    }

    @EventHandler
    public void onPlayerChangeWorld(PlayerChangedWorldEvent e) {
        ArenaData arena = arenas.get(e.getFrom().getUID());
        if (arena != null) {
            removeBossBar(e.getPlayer());
        }
    }

    // --- RECEPTURY ---
    private void registerCustomRecipes() {
        addRecipe(Material.NETHERITE_INGOT, Material.NETHERITE_SCRAP, Material.GOLD_INGOT, "cw_n_ing");
        addRecipe(Material.NETHERITE_SWORD, Material.DIAMOND_SWORD, Material.NETHERITE_INGOT, "cw_n_sw");
        addRecipe(Material.NETHERITE_PICKAXE, Material.DIAMOND_PICKAXE, Material.NETHERITE_INGOT, "cw_n_pi");
        addRecipe(Material.NETHERITE_AXE, Material.DIAMOND_AXE, Material.NETHERITE_INGOT, "cw_n_ax");
        addRecipe(Material.NETHERITE_SHOVEL, Material.DIAMOND_SHOVEL, Material.NETHERITE_INGOT, "cw_n_sh");
        addRecipe(Material.NETHERITE_HELMET, Material.DIAMOND_HELMET, Material.NETHERITE_INGOT, "cw_n_he");
        addRecipe(Material.NETHERITE_CHESTPLATE, Material.DIAMOND_CHESTPLATE, Material.NETHERITE_INGOT, "cw_n_ch");
        addRecipe(Material.NETHERITE_LEGGINGS, Material.DIAMOND_LEGGINGS, Material.NETHERITE_INGOT, "cw_n_le");
        addRecipe(Material.NETHERITE_BOOTS, Material.DIAMOND_BOOTS, Material.NETHERITE_INGOT, "cw_n_bo");
    }

    private void addRecipe(Material res, Material i1, Material i2, String k) {
        try {
            NamespacedKey key = new NamespacedKey(this, k);
            if (Bukkit.getRecipe(key) != null) Bukkit.removeRecipe(key);
            ShapedRecipe r = new ShapedRecipe(key, new ItemStack(res));
            r.shape("AB");
            r.setIngredient('A', i1);
            r.setIngredient('B', i2);
            Bukkit.addRecipe(r);
        } catch (Exception ignored) {}
    }

    // --- KOMENDY ---
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
                p.sendMessage(ChatColor.GREEN + "Swiat zostal zapisany jako arena CaveWars!");
            }
            return true;
        }

        if (c.getName().equalsIgnoreCase("cw")) {
            for (ArenaData a : arenas.values()) {
                if (!a.active && a.world.getPlayers().size() < 12) {
                    p.teleport(a.world.getSpawnLocation());
                    p.setGameMode(GameMode.ADVENTURE);
                    p.sendMessage(ChatColor.YELLOW + "Dolaczyles do areny!");
                    return true;
                }
            }
            p.sendMessage(ChatColor.RED + "Brak wolnych aren!");
            return true;
        }
        return false;
    }

    // --- GENEROWANIE ARENY (STONE 25%) ---
    private void generateSolidArena(World world) {
        int r = 50;
        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                world.getBlockAt(x, 20, z).setType(Material.BEDROCK);
                world.getBlockAt(x, -31, z).setType(Material.BEDROCK);

                for (int y = -30; y < 20; y++) {
                    Block b = world.getBlockAt(x, y, z);
                    double c = random.nextDouble();

                    if (c < 0.008) b.setType(Material.ANCIENT_DEBRIS);        // 0.8%
                    else if (c < 0.04) b.setType(Material.DIAMOND_ORE);       // 3.2%
                    else if (c < 0.10) b.setType(Material.GOLD_ORE);          // 6.0%
                    else if (c < 0.18) b.setType(Material.IRON_ORE);          // 8.0%
                    else if (c < 0.21) b.setType(Material.OBSIDIAN);          // 3.0%
                    else if (c < 0.24) b.setType(Material.GLOWSTONE);         // 3.0%
                    else if (c < 0.27) b.setType(Material.GLASS);             // 3.0%
                    else if (c < 0.30) b.setType(Material.MELON);             // 3.0%
                    else if (c < 0.45) b.setType(Material.OAK_LOG);           // 15.0%
                    else if (c < 0.55) b.setType(Material.OAK_LEAVES);        // 10.0%
                    else b.setType(Material.STONE);                           // 25%
                }
            }
        }
    }

    private void loadArenas() {
        List<String> names = getConfig().getStringList("arenas");
        for (String name : names) {
            World w = Bukkit.getWorld(name);
            if (w != null) {
                registeredWorlds.add(w.getUID());
                arenas.put(w.getUID(), new ArenaData(w));
            }
        }
    }

    private void saveArenas() {
        List<String> names = registeredWorlds.stream()
                .map(u -> Bukkit.getWorld(u))
                .filter(Objects::nonNull)
                .map(World::getName)
                .collect(Collectors.toList());
        getConfig().set("arenas", names);
        saveConfig();
    }

    private void updateActiveArena(ArenaData a) {
        for (Player p : a.world.getPlayers()) {
            if (p.getGameMode() == GameMode.SURVIVAL && !a.eliminated.contains(p.getUniqueId())) {
                updateBossBar(p, a);
                String pvp = a.pvpGraceTime > 0 
                    ? ChatColor.GREEN + "Ochrona: " + a.pvpGraceTime + "s " 
                    : ChatColor.RED + "PvP: ON ";
                p.sendActionBar(pvp + ChatColor.DARK_GRAY + "| " + ChatColor.WHITE 
                    + "Zywi gracze: " + (a.world.getPlayers().size() - a.eliminated.size()));
            } else {
                removeBossBar(p);
            }
        }
    }

    private void updateBossBar(Player p, ArenaData a) {
        BossBar bar = playerBossBars.computeIfAbsent(p.getUniqueId(),
                k -> Bukkit.createBossBar(ChatColor.RED + "Granica", BarColor.RED, BarStyle.SOLID));

        bar.addPlayer(p);
        WorldBorder border = a.world.getWorldBorder();
        double size = border.getSize() / 2.0;
        double centerX = border.getCenter().getX();
        double centerZ = border.getCenter().getZ();

        double distEast  = (centerX + size) - p.getLocation().getX();
        double distWest  = p.getLocation().getX() - (centerX - size);
        double distSouth = (centerZ + size) - p.getLocation().getZ();
        double distNorth = p.getLocation().getZ() - (centerZ - size);

        double minDistance = Math.min(Math.min(distEast, distWest), Math.min(distSouth, distNorth));

        bar.setTitle(ChatColor.RED + "Granica: " + ChatColor.WHITE + (int) minDistance + "m " + ChatColor.RED + "od Ciebie");
        double progress = Math.max(0.0, Math.min(1.0, minDistance / 30.0));
        bar.setProgress(progress);

        bar.setColor(minDistance > 15 ? BarColor.GREEN : BarColor.RED);
    }

    private void fillChest(Chest c) {
        Inventory inv = c.getInventory();
        Material[] loot = {Material.IRON_SWORD, Material.GOLDEN_APPLE, Material.DIAMOND, Material.BOW,
                           Material.ARROW, Material.TNT, Material.ENCHANTED_BOOK};
        for (int i = 0; i < 4; i++) {
            inv.setItem(random.nextInt(inv.getSize()), new ItemStack(loot[random.nextInt(loot.length)], 1));
        }
    }

    private void broadcastToWorld(World w, String m) {
        for (Player p : w.getPlayers()) p.sendMessage(m);
    }

    private void removeBossBar(Player p) {
        BossBar b = playerBossBars.remove(p.getUniqueId());
        if (b != null) b.removeAll();
    }
}
