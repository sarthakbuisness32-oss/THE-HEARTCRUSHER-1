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
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
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
    private final Map<UUID, Long> protectedUntil = new HashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        itemKey = new NamespacedKey(this, "heartcrusher");
        Bukkit.getPluginManager().registerEvents(this, this);
        registerRecipe();

        if (getCommand("heartcrusher") != null) {
            getCommand("heartcrusher").setExecutor(this);
        }

        // Refresh the mace's effects while it is held in either hand.
        Bukkit.getScheduler().runTaskTimer(this, this::refreshWielderEffects, 1L, 20L);
        getLogger().info("THE HEARTCRUSHER has awakened.");
    }

    @Override
    public void onDisable() {
        getLogger().info("THE HEARTCRUSHER has been disabled.");
    }

    private ItemStack createHeartcrusher() {
        ItemStack mace = new ItemStack(Material.MACE);
        ItemMeta meta = mace.getItemMeta();

        meta.setDisplayName(ChatColor.DARK_RED + "" + ChatColor.BOLD + "THE HEARTCRUSHER");

        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "A relic forged for a legend.");
        lore.add(ChatColor.DARK_RED + "Shift + Right Click " + ChatColor.WHITE + "- Divine Piercer");
        lore.add(ChatColor.DARK_RED + "Shift + Left Click " + ChatColor.WHITE + "- Heaven's Wrath");
        lore.add(ChatColor.GRAY + "Each ability: 8 second cooldown");
        meta.setLore(lore);
        meta.setUnbreakable(true);
        meta.getPersistentDataContainer().set(itemKey, PersistentDataType.BYTE, (byte) 1);

        meta.addEnchant(Enchantment.DENSITY, 6, true);
        meta.addEnchant(Enchantment.BREACH, 6, true);
        meta.addEnchant(Enchantment.WIND_BURST, 2, true);
        meta.addEnchant(Enchantment.FIRE_ASPECT, 5, true);

        mace.setItemMeta(meta);
        return mace;
    }

    private boolean isHeartcrusher(ItemStack item) {
        if (item == null || item.getType() != Material.MACE || !item.hasItemMeta()) {
            return false;
        }
        Byte marker = item.getItemMeta().getPersistentDataContainer()
                .get(itemKey, PersistentDataType.BYTE);
        return marker != null && marker == (byte) 1;
    }

    private void registerRecipe() {
        NamespacedKey recipeKey = new NamespacedKey(this, "heartcrusher_recipe");
        Bukkit.removeRecipe(recipeKey);

        ShapedRecipe recipe = new ShapedRecipe(recipeKey, createHeartcrusher());
        recipe.shape(" A ", "BCB", " D ");
        recipe.setIngredient('A', Material.NETHER_STAR);
        recipe.setIngredient('B', Material.NETHERITE_INGOT);
        recipe.setIngredient('C', Material.HEAVY_CORE);
        recipe.setIngredient('D', Material.BREEZE_ROD);
        Bukkit.addRecipe(recipe);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length != 2 || !args[0].equalsIgnoreCase("give")) {
            sender.sendMessage(ChatColor.RED + "Usage: /heartcrusher give <player>");
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "That player is not online.");
            return true;
        }

        target.getInventory().addItem(createHeartcrusher());
        sender.sendMessage(ChatColor.GREEN + "Gave THE HEARTCRUSHER to " + target.getName() + ".");
        target.sendMessage(ChatColor.DARK_RED + "" + ChatColor.BOLD + "THE HEARTCRUSHER "
                + ChatColor.GRAY + "has chosen you.");
        return true;
    }

    @EventHandler
    public void onUse(PlayerInteractEvent event) {
        Player player = event.getPlayer();

        // Ignore the duplicate off-hand interaction event.
        if (event.getHand() != EquipmentSlot.HAND || !player.isSneaking()
                || !isHeartcrusher(event.getItem())) {
            return;
        }

        Action action = event.getAction();
        if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
            event.setCancelled(true);
            if (isOnCooldown(player, piercerCooldowns)) {
                sendCooldownMessage(player, piercerCooldowns);
                return;
            }
            startCooldown(player, piercerCooldowns);
            divinePiercer(player);
        } else if (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK) {
            event.setCancelled(true);
            if (isOnCooldown(player, wrathCooldowns)) {
                sendCooldownMessage(player, wrathCooldowns);
                return;
            }
            startCooldown(player, wrathCooldowns);
            heavensWrath(player);
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
            return new Location(player.getWorld(), hit.getX(), hit.getY(), hit.getZ());
        }

        return player.getEyeLocation().add(player.getEyeLocation().getDirection().multiply(24));
    }

    private void divinePiercer(Player player) {
        Location target = getAimLocation(player);
        World world = target.getWorld();
        if (world == null) return;

        int x = target.getBlockX();
        int z = target.getBlockZ();
        int startY = world.getMinHeight();
        int endY = world.getMaxHeight() - 1;
        int step = Math.max(2, getConfig().getInt("column-step", 8));
        long interval = Math.max(1L, getConfig().getLong("column-interval-ticks", 1L));
        float power = (float) getConfig().getDouble("column-explosion-power", 2.5);

        protectWielder(player, 10000L);
        player.sendMessage(ChatColor.DARK_RED + "DIVINE PIERCER!");
        player.playSound(player.getLocation(), Sound.ENTITY_WITHER_SHOOT, 1.0f, 0.7f);

        final int[] y = {startY};
        Bukkit.getScheduler().runTaskTimer(this, task -> {
            if (!player.isOnline() || y[0] > endY) {
                task.cancel();
                return;
            }

            Location blast = new Location(world, x + 0.5, y[0] + 0.5, z + 0.5);
            world.spawnParticle(Particle.FLAME, blast, 12, 0.35, 0.35, 0.35, 0.02);
            world.spawnParticle(Particle.SMOKE, blast, 8, 0.3, 0.3, 0.3, 0.01);
            world.createExplosion(blast, power, false, true, player);
            y[0] += step;
        }, 0L, interval);
    }

    private void heavensWrath(Player player) {
        Location target = getAimLocation(player);
        World world = target.getWorld();
        if (world == null) return;

        int rings = Math.max(1, getConfig().getInt("sky-rings", 8));
        int perRing = Math.max(4, getConfig().getInt("tnt-per-ring", 8));
        int skyHeight = Math.max(8, getConfig().getInt("sky-height", 18));
        int fuse = Math.max(20, getConfig().getInt("sky-tnt-fuse-ticks", 50));
        float yield = (float) getConfig().getDouble("sky-tnt-yield", 4.0);

        double centerX = target.getBlockX() + 0.5;
        double centerZ = target.getBlockZ() + 0.5;
        double baseY = Math.min(world.getMaxHeight() - 2, target.getY() + skyHeight);

        protectWielder(player, 10000L);
        player.sendMessage(ChatColor.DARK_RED + "HEAVEN'S WRATH!");
        player.playSound(player.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.0f, 0.8f);

        for (int ring = 0; ring < rings; ring++) {
            double radius = 2.5 + ring * 1.5;
            for (int i = 0; i < perRing; i++) {
                double angle = (2.0 * Math.PI * i) / perRing;
                double spawnX = centerX + Math.cos(angle) * radius;
                double spawnZ = centerZ + Math.sin(angle) * radius;

                Location spawnAt = new Location(world, spawnX, baseY, spawnZ);
                int ringFuse = fuse + ring * 2;
                world.spawn(spawnAt, TNTPrimed.class, tnt -> {
                    tnt.setSource(player);
                    tnt.setFuseTicks(ringFuse);
                    tnt.setYield(yield);
                    tnt.setIsIncendiary(false);
                });
            }
        }
    }

    private void protectWielder(Player player, long durationMillis) {
        protectedUntil.put(player.getUniqueId(), System.currentTimeMillis() + durationMillis);
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

    private void refreshWielderEffects() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            boolean holding = isHeartcrusher(player.getInventory().getItemInMainHand())
                    || isHeartcrusher(player.getInventory().getItemInOffHand());

            if (!holding) continue;

            player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 40, 0, false, false, false), true);
            player.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 40, 1, false, false, false), true);
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 40, 0, false, false, false), true);
        }
    }

    private boolean isOnCooldown(Player player, Map<UUID, Long> cooldowns) {
        Long end = cooldowns.get(player.getUniqueId());
        return end != null && end > System.currentTimeMillis();
    }

    private void startCooldown(Player player, Map<UUID, Long> cooldowns) {
        long seconds = Math.max(1L, getConfig().getLong("cooldown-seconds", 8L));
        cooldowns.put(player.getUniqueId(), System.currentTimeMillis() + seconds * 1000L);
    }

    private void sendCooldownMessage(Player player, Map<UUID, Long> cooldowns) {
        long remaining = Math.max(1L,
                (cooldowns.get(player.getUniqueId()) - System.currentTimeMillis() + 999L) / 1000L);
        player.sendMessage(ChatColor.RED + "Ability cooldown: " + remaining + "s");
    }
}
