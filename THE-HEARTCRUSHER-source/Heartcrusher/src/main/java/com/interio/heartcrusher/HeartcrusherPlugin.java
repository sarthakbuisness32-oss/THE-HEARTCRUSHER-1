
package com.interio.heartcrusher;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class HeartcrusherPlugin extends JavaPlugin implements Listener {

    private NamespacedKey itemKey;

    private final Map<UUID, Long> piercerCooldowns = new HashMap<>();
    private final Map<UUID, Long> wrathCooldowns = new HashMap<>();
    private final Map<UUID, Long> novaCooldowns = new HashMap<>();
    private final Map<UUID, Long> voidCooldowns = new HashMap<>();
    private final Map<UUID, Long> protectedUntil = new HashMap<>();

    private static final long COOLDOWN_MS = 4000L;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        itemKey = new NamespacedKey(this, "heartcrusher");

        Bukkit.getPluginManager().registerEvents(this, this);
        registerRecipe();

        if (getCommand("heartcrusher") != null) {
            getCommand("heartcrusher").setExecutor(this);
        }

        Bukkit.getScheduler().runTaskTimer(
                this, this::refreshWielderEffects, 1L, 20L
        );

        getLogger().info("THE HEARTCRUSHER has awakened.");
    }

    @Override
    public void onDisable() {
        saveConfig();
        getLogger().info("THE HEARTCRUSHER has been disabled.");
    }

    // --------------------------------------------------
    // ITEM
    // --------------------------------------------------

    private ItemStack createHeartcrusher() {
        ItemStack mace = new ItemStack(Material.MACE);
        ItemMeta meta = mace.getItemMeta();

        meta.setDisplayName(
                ChatColor.DARK_RED + "" + ChatColor.BOLD + "THE HEARTCRUSHER"
        );

        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "A relic forged for a legend.");
        lore.add(ChatColor.DARK_RED + "Level 1: Heartcrusher");
        lore.add(ChatColor.GRAY + "Kills: 0 / 3");
        lore.add("");
        lore.add(ChatColor.RED + "Sneak + Right Click: Divine Piercer");
        lore.add(ChatColor.RED + "Sneak + Left Click: Heaven's Wrath");
        lore.add(ChatColor.GRAY + "Sneak + F: Unlocks at Level 2");
        lore.add(ChatColor.GRAY + "Ability cooldown: 4 seconds");

        meta.setLore(lore);
        meta.setUnbreakable(true);

        meta.getPersistentDataContainer().set(
                itemKey, PersistentDataType.BYTE, (byte) 1
        );

        meta.addEnchant(Enchantment.DENSITY, 6, true);
        meta.addEnchant(Enchantment.BREACH, 6, true);
        meta.addEnchant(Enchantment.WIND_BURST, 2, true);
        meta.addEnchant(Enchantment.FIRE_ASPECT, 5, true);

        mace.setItemMeta(meta);
        return mace;
    }

    private boolean isHeartcrusher(ItemStack item) {
        if (item == null || item.getType() != Material.MACE
                || !item.hasItemMeta()) {
            return false;
        }

        Byte marker = item.getItemMeta()
                .getPersistentDataContainer()
                .get(itemKey, PersistentDataType.BYTE);

        return marker != null && marker == (byte) 1;
    }

    private void updateMaceLore(Player player) {
        int kills = getKills(player);
        int level = getLevel(player);

        String levelName = getLevelName(level);

        for (ItemStack item : player.getInventory().getContents()) {
            if (!isHeartcrusher(item)) continue;

            ItemMeta meta = item.getItemMeta();
            if (meta == null) continue;

            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "A relic forged for a legend.");
            lore.add(ChatColor.DARK_RED + "Level " + level + ": " + levelName);
            lore.add(ChatColor.GRAY + "Kills: " + kills + " / "
                    + (level == 3 ? "MAX" : (level * 3)));
            lore.add("");

            lore.add(ChatColor.RED + "Sneak + Right Click: Divine Piercer");
            lore.add(ChatColor.RED + "Sneak + Left Click: Heaven's Wrath");

            if (level >= 2) {
                lore.add(ChatColor.GOLD + "Sneak + F: Infernal Nova");
            } else {
                lore.add(ChatColor.GRAY + "Sneak + F: Unlocks at Level 2");
            }

            if (level >= 3) {
                lore.add(ChatColor.DARK_PURPLE + "Sneak + F: Void Dominion");
            }

            lore.add(ChatColor.GRAY + "Ability cooldown: 4 seconds");

            meta.setLore(lore);
            item.setItemMeta(meta);
        }
    }

    private void registerRecipe() {
        NamespacedKey recipeKey =
                new NamespacedKey(this, "heartcrusher_recipe");

        Bukkit.removeRecipe(recipeKey);

        ShapedRecipe recipe =
                new ShapedRecipe(recipeKey, createHeartcrusher());

        recipe.shape(" A ", "BCB", " D ");
        recipe.setIngredient('A', Material.NETHER_STAR);
        recipe.setIngredient('B', Material.NETHERITE_INGOT);
        recipe.setIngredient('C', Material.HEAVY_CORE);
        recipe.setIngredient('D', Material.BREEZE_ROD);

        Bukkit.addRecipe(recipe);
    }

    // --------------------------------------------------
    // COMMAND
    // --------------------------------------------------

    @Override
    public boolean onCommand(
            CommandSender sender,
            Command command,
            String label,
            String[] args
    ) {
        if (args.length != 2 || !args[0].equalsIgnoreCase("give")) {
            sender.sendMessage(
                    ChatColor.RED + "Usage: /heartcrusher give <player>"
            );
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[1]);

        if (target == null) {
            sender.sendMessage(ChatColor.RED + "That player is not online.");
            return true;
        }

        target.getInventory().addItem(createHeartcrusher());
        updateMaceLore(target);

        sender.sendMessage(
                ChatColor.GREEN + "Gave THE HEARTCRUSHER to "
                        + target.getName() + "."
        );

        target.sendMessage(
                ChatColor.DARK_RED + "" + ChatColor.BOLD + "THE HEARTCRUSHER "
                        + ChatColor.GRAY + "has chosen you."
        );

        return true;
    }

    // --------------------------------------------------
    // KILL PROGRESSION
    // --------------------------------------------------

    private int getKills(Player player) {
        return getConfig().getInt(
                "players." + player.getUniqueId() + ".kills", 0
        );
    }

    private int getLevel(Player player) {
        return Math.min(3, 1 + getKills(player) / 3);
    }

    private String getLevelName(int level) {
        if (level >= 3) return "SoulCrusher";
        if (level == 2) return "God's Wrath";
        return "Heartcrusher";
    }

    @EventHandler
    public void onEntityKill(EntityDeathEvent event) {
        LivingEntity victim = event.getEntity();
        Player killer = victim.getKiller();

        if (killer == null) return;

        boolean holding =
                isHeartcrusher(killer.getInventory().getItemInMainHand())
                || isHeartcrusher(killer.getInventory().getItemInOffHand());

        if (!holding) return;

        int oldLevel = getLevel(killer);
        int kills = getKills(killer) + 1;

        getConfig().set(
                "players." + killer.getUniqueId() + ".kills", kills
        );

        saveConfig();

        int newLevel = getLevel(killer);

        updateMaceLore(killer);

        killer.sendMessage(
                ChatColor.RED + "Heartcrusher kills: "
                        + ChatColor.WHITE + kills
        );

        if (newLevel > oldLevel) {
            killer.sendTitle(
                    ChatColor.DARK_RED + getLevelName(newLevel),
                    ChatColor.GOLD + "Your Heartcrusher has evolved!",
                    10, 60, 20
            );

            killer.playSound(
                    killer.getLocation(),
                    Sound.UI_TOAST_CHALLENGE_COMPLETE,
                    1.0f, 0.8f
            );

            killer.getWorld().spawnParticle(
                    Particle.SOUL_FIRE_FLAME,
                    killer.getLocation().add(0, 1, 0),
                    60, 1, 1, 1, 0.05
            );
        }
    }

    // --------------------------------------------------
    // NORMAL RIGHT AND LEFT CLICK ABILITIES
    // --------------------------------------------------

    @EventHandler
    public void onUse(PlayerInteractEvent event) {
        Player player = event.getPlayer();

        if (event.getHand() != EquipmentSlot.HAND
                || !player.isSneaking()
                || !isHeartcrusher(event.getItem())) {
            return;
        }

        Action action = event.getAction();

        if (action == Action.RIGHT_CLICK_AIR
                || action == Action.RIGHT_CLICK_BLOCK) {

            event.setCancelled(true);

            if (isOnCooldown(player, piercerCooldowns)) {
                sendCooldownMessage(player, piercerCooldowns);
                return;
            }

            startCooldown(player, piercerCooldowns);
            divinePiercer(player);

        } else if (action == Action.LEFT_CLICK_AIR
                || action == Action.LEFT_CLICK_BLOCK) {

            event.setCancelled(true);

            if (isOnCooldown(player, wrathCooldowns)) {
                sendCooldownMessage(player, wrathCooldowns);
                return;
            }

            startCooldown(player, wrathCooldowns);
            heavensWrath(player);
        }
    }

    // Sneak + F abilities
    @EventHandler
   public void onSwapHands(PlayerSwapHandItemsEvent event) {
       Player player = event.getPlayer();
        if (!player.isSneaking()) return;

        boolean holding =
                isHeartcrusher(player.getInventory().getItemInMainHand())
                || isHeartcrusher(player.getInventory().getItemInOffHand());

        if (!holding) return;

        event.setCancelled(true);

        int level = getLevel(player);

        if (level < 2) {
            player.sendMessage(
                    ChatColor.RED + "You need 3 kills to unlock Infernal Nova!"
            );
            return;
        }

        if (level == 2) {
            if (isOnCooldown(player, novaCooldowns)) {
                sendCooldownMessage(player, novaCooldowns);
                return;
            }

            startCooldown(player, novaCooldowns);
            infernalNova(player);

        } else {
            if (isOnCooldown(player, voidCooldowns)) {
                sendCooldownMessage(player, voidCooldowns);
                return;
            }

            startCooldown(player, voidCooldowns);
            voidDominion(player);
        }
    }

    private Location getAimLocation(Player player) {
        RayTraceResult result = player.getWorld().rayTraceBlocks(
                player.getEyeLocation(),
                player.getEyeLocation().getDirection(),
                96.0
        );

        if (result != null && result.getHitPosition() != null) {
            Vector hit = result.getHitPosition();

            return new Location(
                    player.getWorld(),
                    hit.getX(), hit.getY(), hit.getZ()
            );
        }

        return player.getEyeLocation().add(
                player.getEyeLocation().getDirection().multiply(24)
        );
    }

    // --------------------------------------------------
    // DIVINE PIERCER - ACTUAL PRIMED TNT
    // --------------------------------------------------

    private void divinePiercer(Player player) {
        Location target = getAimLocation(player);
        World world = target.getWorld();

        if (world == null) return;

        int level = getLevel(player);
        double centerX = target.getBlockX() + 0.5;
        double centerZ = target.getBlockZ() + 0.5;

        double startY = Math.min(
                world.getMaxHeight() - 3,
                target.getY() + 32
        );

        double power = 3.5 + (level - 1) * 0.75;

        protectWielder(player, 12000L);

        player.sendMessage(ChatColor.DARK_RED + "DIVINE PIERCER!");
        player.playSound(
                player.getLocation(),
                Sound.ENTITY_WITHER_SHOOT,
                1.0f, 0.7f
        );

        // Spawn real TNT entities in a descending column.
        for (int i = 0; i < 9; i++) {
            double y = startY - (i * 3.5);

            if (y <= target.getY() || y < world.getMinHeight()) {
                continue;
            }

            Location spawnAt = new Location(
                    world, centerX, y, centerZ
            );

            world.spawn(spawnAt, TNTPrimed.class, tnt -> {
                tnt.setSource(player);
                tnt.setFuseTicks(55);
                tnt.setYield((float) power);
                tnt.setIsIncendiary(false);
                tnt.setVelocity(new Vector(0, -0.35, 0));
            });

            world.spawnParticle(
                    Particle.FLAME, spawnAt,
                    12, 0.3, 0.3, 0.3, 0.02
            );

            world.spawnParticle(
                    Particle.SMOKE, spawnAt,
                    8, 0.3, 0.3, 0.3, 0.01
            );
        }
    }

    // --------------------------------------------------
    // HEAVEN'S WRATH - LARGE TNT RINGS
    // --------------------------------------------------

    private void heavensWrath(Player player) {
        Location target = getAimLocation(player);
        World world = target.getWorld();

        if (world == null) return;

        int level = getLevel(player);

        int rings = 8 + (level - 1);
        int perRing = 12;

        double centerX = target.getBlockX() + 0.5;
        double centerZ = target.getBlockZ() + 0.5;

        double baseY = Math.min(
                world.getMaxHeight() - 2,
                target.getY() + 22
        );

        float power = 4.0f + (level - 1) * 0.5f;

        protectWielder(player, 12000L);

        player.sendMessage(ChatColor.DARK_RED + "HEAVEN'S WRATH!");

        player.playSound(
                player.getLocation(),
                Sound.ENTITY_LIGHTNING_BOLT_THUNDER,
                1.0f, 0.8f
        );

        for (int ring = 0; ring < rings; ring++) {
            double radius = 2.5 + ring * 2.0;

            for (int i = 0; i < perRing; i++) {
                double angle = (2.0 * Math.PI * i) / perRing;

                double spawnX = centerX + Math.cos(angle) * radius;
                double spawnZ = centerZ + Math.sin(angle) * radius;

                Location spawnAt = new Location(
                        world, spawnX, baseY, spawnZ
                );

                int ringFuse = 50 + ring * 2;

                world.spawn(spawnAt, TNTPrimed.class, tnt -> {
                    tnt.setSource(player);
                    tnt.setFuseTicks(ringFuse);
                    tnt.setYield(power);
                    tnt.setIsIncendiary(false);
                });
            }
        }
    }

    // --------------------------------------------------
    // LEVEL 2 - INFERNAL NOVA
    // --------------------------------------------------

    private void infernalNova(Player player) {
        Location target = getAimLocation(player);
        World world = target.getWorld();

        if (world == null) return;

        protectWielder(player, 12000L);

        player.sendMessage(ChatColor.GOLD + "INFERNAL NOVA!");

        player.playSound(
                target,
                Sound.ENTITY_BLAZE_SHOOT,
                1.5f, 0.6f
        );

        world.spawnParticle(
                Particle.SOUL_FIRE_FLAME,
                target,
                100, 3, 1, 3, 0.1
        );

        world.spawnParticle(
                Particle.FLAME,
                target,
                150, 3, 1, 3, 0.08
        );

        for (int i = 0; i < 16; i++) {
            double angle = 2.0 * Math.PI * i / 16.0;

            Location spawnAt = target.clone().add(
                    Math.cos(angle) * 6,
                    12,
                    Math.sin(angle) * 6
            );

            world.spawn(spawnAt, TNTPrimed.class, tnt -> {
                tnt.setSource(player);
                tnt.setFuseTicks(45);
                tnt.setYield(4.0f);
                tnt.setIsIncendiary(false);
            });
        }
    }

    // --------------------------------------------------
    // LEVEL 3 - VOID DOMINION
    // --------------------------------------------------

    private void voidDominion(Player player) {
        Location target = getAimLocation(player);
        World world = target.getWorld();

        if (world == null) return;

        protectWielder(player, 12000L);

        player.sendMessage(
                ChatColor.DARK_PURPLE + "" + ChatColor.BOLD
                        + "VOID DOMINION!"
        );

        player.playSound(
                target,
                Sound.ENTITY_WARDEN_SONIC_BOOM,
                1.5f, 0.5f
        );

        world.spawnParticle(
                Particle.REVERSE_PORTAL,
                target,
                180, 5, 3, 5, 0.15
        );

        world.spawnParticle(
                Particle.SOUL_FIRE_FLAME,
                target,
                100, 4, 2, 4, 0.1
        );

        // Outer ring of TNT.
        for (int i = 0; i < 24; i++) {
            double angle = 2.0 * Math.PI * i / 24.0;

            Location spawnAt = target.clone().add(
                    Math.cos(angle) * 9,
                    16,
                    Math.sin(angle) * 9
            );

            world.spawn(spawnAt, TNTPrimed.class, tnt -> {
                tnt.setSource(player);
                tnt.setFuseTicks(50);
                tnt.setYield(5.0f);
                tnt.setIsIncendiary(false);
            });
        }

        // Inner ring of TNT.
        for (int i = 0; i < 12; i++) {
            double angle = 2.0 * Math.PI * i / 12.0;

            Location spawnAt = target.clone().add(
                    Math.cos(angle) * 4,
                    12,
                    Math.sin(angle) * 4
            );

            world.spawn(spawnAt, TNTPrimed.class, tnt -> {
                tnt.setSource(player);
                tnt.setFuseTicks(55);
                tnt.setYield(5.0f);
                tnt.setIsIncendiary(false);
            });
        }
    }

    // --------------------------------------------------
    // PLAYER PROTECTION
    // --------------------------------------------------

    private void protectWielder(Player player, long durationMillis) {
        protectedUntil.put(
                player.getUniqueId(),
                System.currentTimeMillis() + durationMillis
        );
    }

    @EventHandler
    public void protectFromOwnBlast(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        Long until = protectedUntil.get(player.getUniqueId());

        if (until == null) return;

        if (System.currentTimeMillis() > until) {
            protectedUntil.remove(player.getUniqueId());
            return;
        }

        EntityDamageEvent.DamageCause cause = event.getCause();

        if (cause == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION
                || cause == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION) {
            event.setCancelled(true);
        }
    }

    // --------------------------------------------------
    // PERMANENT WIELDER EFFECTS
    // --------------------------------------------------

    private void refreshWielderEffects() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            boolean holding =
                    isHeartcrusher(
                            player.getInventory().getItemInMainHand()
                    )
                    || isHeartcrusher(
                            player.getInventory().getItemInOffHand()
                    );

            if (!holding) continue;

            player.addPotionEffect(new PotionEffect(
                    PotionEffectType.STRENGTH,
                    40, 0, false, false, false
            ), true);

            player.addPotionEffect(new PotionEffect(
                    PotionEffectType.FIRE_RESISTANCE,
                    40, 1, false, false, false
            ), true);

            player.addPotionEffect(new PotionEffect(
                    PotionEffectType.SPEED,
                    40, 0, false, false, false
            ), true);
        }
    }

    // --------------------------------------------------
    // COOLDOWNS
    // --------------------------------------------------

    private boolean isOnCooldown(
            Player player,
            Map<UUID, Long> cooldowns
    ) {
        Long end = cooldowns.get(player.getUniqueId());

        return end != null && end > System.currentTimeMillis();
    }

    private void startCooldown(
            Player player,
            Map<UUID, Long> cooldowns
    ) {
        cooldowns.put(
                player.getUniqueId(),
                System.currentTimeMillis() + COOLDOWN_MS
        );
    }

    private void sendCooldownMessage(
            Player player,
            Map<UUID, Long> cooldowns
    ) {
        long remaining = Math.max(
                1L,
                (cooldowns.get(player.getUniqueId())
                        - System.currentTimeMillis() + 999L) / 1000L
        );

        player.sendMessage(
                ChatColor.RED + "Ability cooldown: " + remaining + "s"
        );
    }
}
