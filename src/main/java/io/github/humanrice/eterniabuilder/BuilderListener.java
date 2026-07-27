    package io.github.humanrice.eterniabuilder;
    
    import io.lumine.mythic.api.mobs.MythicMob;
    import io.lumine.mythic.bukkit.BukkitAdapter;
    import io.lumine.mythic.bukkit.MythicBukkit;
    import io.lumine.mythic.core.mobs.ActiveMob;
    import net.kyori.adventure.text.Component;
    import net.kyori.adventure.text.format.NamedTextColor;
    import org.bukkit.*;
    import org.bukkit.block.*;
    import org.bukkit.block.data.BlockData;
    import org.bukkit.enchantments.Enchantment;
    import org.bukkit.entity.*;
    import org.bukkit.event.EventHandler;
    import org.bukkit.event.Listener;
    import org.bukkit.event.block.*;
    import org.bukkit.event.entity.EntitySpawnEvent;
    import org.bukkit.event.inventory.PrepareGrindstoneEvent;
    import org.bukkit.event.player.PlayerInteractEvent;
    import org.bukkit.event.player.PlayerItemHeldEvent;
    import org.bukkit.event.player.PlayerQuitEvent;
    import org.bukkit.inventory.ItemFlag;
    import org.bukkit.inventory.ItemStack;
    import org.bukkit.inventory.meta.ItemMeta;
    import org.bukkit.persistence.PersistentDataType;

    import java.nio.charset.StandardCharsets;
    import java.nio.file.Path;
    import java.security.MessageDigest;
    import java.util.*;
    
    public final class BuilderListener implements Listener {
    
        // The builder block is a barrel
        private static final EnumSet<Material> BUILDER_BLOCKS = EnumSet.of(Material.BARREL);
    
        // Builds can build over all blocks in this set
        private static final EnumSet<Material> REPLACEABLE_BLOCKS = EnumSet.of(
                Material.AIR, Material.SHORT_GRASS, Material.TALL_GRASS, Material.SNOW, Material.GREEN_STAINED_GLASS
        );
    
        // Variables
        private final EterniaBuilder plugin;
        private final NamespacedKey typeKey;
        private final NamespacedKey wandKey;
        private final NamespacedKey builtKey;
        private final NamespacedKey lockKey;
    
        // List of all builds that are in preview and that are currently being built (for cleanup)
        private final Map<UUID, BuildSession> previews = new HashMap<>();
        private final Map<UUID, BuildSession> builds = new HashMap<>();
    
        // Class Init
        public BuilderListener(EterniaBuilder plugin) {
            this.plugin = plugin;
            this.typeKey = new NamespacedKey(plugin, "builder-type");
            this.wandKey = new NamespacedKey(plugin, "builder-wand");
            this.builtKey = new NamespacedKey(plugin, "built");
            this.lockKey = new NamespacedKey(plugin, "lock-code");
        }
    
        /* ----------------------------------- LISTENER FUNCTIONS --------------------------------------------- */
        // Check Barrel opens
        @EventHandler
        public void onUse(PlayerInteractEvent event) {
            // Get the Clicked Block, Item, and Player
            Block barrel = event.getClickedBlock();
            ItemStack item = event.getItem();
            Player player = event.getPlayer();
    
            // If they didn't click anything, they aren't holding anything or the barrel/item are not a builder/wand ignore
            if (barrel == null || event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
    
            // Prevent opening builder barrels
            if (BUILDER_BLOCKS.contains(barrel.getType()) && buildType(barrel).isPresent() && (item == null || !isBuilderClick(barrel, item))) {
                event.setCancelled(true);
                player.sendMessage(ChatColor.RED + "This is a builder block, you can not use it as a barrel.");
            }
    
            if (item == null || !isBuilderClick(barrel, item)) return;
            event.setCancelled(true);
    
            // Check the build type of the builder block
            Optional<String> buildType = buildType(barrel);
            if (buildType.isEmpty()) {
                player.sendMessage(ChatColor.RED + "This barrel has no build type.");
                return;
            }
    
            // Try to load the build
            List<BlockEntry> entries = loadBuild(buildType.get());
            if (entries.isEmpty()) {
                player.sendMessage(ChatColor.RED + "No valid build data found for " + buildType.get() + ".");
                return;
            }
    
            // if the player has the lock pickup the block
            UUID id = player.getUniqueId();
            if (isMatchingLockPaper(barrel, item)) {
    
                if (isBarrelBuilding(barrel)) {
                    player.sendMessage("§cYou cannot pick this up while it is building!");
                    return;
                }
    
                pickupBuild(player, barrel, item, buildType.get());
                return;
            }
    
            // Do not try previewing if the item is already connected to a build
            if (hasLockCode(item)) return;

            // Do not try previewing if the barrel is building
            if (isBarrelBuilding(barrel)){
                player.sendMessage("§cThis build is being built!");
                return;
            }

            // Do not try previewing if the barrel is built
            if (!(barrel.getState() instanceof TileState state)) return;
            boolean built = Boolean.TRUE.equals(state.getPersistentDataContainer().get(builtKey, PersistentDataType.BOOLEAN));
            if (built){
                player.sendMessage("§cThis build is already built!");
                return;
            }
    
            // If there is no preview create a preview
            BuildSession preview = previews.remove(id);
            if (preview == null) {
                previews.put(id, preview(player, barrel, entries));
                return;
            }
    
            // If there are blocking blocks inform the player
            if (!canBuild(preview, barrel, entries)) {
                player.sendMessage(ChatColor.RED + "Build cancelled: Please clear all blocks in the build area.");
                return;
            }
    
            // Build the structure if everything goes through
            startBuild(player, barrel, entries, preview, buildType.get());
        }
    
        // Set the placed barrel to have the same info as the components it had
        @EventHandler
        public void onPlace(BlockPlaceEvent event) {
            ItemStack item = event.getItemInHand();
            if (!item.hasItemMeta()) return;

            String type = item.getItemMeta().getPersistentDataContainer().get(typeKey, PersistentDataType.STRING);
            if (type == null || !(event.getBlockPlaced().getState() instanceof TileState state)) return;

            state.getPersistentDataContainer().set(typeKey, PersistentDataType.STRING, type);
            state.update();
        }
    
        // If the build is finished cancel all break events for the barrel
        @EventHandler
        public void onBreak(BlockBreakEvent event) {
            Block block = event.getBlock();
            long packed = pack(block.getX(), block.getY(), block.getZ());
    
            // If the block is one that was built by the builder, do not drop it
            if (plugin.removeBuilderBlock(packed)) {
                event.setDropItems(false);
                saveToConfig();
                return;
            }
    
            if (!(block.getState() instanceof TileState state)) return;
    
            String type = state.getPersistentDataContainer().get(typeKey, PersistentDataType.STRING);
            boolean built = Boolean.TRUE.equals(state.getPersistentDataContainer().get(builtKey, PersistentDataType.BOOLEAN));
    
            // If the block is built fully cancel the event
            if (built) {
                event.setCancelled(true);
                event.getPlayer().sendMessage("§cYou cannot break a finished build.");
                return;
            }
    
            // If the block is building cancel the event
            if (isBarrelBuilding(block)) {
                event.setCancelled(true);
                event.getPlayer().sendMessage("§cYou cannot break a building build.");
                return;
            }
    
            // If it's a builder block have it drop the enchanted version
            if (type != null) {
                event.setCancelled(true);
                event.setDropItems(false);
    
                Bukkit.getScheduler().runTask(plugin, () -> {
                    block.setType(Material.AIR, false);
                    block.getWorld().dropItemNaturally(
                            block.getLocation(),
                            createBuilderBarrel(type)
                    );
                });
            }
        }

        // If the build is finished cancel all push and pull events that would push the blocks built by the plugin
        @EventHandler
        public void onPistonExtend(BlockPistonExtendEvent event) {
            for (Block block : event.getBlocks()) {
                long packed = pack(block.getX(), block.getY(), block.getZ());
                if (isTrackedBuildBlock(packed)) {
                    event.setCancelled(true);
                    return;
                }
            }
        }
        @EventHandler
        public void onPistonRetract(BlockPistonRetractEvent event) {
            for (Block block : event.getBlocks()) {
                long packed = pack(block.getX(), block.getY(), block.getZ());
                if (isTrackedBuildBlock(packed)) {
                    event.setCancelled(true);
                    return;
                }
            }
        }

        // If the block is supposed to fall, cancel it (NOT WORKING)
        @EventHandler(ignoreCancelled = true)
        public void onPhysics(BlockPhysicsEvent event) {
            Block block = event.getBlock();

            long packed = pack(block.getX(), block.getY(), block.getZ());
            if (isTrackedBuildBlock(packed)) {
                event.setCancelled(true);
            }
        }

        // If the block is supposed to fall, cancel it
        @EventHandler(ignoreCancelled = true)
        public void onFallingBlockSpawn(EntitySpawnEvent event) {
            if (!(event.getEntity() instanceof FallingBlock fallingBlock)) return;

            Block block = event.getLocation().getBlock();
            long packed = pack(block.getX(), block.getY(), block.getZ());

            if (isTrackedBuildBlock(packed)) {
                event.setCancelled(true);
                // The game already cleared this block to air before spawning the entity — put it back
                block.setBlockData(fallingBlock.getBlockData(), false);
            }
        }
    
        // Cancel the preview if they change their item in hand cancel their preview
        @EventHandler
        public void onHeldItemChange(PlayerItemHeldEvent event) {
            cancelPreview(event.getPlayer(), ChatColor.RED + "Confirmation cancelled - changed item in hand.");
        }
    
        // If they leave cancel the preview
        @EventHandler
        public void onQuit(PlayerQuitEvent event) {
            cancelPreview(event.getPlayer(), null);
        }
    
        // Disable grindstoning the custom items
        @EventHandler
        public void onGrindstone(PrepareGrindstoneEvent event) {
            ItemStack top = event.getInventory().getUpperItem();
            ItemStack bottom = event.getInventory().getLowerItem();
    
            if (isProtected(top) || isProtected(bottom)) {
                event.setResult(null);
            }
        }
        /* ----------------------------------- LISTENER FUNCTIONS --------------------------------------------- */
        /* ------------------------------------ HELPER FUNCTIONS ---------------------------------------------- */

        // Get if the block in question is one created by the plugin
        private boolean isTrackedBuildBlock(long packed) {
            return plugin.allTrackedBlocks.contains(packed);
        }

        // Create the builder barrel as an item (for dropping when breaking the item
        private ItemStack createBuilderBarrel(String buildType) {
            ItemStack barrel = new ItemStack(Material.BARREL);
            ItemMeta barrelMeta = barrel.getItemMeta();
            if (barrelMeta != null) {
                // Set the barrel to build key
                barrelMeta.getPersistentDataContainer().set(
                        typeKey,
                        PersistentDataType.STRING,
                        buildType
                );
    
                String displayName = formatBuildName(buildType);
                barrelMeta.setDisplayName("§a" + displayName + " Builder");
                barrelMeta.addEnchant(Enchantment.UNBREAKING, 1, true);
                barrelMeta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
    
                barrel.setItemMeta(barrelMeta);
            }
    
            return barrel;
        }
    
        // Figure out if the item in inventory is custom (to our plugin only)
        private boolean isProtected(ItemStack item) {
            if (item == null || !item.hasItemMeta()) return false;
    
            var pdc = item.getItemMeta().getPersistentDataContainer();
    
            return pdc.has(wandKey, PersistentDataType.BOOLEAN)
                    || pdc.has(lockKey, PersistentDataType.STRING)
                    || pdc.has(typeKey, PersistentDataType.STRING);
        }
    
        // Build and preview cleanup
        public void cleanup() {
            previews.values().forEach(BuildSession::restore);
            previews.clear();
            builds.values().forEach(BuildSession::removeTimer);
            builds.clear();
        }
    
        // Check if the blocks and items have the correct keys
        private boolean isBuilderClick(Block block, ItemStack item) {
            if (!BUILDER_BLOCKS.contains(block.getType())) return false;
            if (item.getType() != Material.PAPER || !item.hasItemMeta()) return false;
    
            var pdc = item.getItemMeta().getPersistentDataContainer();
    
            return pdc.has(wandKey, PersistentDataType.BOOLEAN) || pdc.has(lockKey, PersistentDataType.STRING);
        }
    
        // Get the build type
        private Optional<String> buildType(Block block) {
            if (!(block.getState() instanceof TileState state)) return Optional.empty();
            return Optional.ofNullable(state.getPersistentDataContainer().get(typeKey, PersistentDataType.STRING));
        }
    
        // Load the json file from the key name
        private List<BlockEntry> loadBuild(String type) {
            Path path = plugin.getDataFolder().toPath().resolve("builds").resolve(type.toLowerCase(Locale.ROOT) + ".json");
            return BuildLoader.load(path);
        }
    
        // Create and start building the previews
        private BuildSession preview(Player player, Block barrel, List<BlockEntry> entries) {
            BuildSession session = new BuildSession(barrel.getLocation(), player.getFacing());
            player.sendMessage(ChatColor.GREEN + "Preview spawned. Click again to confirm, or switch items to cancel.");
    
            // For each entry if it's still a barrel block(fake) ignore it, if not set the blocks to green or red
            for (int i = 0; i < entries.size(); i += 3) {
                BlockEntry entry = entries.get(i);
                if (entry.isPlaceholder() || entry.isNPC()) continue;
    
                Block target = relativeBlock(barrel, entry, session.facing);
                if (target.equals(barrel) || target.getState() instanceof Container) continue;
    
                session.remember(target);
                target.setType(REPLACEABLE_BLOCKS.contains(target.getType()) ? Material.GREEN_STAINED_GLASS : Material.RED_STAINED_GLASS, false);
            }
            return session;
        }
    
        // Check if you can build the preview
        private boolean canBuild(BuildSession preview, Block barrel, List<BlockEntry> entries) {
            // Get the cube area of the build
            preview.restore();

            for (BlockEntry entry: entries) {
                if (entry.isPlaceholder() || entry.isNPC()) continue;
                if ("AIR".equalsIgnoreCase((entry.getBlock())) || "BARRIER".equalsIgnoreCase(entry.getBlock()))
                    continue;

                Block block = relativeBlock(barrel, entry, preview.facing);
                if (!block.equals(barrel) && !REPLACEABLE_BLOCKS.contains(block.getType())) return false;
            }
            return true; // The build is fine to be built
        }
    
        // Check if the barrel is already building
        private boolean isBarrelBuilding(Block barrel) {
            Location loc = barrel.getLocation();
    
            for (BuildSession session : builds.values()) {
                Location origin = session.origin;
    
                if (origin.getWorld().equals(loc.getWorld())
                        && origin.getBlockX() == loc.getBlockX()
                        && origin.getBlockY() == loc.getBlockY()
                        && origin.getBlockZ() == loc.getBlockZ()) {
                    return true;
                }
            }
    
            return false;
        }
    
        // Pack a set of coordinates into a long (for smaller storage space)
        public static long pack(int x, int y, int z) {
            return ((long) x & 0x3FFFFFFL) << 38
                    | ((long) z & 0x3FFFFFFL) << 12
                    | ((long) y & 0xFFFL);
        }
    
        // Unpack a long into a set of coordinates
        public static int[] unpack(long packed) {
            int x = (int) (packed >> 38);
            int y = (int) (packed << 52 >> 52); // sign-extend 12 bits
            int z = (int) (packed << 26 >> 38); // sign-extend 26 bits
    
            return new int[]{x, y, z};
        }
    
        // Start building the preview
        private void startBuild(Player player, Block barrel, List<BlockEntry> entries, BuildSession preview, String buildType) {
            UUID id = player.getUniqueId(); // Get the players id (for the builds list)
            BuildSession session = new BuildSession(barrel.getLocation(), preview.facing); // Create a build session
            builds.put(id, session);
    
            // Create the build timer and apply the lock to the barrel
            session.timer = spawnTimer(barrel, entries);
            applyLock(barrel, player, buildType);
    
            // Get the length of the build and figure out the delay on build per block
            int totalTicks = buildMinutes(barrel) * 60 * 20;
            int delayPerBlock = Math.max(1, totalTicks / Math.max(1, entries.size()));
    
            // For each block (Ignoring the last, as the last represents the NPC
            for (int i = 0; i < entries.size() - 1; i++) {
                int index = i;
                BlockEntry entry = entries.get(i); // Get the block info
    
                // Set all delayed block places
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    // Update the timer
                    updateTimer(session.timer, totalTicks - (index * delayPerBlock));
    
                    // If the entry is good, rotate and place the block
                    if (!entry.isPlaceholder()) {
                        entry.rotate(session.facing);
                        Block target = relativeBlock(barrel, entry, session.facing);

                        Location placeLoc = target.getLocation();
                        long packed = pack(placeLoc.getBlockX(), placeLoc.getBlockY(), placeLoc.getBlockZ());

                        // If the block is a barrier save it's previous block so we can replace it when we set the block to air
                        if ("BARRIER".equalsIgnoreCase(entry.getBlock())){
                            session.saveUnderBarrier(packed, target.getBlockData().clone());
                            entry.place(placeLoc);
                        }

                        // If the block is air set it to what we saved when we set it to a barrier
                        else if ("AIR".equalsIgnoreCase(entry.getBlock())) {
                            BlockData saved = session.takeSavedUnderBarrier(packed);
                            if (saved != null) {
                                target.setBlockData(saved, false);
                            } else {
                                entry.place(placeLoc);
                            }
                        }

                        // If not treat it like normal
                        else {
                            entry.place(placeLoc);
                        }

                        String key = lockName(barrel);
                        if (!"AIR".equalsIgnoreCase(entry.getBlock()) && !"BARRIER".equalsIgnoreCase(entry.getBlock())) {
                            plugin.addBuilderBlock(key, packed);
                        }
                    }
    
                    // If it is the block is the second last entry finish the build (last is reserved for the NPC)
                    if (index == entries.size() - 2) {
                        finishBuild(id, barrel, session, buildType, entries);
                        saveToConfig();
                    }
                }, (long) index * delayPerBlock);
            }
        }
    
        // Create the timer display
        private TextDisplay spawnTimer(Block barrel, List<BlockEntry> entries) {
            int maxY = entries.stream().mapToInt(BlockEntry::getY).max().orElse(0);
            Location location = barrel.getLocation().add(0, maxY + 1, 0);
            TextDisplay timer = barrel.getWorld().spawn(location, TextDisplay.class);
            timer.setBillboard(Display.Billboard.CENTER);
            timer.setAlignment(TextDisplay.TextAlignment.CENTER);
            timer.setSeeThrough(true);
            timer.setLineWidth(100);
            return timer;
        }
    
        // Update the timer
        private void updateTimer(TextDisplay timer, int ticksLeft) {
            if (timer == null || timer.isDead()) return;
            int seconds = Math.max(0, ticksLeft / 20);
            timer.text(Component.text(String.format("%d:%02d", seconds / 60, seconds % 60), NamedTextColor.GREEN));
        }
    
        // Clean up everything once a build is finished and spawn NPC
        private void finishBuild(UUID player, Block barrel, BuildSession session, String buildType, List<BlockEntry> entries) {
            // Remove the builds from our lists
            Player playerEntity = Bukkit.getPlayer(player);
            builds.remove(player);
            session.removeTimer();
    
            // Set the barrel to have the "built" key
            if (barrel.getState() instanceof TileState state) {
                state.getPersistentDataContainer().set(builtKey, PersistentDataType.BOOLEAN, true);
                state.update();
            }
    
            // Create NPC according to the build Name
            String npcName = formatBuildName(buildType).replaceAll("\\s+", "_");
            MythicMob mob = MythicBukkit.inst().getMobManager().getMythicMob(npcName).orElse(null);
    
            // Get the spawn location for the NPC
            BlockEntry placeholderNPC = entries.getLast();
            if (!placeholderNPC.isPlaceholder()){
    
                // Get Location
                Location relative = new Location(barrel.getWorld(), placeholderNPC.getX(), placeholderNPC.getY(), placeholderNPC.getZ());
                Location spawnLocation = barrel.getLocation().add(Rotation.location(relative, session.facing));
    
                // Get the key of the barrel
                String key = "ERROR";
                if(barrel.getState() instanceof Lockable lockable){
                    key = lockable.getLock().replace("\"", "");
                }
    
                // Spawn NPC
                if(mob != null){
                    NamespacedKey key_name = new NamespacedKey(plugin, "entity-key");
                    ActiveMob npc = mob.spawn(BukkitAdapter.adapt(spawnLocation),1);
                    Entity entity = npc.getEntity().getBukkitEntity();
    
                    // Apply the key to the npc
                    if (!key.equals("ERROR")){
                        entity.getPersistentDataContainer().set(key_name, PersistentDataType.STRING, key);
                    }
                }
                else if (playerEntity != null) playerEntity.sendMessage(ChatColor.RED + "An Error has occurred, please inform the moderators that there is no NPC Named " + ChatColor.GOLD + npcName);
            }
    
        }
    
        // Use the build key to figure out how long the build should take
        private int buildMinutes(Block barrel) {
            return buildType(barrel).map(type -> {
                if (type.contains("3")) return 3;
                if (type.contains("2")) return 2;
                if (type.contains("1")) return 1;
                return 3;
            }).orElse(3);
        }
    
        // Convert from the relative locations in the json to the correct position
        private Block relativeBlock(Block origin, BlockEntry entry, BlockFace facing) {
            Location relative = new Location(origin.getWorld(), entry.getX(), entry.getY(), entry.getZ());
            return origin.getLocation().add(Rotation.location(relative, facing)).getBlock();
        }
    
        // Check if the item has the lock key info
        private boolean hasLockCode(ItemStack item) {
            return item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer().has(lockKey, PersistentDataType.STRING);
        }
    
        // If the paper has a lock check to make sure
        private boolean isMatchingLockPaper(Block barrel, ItemStack item) {
            if (!(barrel.getState() instanceof Lockable lockable) || !hasLockCode(item)) return false;
            String lock = lockable.getLock().replace("\"", "");
            String paperLock = item.getItemMeta().getPersistentDataContainer().get(lockKey, PersistentDataType.STRING);
            return !lock.isEmpty() && lock.equals(paperLock);
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

        // Create Lore for the pickup of the wand
        private List<String> formatBuildLore(int x, int y, int z){
            String x_str = String.valueOf(x);
            String y_str = String.valueOf(y);
            String z_str = String.valueOf(z);

            List<String> lore = new ArrayList<>();
            lore.add("Build can be found at coordinates");
            lore.add(("X: " + x_str + ", Y: " + y_str + ", Z: " + z_str).trim());

            return lore;
        }
    
        // Apply a lock to the barrel and replace the paper with a pickup version
        private void applyLock(Block barrel, Player player, String buildType) {
            String lock = lockName(barrel); // Get the name of the lock
            BlockState state = barrel.getState(); // Get the state of the block
    
            // If the block is lockable (which it should be) lock it
            if (state instanceof Lockable lockable) {
                lockable.setLock(lock);
                state.update();
            }
    
            // Create a paper with the lock key
            ItemStack paper = new ItemStack(Material.PAPER);
            var meta = paper.getItemMeta();
            meta.getPersistentDataContainer().set(lockKey, PersistentDataType.STRING, lock);
    
            String niceName = formatBuildName(buildType);
            meta.setDisplayName("§ePickup: §6" + niceName);
            meta.setLore(formatBuildLore(barrel.getX(), barrel.getY(), barrel.getZ()));
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
    
            paper.setItemMeta(meta);
    
            // Set it into the players hand
            // Consume exactly one builder wand from the stack the player is holding
            ItemStack hand = player.getInventory().getItemInMainHand();
            if (hand.getAmount() > 1) {
                hand.setAmount(hand.getAmount() - 1);
                player.getInventory().setItemInMainHand(hand);

                // Give the lock paper back without touching the rest of the stack
                var leftover = player.getInventory().addItem(paper);
                if (!leftover.isEmpty()) {
                    leftover.values().forEach(i -> player.getWorld().dropItemNaturally(player.getLocation(), i));
                }
            } else {
                // It was the last one in the stack, safe to just replace
                player.getInventory().setItemInMainHand(paper);
            }
        }
    
        // Pickup the build
        private void pickupBuild(Player player, Block barrel, ItemStack paper, String buildType) {
    
            // Find the build area & Remove all blocks associated with the build
            String key = lockName(barrel);
            Set<Long> worldBlocks = plugin.builderBlocks.get(key);
            if (worldBlocks == null || worldBlocks.isEmpty()) return;
    
            for (long packed : new HashSet<>(worldBlocks)) {
    
                // Unpack the Coordinate
                int[] pos = unpack(packed);
    
                // remove the block from the build
                Block block = barrel.getWorld().getBlockAt(pos[0], pos[1], pos[2]);
                if (!block.equals(barrel)) {
                    block.setType(Material.AIR, false);
                }
            }
    
            // Remove all entries in the build
            plugin.removeBuild(key);
            saveToConfig();
    
            // Kill The NPC associated with the build
            NamespacedKey key_name = new NamespacedKey(plugin, "entity-key");
            Collection<ActiveMob> activeMobs = MythicBukkit.inst().getMobManager().getActiveMobs(am -> am.getMobType().equals(formatBuildName(buildType).replaceAll("\\s+", "_")));
            for (ActiveMob mob : activeMobs) {
                Entity npc = mob.getEntity().getBukkitEntity();
    
                // Get the key associated with the npc
                String npcKey = npc.getPersistentDataContainer().get(key_name, PersistentDataType.STRING);
    
                // Get the key associated with the barrel
                String lock = "NOLOCK";
                if (barrel.getState() instanceof Lockable lockable) {
                    lock = lockable.getLock().replace("\"", "");
                }
    
                // Kill the NPC if they have the same lock
                if (npcKey != null && npcKey.equals(lock)) {
                    npc.remove();
                }
            }

            // Set the paper back to a wand key
            ItemStack cleanPaper = paper.clone();
            cleanPaper.editMeta(meta -> {
                meta.setDisplayName("§aBuilder Wand");

                List<String> lore = new ArrayList<>();
                lore.add("Right click this on a barrel block to begin building the block");
                lore.add("This will then become the key to pickup that build");
                meta.setLore(lore);

                meta.getPersistentDataContainer().remove(lockKey);
                meta.getPersistentDataContainer().set(
                        wandKey,
                        PersistentDataType.BOOLEAN,
                        true
                );
            });
            player.getInventory().setItemInMainHand(cleanPaper);
    
            // Get the barrel info and give it to the player (then set it to air)
            ItemStack barrelItem = new ItemStack(Material.BARREL);
            barrelItem.editMeta(meta -> {
                meta.getPersistentDataContainer().set(
                        typeKey,
                        PersistentDataType.STRING,
                        buildType
                );
    
                String niceName = formatBuildName(buildType);
                meta.setDisplayName("§a" + niceName + " Builder");
                meta.addEnchant(Enchantment.UNBREAKING, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            });
            player.getInventory().addItem(barrelItem);
            barrel.setType(Material.AIR, false);
        }
    
        // Canceling preview sets it back to what it used to be
        private void cancelPreview(Player player, String message) {
            BuildSession session = previews.remove(player.getUniqueId());
            if (session == null) return;
            session.restore();
            if (message != null) player.sendMessage(message);
        }
    
        // Save to Config
        private void saveToConfig() {
            plugin.getConfig().set("builds", null);

            for (Map.Entry<String, Set<Long>> entry : plugin.builderBlocks.entrySet()) {
                plugin.getConfig().set(
                        "builds." + entry.getKey(),
                        new ArrayList<>(entry.getValue())
                );
            }

            plugin.saveConfig();
        }
    
        // Lock name generator (uses SHA-256 to make it difficult to break)
        private String lockName(Block block) {
            String raw = block.getWorld().getName() + ':' + block.getX() + ':' + block.getY() + ':' + block.getZ();
            try {
                byte[] bytes = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
                StringBuilder out = new StringBuilder();
                for (byte b : bytes) out.append(String.format("%02x", b));
                return out.toString();
            } catch (Exception ignored) {
                return raw.replace(':', '_');
            }
        }
        /* ------------------------------------ HELPER FUNCTIONS ---------------------------------------------- */
    }
