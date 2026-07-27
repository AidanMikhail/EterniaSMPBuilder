package io.github.humanrice.eterniabuilder;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.BlockState;
import org.bukkit.block.Lockable;
import org.bukkit.block.TileState;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.block.Block;

import java.util.*;

public final class EterniaBuilder extends JavaPlugin {
    private BuilderListener listener;
    public final Map<String, Set<Long>> builderBlocks = new HashMap<>();
    public final Set<Long> allTrackedBlocks = new HashSet<>(); // Set for easy calling

    // Add a block to a build's tracked set (and the flattened lookup set)
    public void addBuilderBlock(String key, long packed) {
        builderBlocks.computeIfAbsent(key, k -> new HashSet<>()).add(packed);
        allTrackedBlocks.add(packed);
    }

    // Remove a single block from a build's tracked set; cleans up the build entry if now empty.
    // Returns true if the block was found in some build.
    public boolean removeBuilderBlock(long packed) {
        boolean found = false;
        Iterator<Map.Entry<String, Set<Long>>> it = builderBlocks.entrySet().iterator();
        while (it.hasNext()) {
            Set<Long> set = it.next().getValue();
            if (set.remove(packed)) {
                found = true;
                if (set.isEmpty()) it.remove();
                break;
            }
        }
        if (found) allTrackedBlocks.remove(packed);
        return found;
    }

    // Remove an entire build (used on pickup)
    public void removeBuild(String key) {
        Set<Long> removed = builderBlocks.remove(key);
        if (removed != null) allTrackedBlocks.removeAll(removed);
    }

    @Override
    public void onEnable() {
        getLogger().info("EterniaBuilder enabled!");

        // Load al positions from yaml file
        ConfigurationSection builds = getConfig().getConfigurationSection("builds");
        if (builds != null) {
            for (String key : builds.getKeys(false)) {
                List<Long> longs = builds.getLongList(key);
                builderBlocks.put(key, new HashSet<>(longs));
                allTrackedBlocks.addAll(longs);
            }
        }

        listener = new BuilderListener(this);
        getServer().getPluginManager().registerEvents(listener, this);

        NamespacedKey typeKey = new NamespacedKey(this, "builder-type");
        NamespacedKey paperKey = new NamespacedKey(this, "builder-wand");
        NamespacedKey lockKey = new NamespacedKey(this, "lock-code");

        getCommand("get_lock").setExecutor((sender, command, label, args) -> {
            if (!(sender instanceof Player player)) return true;

            if (args.length < 3) {
                player.sendMessage("§cUsage: /get_lock <x> <y> <z>");
                return true;
            }

            int x, y, z;

            try {
                x = Integer.parseInt(args[0]);
                y = Integer.parseInt(args[1]);
                z = Integer.parseInt(args[2]);
            } catch (NumberFormatException e) {
                player.sendMessage("§cCoordinates must be numbers.");
                return true;
            }

            Block block = player.getWorld().getBlockAt(x, y, z);

            if (block.getType() != Material.BARREL) {
                player.sendMessage("§cThat block is not a barrel.");
                return true;
            }

            BlockState state = block.getState();

            if (!(state instanceof Lockable lockable)) {
                player.sendMessage("§cThat barrel cannot be locked.");
                return true;
            }
            String lock = lockable.getLock();

            if (!(state instanceof TileState tileState)) {
                player.sendMessage("§cThat barrel cannot store builder data.");
                return true;
            }
            String buildType = tileState.getPersistentDataContainer().get(typeKey, PersistentDataType.STRING);

            if (lock.isEmpty()) {
                player.sendMessage("§cThat barrel has no lock.");
                return true;
            }

            if (buildType == null) {
                player.sendMessage("§cThat barrel has no builder type.");
                return true;
            }

            ItemStack paper = new ItemStack(Material.PAPER);
            paper.editMeta(meta -> {
                meta.getPersistentDataContainer().set(lockKey, PersistentDataType.STRING, lock);
                meta.setDisplayName("§ePickup: §6" + formatBuildName(buildType));
                meta.setLore(formatBuildLore(x, y, z));
                meta.addEnchant(Enchantment.UNBREAKING, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            });

            player.getInventory().addItem(paper);
            player.sendMessage("§aGave pickup paper.");

            return true;
        });

        Objects.requireNonNull(getCommand("get_lock")).setTabCompleter((sender, command, alias, args) -> {
            if (!(sender instanceof Player player)) return List.of();

            Block target = player.getTargetBlockExact(100);
            if (target == null) return List.of();

            return switch (args.length) {
                case 1 -> List.of(String.valueOf(target.getX()));
                case 2 -> List.of(String.valueOf(target.getY()));
                case 3 -> List.of(String.valueOf(target.getZ()));
                default -> List.of();
            };
        });

        Objects.requireNonNull(getCommand("builder")).setExecutor((sender, command, label, args) -> {
            if (!(sender instanceof Player player)) return true;

            if (args.length < 1) {
                player.sendMessage("§cUsage: /builder <type>");
                return true;
            }

            String buildType = args[0].toLowerCase();
            String displayName = formatBuildName(buildType);

            ItemStack barrel = new ItemStack(Material.BARREL);
            barrel.editMeta(meta -> {
                meta.getPersistentDataContainer().set(typeKey, PersistentDataType.STRING, buildType);
                meta.setDisplayName("§a" + displayName + " Builder");
                meta.addEnchant(Enchantment.UNBREAKING, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            });

            ItemStack paper = new ItemStack(Material.PAPER);
            paper.editMeta(meta -> {
                meta.getPersistentDataContainer().set(paperKey, PersistentDataType.BOOLEAN, true);
                meta.setDisplayName("§aBuilder Wand");

                List<String> lore = new ArrayList<>();
                lore.add("Right click this on a barrel block to begin building the block");
                lore.add("This will then become the key to pickup that build");
                meta.setLore(lore);

                meta.addEnchant(Enchantment.UNBREAKING, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            });

            player.getInventory().addItem(barrel);
            player.getInventory().addItem(paper);
            player.sendMessage("§aGave builder barrel & wand!");

            return true;
        });
    }

    @Override
    public void onDisable() {
        listener.cleanup();

        getConfig().set("builds", null);

        for (Map.Entry<String, Set<Long>> entry : builderBlocks.entrySet()) {
            getConfig().set(
                    "builds." + entry.getKey(),
                    new ArrayList<>(entry.getValue())
            );
        }

        saveConfig();
        builderBlocks.clear();

        getLogger().info("EterniaBuilder disabled.");
    }

    // Format the name of the type to be nice
    private String formatBuildName(String raw) {
        if (raw == null || raw.isEmpty()) return "Unknown";

        raw = raw.replace("_", " ").toLowerCase();

        String number = raw.replaceAll("\\D+", "");
        String name = raw.replaceAll("\\d+", "").trim();

        // Capitalize each word
        StringBuilder result = new StringBuilder();
        if (!number.isEmpty()) {
            result.append("Level ").append(number).append(" ");
        }

        for (String word : name.split(" ")) {
            if (!word.isEmpty()) {
                result.append(Character.toUpperCase(word.charAt(0)))
                        .append(word.substring(1))
                        .append(" ");
            }
        }

        return result.toString().trim();
    }

    private List<String> formatBuildLore(int x, int y, int z){
        String x_str = String.valueOf(x);
        String y_str = String.valueOf(y);
        String z_str = String.valueOf(z);

        List<String> lore = new ArrayList<>();
        lore.add("Build can be found at coordinates");
        lore.add(("X: " + x_str + ", Y: " + y_str + ", Z: " + z_str).trim());

        return lore;
    }
}
