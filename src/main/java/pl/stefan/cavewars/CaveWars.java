package pl.stefan.cavewars;

import net.milkbowl.vault.economy.Economy;
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
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import java.util.*;
import java.util.stream.Collectors;

public class CaveWars extends JavaPlugin implements Listener {

    private final Random random = new Random();
    private final Map<UUID, ArenaData> arenas = new HashMap<>();
    private final Map<UUID, BossBar> playerBossBars = new HashMap<>();
    private final List<UUID> registeredWorlds = new ArrayList<>();
    private final String SPAWN_WORLD_NAME = "world";

    private Economy economy = null;

    private static class ArenaData {
        World world;
        boolean active = false;
        int countdown = -1;
        int pvpGraceTime = 180;
        List<Location> spawnPoints = new ArrayList<>();
        Set<UUID> eliminated = new HashSet<>();
        double borderRadius = 200.0;
        int borderShrinkRemaining = 0;
        boolean borderEnabled = false;

        ArenaData(World world) {
            this.world = world;
        }
    }

    @Override
    public void onEnable() {
        if (!getDataFolder().exists()) getDataFolder().mkdirs();
        try { saveDefaultConfig(); } catch (Exception ignored) {}

        Bukkit.getPluginManager().registerEvents(this, this);
        registerCustomRecipes();

        if (Bukkit.getPluginManager().getPlugin("Vault") != null) {
            RegisteredServiceProvider<Economy> rsp = Bukkit.getServicesManager().getRegistration(Economy.class);
            if (rsp != null) {
                economy = rsp.getProvider();
                getLogger().info("§aVault Economy pomyślnie podłączony!");
            }
        }

        Bukkit.getScheduler().runTaskLater(this, () -> {
            loadArenas();
            getLogger().info("§aSystem aren CaveWars został zainicjalizowany.");
        }, 40L);

        startMainTask();
    }

    private void startMainTask() {
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (ArenaData arena : arenas.values()) {
                if (arena.active) {
                    updateActiveArena(arena);
                    if (arena.pvpGraceTime > 0) arena.pvpGraceTime--;
                    if (arena.borderEnabled && arena.borderShrinkRemaining > 0) {
                        arena.borderShrinkRemaining--;
                        double progress = (1500.0 - arena.borderShrinkRemaining) / 1500.0;
                        arena.borderRadius = 200.0 - 190.0 * progress;
                    }
                    checkWinner(arena);
                } else if (registeredWorlds.contains(arena.world.getUID())) {
                    handleLobbyCountdown(arena);
                }
            }
        }, 60L, 20L);
    }

    // ==================== FREEZE TYLKO DLA GRACZY NA ARENIE ====================
    private void applyStartFreeze(ArenaData arena) {
        for (Player p : arena.world.getPlayers()) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 200, 255, false, false));
            p.addPotionEffect(new PotionEffect(PotionEffectType.JUMP, 200, 200, false, false));
            p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 200, 0, false, false));

            p.sendTitle(ChatColor.RED + "§lFREEZE!", ChatColor.GRAY + "Gra zaczyna się za chwilę...", 0, 60, 20);
            p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_LAND, 1.0f, 0.5f);
        }
    }

    // ==================== DROP POD GRACZEM (przy pełnym eq) ====================
    private void dropItemUnderPlayer(Player p, ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return;

        HashMap<Integer, ItemStack> leftover = p.getInventory().addItem(item);

        for (ItemStack rest : leftover.values()) {
            if (rest != null && rest.getAmount() > 0) {
                p.getWorld().dropItemNaturally(p.getLocation().add(0, 0.3, 0), rest);
            }
        }
    }

    // ==================== LOBBY ====================
    private void handleLobbyCountdown(ArenaData arena) {
        int count = arena.world.getPlayers().size();
        if (count < 2) {
            arena.countdown = -1;
            return;
        }
        if (arena.countdown == -1) arena.countdown = 60;
        if (count >= 8 && arena.countdown > 15) arena.countdown = 15;

        if (arena.countdown > 0) {
            if (arena.countdown % 10 == 0 || arena.countdown <= 5) {
                showCountdownTitle(arena.world, arena.countdown);
            }

            if (arena.countdown == 1) {
                applyStartFreeze(arena);
            }

            arena.countdown--;
        } else if (arena.countdown == 0) {
            arena.countdown = -1;
            generateSolidArena(arena.world);
            startMatch(arena);
        }
    }

    private void showCountdownTitle(World world, int seconds) {
        String title;
        String subtitle;
        if (seconds > 10) {
            title = ChatColor.YELLOW + "" + ChatColor.BOLD + "START ZA";
            subtitle = ChatColor.GOLD + "" + ChatColor.BOLD + seconds + ChatColor.YELLOW + " sekund";
        } else {
            title = ChatColor.RED + "" + ChatColor.BOLD + String.valueOf(seconds);
            subtitle = ChatColor.YELLOW + "Przygotuj się...";
        }
        for (Player p : world.getPlayers()) {
            p.sendTitle(title, subtitle, 0, 45, 10);
            if (seconds <= 5) {
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.8f);
            }
        }
    }

    // ==================== BORDER ŚWIATA ====================
    private void setArenaWorldBorder(World world) {
        WorldBorder wb = world.getWorldBorder();
        wb.setCenter(0, 0);
        wb.setSize(200);
        wb.setDamageAmount(0);
        wb.setDamageBuffer(0);
        wb.setWarningDistance(0);
        wb.setWarningTime(0);
    }

    // ==================== START GRY ====================
    private void startMatch(ArenaData arena) {
        arena.active = true;
        arena.pvpGraceTime = 180;
        arena.eliminated.clear();
        arena.spawnPoints.clear();

        setArenaWorldBorder(arena.world);

        arena.borderRadius = 200.0;
        arena.borderShrinkRemaining = 1500;
        arena.borderEnabled = true;

        for (Player p : arena.world.getPlayers()) {
            p.removePotionEffect(PotionEffectType.SLOW);
            p.removePotionEffect(PotionEffectType.JUMP);
            p.removePotionEffect(PotionEffectType.BLINDNESS);

            p.sendTitle(ChatColor.GREEN + "" + ChatColor.BOLD + "GRA ZACZĘTA!", ChatColor.GOLD + "Powodzenia na arenie!", 10, 70, 20);

            Location loc = findSafeSpawn(arena);
            arena.spawnPoints.add(loc);

            for (int x = -1; x <= 1; x++)
                for (int y = 0; y <= 2; y++)
                    for (int z = -1; z <= 1; z++)
                        loc.clone().add(x, y, z).getBlock().setType(Material.AIR);

            p.teleport(loc.clone().add(0.5, 0.1, 0.5));
            p.setGameMode(GameMode.SURVIVAL);
            p.getInventory().clear();
            p.getInventory().setArmorContents(null);
            setPlayerLevel30(p);
            p.getInventory().addItem(new ItemStack(Material.STONE_PICKAXE), new ItemStack(Material.BREAD, 32));
        }

        broadcastToWorld(arena.world, ChatColor.RED + "§lSztuczna granica zaczęła się kurczyć!");
        broadcastToWorld(arena.world, ChatColor.RED + "Z 200×200 do 10×10 w ciągu 25 minut");
        broadcastToWorld(arena.world, ChatColor.GREEN + "Ochrona PvP aktywna przez 180 sekund!");
    }

    private void setPlayerLevel30(Player p) {
        p.setLevel(30);
        p.setExp(0.0f);
        p.setTotalExperience(0);
    }

    private Location findSafeSpawn(ArenaData arena) {
        for (int i = 0; i < 300; i++) {
            Location loc = new Location(arena.world, random.nextInt(180) - 90, -10, random.nextInt(180) - 90);
            boolean far = true;
            for (Location o : arena.spawnPoints) {
                if (loc.distance(o) < 25) {
                    far = false;
                    break;
                }
            }
            if (far) return loc;
        }
        return new Location(arena.world, 0, -10, 0);
    }

    private void applyBorderEffects(Player p, ArenaData arena) {
        if (!arena.borderEnabled) return;
        double x = p.getLocation().getX();
        double z = p.getLocation().getZ();
        double r = arena.borderRadius;
        if (Math.abs(x) > r || Math.abs(z) > r) {
            p.damage(2.0);
            p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 60, 0, false, false));
            if (random.nextInt(8) == 0) {
                p.sendTitle(ChatColor.RED + "§lPOZA GRANICĄ!", ChatColor.GRAY + "Wracaj do środka!", 0, 25, 10);
            }
        } else {
            p.removePotionEffect(PotionEffectType.BLINDNESS);
        }
    }

    private void updateActiveArena(ArenaData a) {
        setArenaWorldBorder(a.world);

        for (Player p : a.world.getPlayers()) {
            if (p.getGameMode() == GameMode.SURVIVAL && !a.eliminated.contains(p.getUniqueId())) {
                updateBossBar(p, a);
                sendDistanceActionBar(p, a);
                applyBorderEffects(p, a);
                setPlayerLevel30(p);
            } else {
                removeBossBar(p);
            }
        }
    }

    private void sendDistanceActionBar(Player p, ArenaData arena) {
        Player nearest = null;
        double minDistance = Double.MAX_VALUE;
        for (Player other : arena.world.getPlayers()) {
            if (other.equals(p)) continue;
            if (other.getGameMode() != GameMode.SURVIVAL) continue;
            if (arena.eliminated.contains(other.getUniqueId())) continue;
            double dist = p.getLocation().distance(other.getLocation());
            if (dist < minDistance) {
                minDistance = dist;
                nearest = other;
            }
        }
        String pvp = arena.pvpGraceTime > 0
                ? ChatColor.GREEN + "Ochrona: " + arena.pvpGraceTime + "s "
                : ChatColor.RED + "PvP: ON ";
        if (nearest != null) {
            p.sendActionBar(pvp + ChatColor.DARK_GRAY + " | " + ChatColor.GOLD + "Najbliższy: " + ChatColor.WHITE + (int) minDistance + " bloków");
        } else {
            p.sendActionBar(pvp + ChatColor.DARK_GRAY + " | " + ChatColor.YELLOW + "Brak innych żywych graczy");
        }
    }

    private void updateBossBar(Player p, ArenaData a) {
        BossBar bar = playerBossBars.computeIfAbsent(p.getUniqueId(),
                k -> Bukkit.createBossBar(ChatColor.RED + "Granica", BarColor.RED, BarStyle.SOLID));
        bar.addPlayer(p);
        double r = a.borderRadius;
        double minDistance = Math.min(r - Math.abs(p.getLocation().getX()), r - Math.abs(p.getLocation().getZ()));
        bar.setTitle(ChatColor.RED + "Granica: " + ChatColor.WHITE + (int) minDistance + "m od Ciebie");
        bar.setProgress(Math.max(0.0, Math.min(1.0, minDistance / 50.0)));
        bar.setColor(minDistance > 25 ? BarColor.GREEN : BarColor.RED);
    }

    private void handlePlayerDeath(Player victim, Player killer) {
        if (victim != null) victim.getWorld().strikeLightningEffect(victim.getLocation());
        ArenaData arena = arenas.get(victim.getWorld().getUID());
        if (arena == null || !arena.active) return;

        String victimName = victim.getName();
        String killerName = (killer != null) ? killer.getName() : "Nieznana siła";
        broadcastToWorld(arena.world, ChatColor.RED + victimName + ChatColor.GRAY + " został zabity przez " + ChatColor.GOLD + killerName);

        if (killer != null) {
            int gain = random.nextInt(6) + 4;
            if (economy != null) economy.depositPlayer(killer, gain);
            killer.sendMessage(ChatColor.GREEN + "✓ Zabiłeś " + ChatColor.RED + victimName);
            killer.sendMessage(ChatColor.YELLOW + " +" + gain + " punktów");
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        Player victim = e.getEntity();
        ArenaData a = arenas.get(victim.getWorld().getUID());
        if (a == null || !a.active) return;

        Player killer = victim.getKiller();
        a.eliminated.add(victim.getUniqueId());

        e.setKeepInventory(false);
        e.getDrops().clear();

        victim.sendMessage(ChatColor.RED + "Zostałeś wyeliminowany!");
        handlePlayerDeath(victim, killer);

        Bukkit.getScheduler().runTaskLater(this, () -> {
            victim.setGameMode(GameMode.SPECTATOR);
            resetPlayerAfterArena(victim);
            setPlayerLevel30(victim);
            checkWinner(a);
        }, 1L);
    }

    private void resetPlayerAfterArena(Player p) {
        p.getInventory().clear();
        p.getInventory().setArmorContents(null);
        p.setHealth(20.0);
        p.setFoodLevel(20);
        p.removePotionEffect(PotionEffectType.BLINDNESS);
        removeBossBar(p);
    }

    private void checkWinner(ArenaData a) {
        List<Player> alive = a.world.getPlayers().stream()
                .filter(p -> p.getGameMode() == GameMode.SURVIVAL && !a.eliminated.contains(p.getUniqueId()))
                .collect(Collectors.toList());
        if (alive.size() <= 1) {
            endGame(a, alive.isEmpty() ? null : alive.get(0));
        }
    }

    private void endGame(ArenaData a, Player winner) {
        if (!a.active) return;
        a.active = false;
        a.borderEnabled = false;

        String name = (winner != null) ? winner.getName() : "Remis";
        for (Player p : a.world.getPlayers()) {
            p.sendTitle(ChatColor.GOLD + "§lKONIEC GRY", ChatColor.YELLOW + "Zwycięzca: " + ChatColor.WHITE + name, 10, 140, 40);
        }

        if (winner != null) {
            if (economy != null) economy.depositPlayer(winner, 50.0);
            winner.sendMessage(ChatColor.GOLD + "§l+50 punktów za wygranie areny!");
        }

        launchFireworks(a, winner);

        Bukkit.getScheduler().runTaskLater(this, () -> {
            World spawnWorld = Bukkit.getWorld(SPAWN_WORLD_NAME);
            for (Player p : a.world.getPlayers()) {
                p.teleport(spawnWorld != null ? spawnWorld.getSpawnLocation() : a.world.getSpawnLocation());
                p.setGameMode(GameMode.ADVENTURE);
                resetPlayerAfterArena(p);
                setPlayerLevel30(p);
            }
        }, 160L);
    }

    private void launchFireworks(ArenaData arena, Player winner) {
        if (winner != null) {
            Location loc = winner.getLocation().clone().add(0, 8, 0);
            for (int i = 0; i < 5; i++)
                Bukkit.getScheduler().runTaskLater(this, () -> spawnFirework(loc), i * 8L);
        } else {
            Location center = arena.world.getSpawnLocation().clone().add(0, 15, 0);
            for (int i = 0; i < 8; i++)
                Bukkit.getScheduler().runTaskLater(this, () -> spawnFirework(center), i * 6L);
        }
    }

    private void spawnFirework(Location loc) {
        Firework fw = loc.getWorld().spawn(loc, Firework.class);
        FireworkMeta meta = fw.getFireworkMeta();
        meta.addEffect(FireworkEffect.builder()
                .withColor(Color.RED, Color.YELLOW, Color.ORANGE, Color.WHITE)
                .with(FireworkEffect.Type.BURST)
                .trail(true).flicker(true).build());
        meta.setPower(2);
        fw.setFireworkMeta(meta);
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        if (!(event.getDamager() instanceof Player attacker)) return;
        ArenaData arena = arenas.get(victim.getWorld().getUID());
        if (arena == null || !arena.active) return;
        if (arena.pvpGraceTime > 0) {
            event.setCancelled(true);
            attacker.sendMessage(ChatColor.RED + "Ochrona PvP jest jeszcze aktywna! (" + arena.pvpGraceTime + "s)");
        }
    }

    // ==================== GŁÓWNA METODA Z DROPAMI POD GRACZEM ====================
    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        ArenaData arena = arenas.get(event.getBlock().getWorld().getUID());
        if (arena == null || !arena.active) return;

        Player p = event.getPlayer();
        Block b = event.getBlock();
        Material type = b.getType();

        if (type == Material.OAK_LEAVES) {
            if (random.nextDouble() < 0.20) dropItemUnderPlayer(p, new ItemStack(Material.APPLE));
            b.setType(Material.AIR);
            event.setCancelled(true);
            return;
        }

        if (type == Material.MELON) {
            int amount = random.nextInt(5) + 3;
            dropItemUnderPlayer(p, new ItemStack(Material.MELON_SLICE, amount));
            b.setType(Material.AIR);
            event.setCancelled(true);
            return;
        }

        if (random.nextDouble() < 0.005) {
            b.setType(Material.CHEST);
            fillChest((Chest) b.getState());
            p.sendMessage(ChatColor.GOLD + "Znalazłeś ukrytą skrzynię w skale!");
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
            p.playSound(p.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.8f, 1.0f);
            event.setCancelled(true);
            return;
        }

        ItemStack drop = null;
        if (type == Material.IRON_ORE || type == Material.DEEPSLATE_IRON_ORE)
            drop = new ItemStack(Material.IRON_INGOT);
        else if (type == Material.GOLD_ORE || type == Material.DEEPSLATE_GOLD_ORE)
            drop = new ItemStack(Material.GOLD_INGOT);
        else if (type == Material.ANCIENT_DEBRIS) {
            drop = new ItemStack(Material.NETHERITE_SCRAP);
            p.sendMessage(ChatColor.DARK_RED + "Wydobyłeś i oczyściłeś starożytny gruz!");
        }

        if (drop != null) {
            dropItemUnderPlayer(p, drop);
            b.setType(Material.AIR);
            event.setDropItems(false);
            return;
        }

        // Normalne dropy
        Collection<ItemStack> drops = b.getDrops(p.getInventory().getItemInMainHand());
        for (ItemStack item : drops) {
            dropItemUnderPlayer(p, item);
        }

        b.setType(Material.AIR);
        event.setDropItems(false);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        ArenaData arena = arenas.get(event.getBlock().getWorld().getUID());
        if (arena != null && arena.active) {
            event.setCancelled(false);
        }
    }

    @Override
    public boolean onCommand(CommandSender s, Command c, String l, String[] args) {
        if (!(s instanceof Player p)) {
            s.sendMessage(ChatColor.RED + "Tylko gracze mogą używać tej komendy!");
            return true;
        }
        if (c.getName().equalsIgnoreCase("cwcreate")) {
            if (!p.isOp()) {
                p.sendMessage(ChatColor.RED + "Nie masz uprawnień!");
                return true;
            }
            UUID id = p.getWorld().getUID();
            if (!registeredWorlds.contains(id)) {
                registeredWorlds.add(id);
                arenas.put(id, new ArenaData(p.getWorld()));
                saveArenas();
                p.sendMessage(ChatColor.GREEN + "Świat został zapisany jako arena CaveWars!");
            } else {
                p.sendMessage(ChatColor.YELLOW + "Ten świat jest już zarejestrowany.");
            }
            return true;
        }
        if (c.getName().equalsIgnoreCase("cw")) {
            ArenaData current = arenas.get(p.getWorld().getUID());
            if (current != null && current.active) {
                p.sendMessage(ChatColor.RED + "Już jesteś na aktywnej arenie!");
                return true;
            }

            ArenaData bestArena = null;
            int maxPlayers = -1;
            for (ArenaData a : arenas.values()) {
                if (a.active) continue;
                int playerCount = a.world.getPlayers().size();
                if (playerCount < 12 && playerCount > maxPlayers) {
                    maxPlayers = playerCount;
                    bestArena = a;
                }
            }
            if (bestArena != null) {
                p.teleport(bestArena.world.getSpawnLocation().add(0.5, 1, 0.5));
                p.setGameMode(GameMode.ADVENTURE);
                p.sendMessage(ChatColor.GREEN + "Dołączyłeś do areny! (" + (maxPlayers + 1) + "/12 graczy)");
                handlePlayerJoinArena(p, bestArena);
                return true;
            }
            p.sendMessage(ChatColor.RED + "Obecnie nie ma wolnych aren do dołączenia!");
            return true;
        }
        return false;
    }

    private void handlePlayerJoinArena(Player p, ArenaData arena) {
        int current = arena.world.getPlayers().size();
        String msg = ChatColor.GRAY + p.getName() + " dołączył na arenę!!! (" + current + "/12)";
        broadcastToWorld(arena.world, msg);
    }

    @EventHandler public void onPlayerQuit(PlayerQuitEvent e) { removeBossBar(e.getPlayer()); }
    @EventHandler public void onPlayerChangeWorld(PlayerChangedWorldEvent e) {
        if (arenas.containsKey(e.getFrom().getUID())) resetPlayerAfterArena(e.getPlayer());
    }

    // ==================== RECEPTURY ====================
    private void registerCustomRecipes() {
        NamespacedKey netheriteKey = new NamespacedKey(this, "cw_netherite_ingot");
        if (Bukkit.getRecipe(netheriteKey) != null) Bukkit.removeRecipe(netheriteKey);
        ShapelessRecipe netheriteRecipe = new ShapelessRecipe(netheriteKey, new ItemStack(Material.NETHERITE_INGOT));
        netheriteRecipe.addIngredient(4, Material.NETHERITE_SCRAP);
        netheriteRecipe.addIngredient(4, Material.GOLD_INGOT);
        Bukkit.addRecipe(netheriteRecipe);

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

    private void generateSolidArena(World world) {
        int r = 100;
        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                world.getBlockAt(x, 20, z).setType(Material.BEDROCK);
                world.getBlockAt(x, -31, z).setType(Material.BEDROCK);
                for (int y = -30; y < 20; y++) {
                    Block b = world.getBlockAt(x, y, z);
                    double c = random.nextDouble();
                    if (c < 0.008) b.setType(Material.ANCIENT_DEBRIS);
                    else if (c < 0.016) b.setType(Material.BOOKSHELF);
                    else if (c < 0.056) b.setType(Material.LAPIS_ORE);
                    else if (c < 0.096) b.setType(Material.DIAMOND_ORE);
                    else if (c < 0.156) b.setType(Material.GOLD_ORE);
                    else if (c < 0.236) b.setType(Material.IRON_ORE);
                    else if (c < 0.266) b.setType(Material.OBSIDIAN);
                    else if (c < 0.296) b.setType(Material.GLOWSTONE);
                    else if (c < 0.326) b.setType(Material.GLASS);
                    else if (c < 0.356) b.setType(Material.MELON);
                    else if (c < 0.506) b.setType(Material.OAK_LOG);
                    else if (c < 0.606) b.setType(Material.OAK_LEAVES);
                    else b.setType(Material.STONE);
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
