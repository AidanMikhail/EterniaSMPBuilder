package io.github.humanrice.eterniabuilder;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.TextDisplay;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Class that holds all the info for a given build
final class BuildSession {
    final Location origin;
    final BlockFace facing;
    TextDisplay timer;
    private final List<BlockSnapshot> snapshots = new ArrayList<>();
    private final Map<Long, BlockData> savedUnderBarrier = new HashMap<>();

    // Init
    BuildSession(Location origin, BlockFace facing) {
        this.origin = origin.clone();
        this.facing = facing;
    }

    // Adds the previous build in the position to the snapshots list (for undoing previews)
    void remember(Block block) {
        snapshots.add(new BlockSnapshot(block, block.getBlockData().clone()));
    }

    // Restore all blocks in snapshot
    void restore() {
        snapshots.forEach(BlockSnapshot::restore);
    }

    // Remove the timer from the world
    void removeTimer() {
        if (timer != null && !timer.isDead()) timer.remove();
    }

    // Save what was under a barrier at a packed location, so a later AIR entry can restore it
    void saveUnderBarrier(long packed, BlockData data) {
        savedUnderBarrier.put(packed, data);
    }

    // Take (and remove) the saved block data for a packed location, if any exists
    BlockData takeSavedUnderBarrier(long packed) {
        return savedUnderBarrier.remove(packed);
    }

    // ::restore now sets the block
    private record BlockSnapshot(Block block, BlockData data) {
        void restore() {
            block.setBlockData(data, false);
        }
    }
}
