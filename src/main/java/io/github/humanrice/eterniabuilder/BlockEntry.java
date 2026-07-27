package io.github.humanrice.eterniabuilder;

import org.bukkit.Axis;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.*;
import org.bukkit.block.data.type.*;

import java.util.Locale;

// all block info storage
@SuppressWarnings("unused")
public final class BlockEntry {
    private String block;
    private int x, y, z;

    private Boolean open, inWall, waterlogged, powered, up, hanging;
    private Boolean fence_facing_NORTH, fence_facing_EAST, fence_facing_SOUTH, fence_facing_WEST;
    private String facing, half, shape, face, type, hinge, axis, chest_half;
    private String wall_facing_NORTH, wall_facing_EAST, wall_facing_SOUTH, wall_facing_WEST;

    public int getX() { return x; }
    public int getY() { return y; }
    public int getZ() { return z; }
    public String getBlock() {return block;}

    // Placeholder is the block at 0,0,0 (barrel or unplaced NPC)
    boolean isPlaceholder() {
        return x == 0 && y == 0 && z == 0;
    }
    boolean isNPC() { return block.equals("NPC"); }

    // Rotate the given block based on the face
    void rotate(BlockFace buildFacing) {
        if (facing != null) facing = Rotation.face(BlockFace.valueOf(facing), buildFacing).name();
        if (shape != null) {
            try { shape = Rotation.rail(Rail.Shape.valueOf(shape), buildFacing).name(); }
            catch (IllegalArgumentException ignored) {}
        }
        rotateFenceFaces(buildFacing);
        rotateWallFaces(buildFacing);
    }

    // Place the block in the world
    void place(Location location) {
        if (block == null) return;

        Material material = Material.matchMaterial(block.toUpperCase(Locale.ROOT));
        if (material == null) return;

        var b = location.getBlock();
        b.setType(material, false);

        BlockData data = b.getBlockData();
        apply(data);
        b.setBlockData(data, false);

        // Phantom Block
        Block block = location.getBlock();

    }

    // Apply specific data to the block
    private void apply(BlockData data) {
        if (data instanceof Directional d && facing != null) d.setFacing(BlockFace.valueOf(facing));
        if (data instanceof Bisected b && half != null) b.setHalf(Bisected.Half.valueOf(half));
        if (data instanceof Stairs s && shape != null) s.setShape(Stairs.Shape.valueOf(shape));
        if (data instanceof FaceAttachable f && face != null) f.setAttachedFace(FaceAttachable.AttachedFace.valueOf(face));
        if (data instanceof Slab s && type != null) s.setType(Slab.Type.valueOf(type));
        if (data instanceof Openable o && open != null) o.setOpen(open);
        if (data instanceof Gate g && inWall != null) g.setInWall(inWall);
        if (data instanceof Door d && hinge != null) d.setHinge(Door.Hinge.valueOf(hinge));
        if (data instanceof Hangable h && hanging != null) h.setHanging(hanging);
        if (data instanceof Powerable p && powered != null) p.setPowered(powered);
        if (data instanceof Waterlogged w && waterlogged != null) w.setWaterlogged(waterlogged);
        if (data instanceof Orientable o && axis != null) o.setAxis(Axis.valueOf(axis));
        if (data instanceof Rail r && shape != null) r.setShape(Rail.Shape.valueOf(shape));
        if (data instanceof Chest c && chest_half != null) c.setType(Chest.Type.valueOf(chest_half));

        if (data instanceof Wall wall) {
            if (up != null) wall.setUp(up);
            setWall(wall, BlockFace.NORTH, wall_facing_NORTH);
            setWall(wall, BlockFace.EAST, wall_facing_EAST);
            setWall(wall, BlockFace.SOUTH, wall_facing_SOUTH);
            setWall(wall, BlockFace.WEST, wall_facing_WEST);
        }

        if (data instanceof MultipleFacing multi) {
            setFace(multi, BlockFace.NORTH, fence_facing_NORTH);
            setFace(multi, BlockFace.EAST, fence_facing_EAST);
            setFace(multi, BlockFace.SOUTH, fence_facing_SOUTH);
            setFace(multi, BlockFace.WEST, fence_facing_WEST);
        }
    }
    private void setWall(Wall wall, BlockFace face, String height) {
        if (height != null) wall.setHeight(face, Wall.Height.valueOf(height));
    }
    private void setFace(MultipleFacing multi, BlockFace face, Boolean enabled) {
        if (enabled != null) multi.setFace(face, enabled);
    }

    // Figure out how the changes are made on rotations for fences and walls
    private void rotateFenceFaces(BlockFace buildFacing) {
        if (buildFacing == BlockFace.SOUTH) return;
        Boolean n = fence_facing_NORTH, e = fence_facing_EAST, s = fence_facing_SOUTH, w = fence_facing_WEST;
        switch (buildFacing) {
            case NORTH -> { fence_facing_NORTH = s; fence_facing_EAST = w; fence_facing_SOUTH = n; fence_facing_WEST = e; }
            case EAST -> { fence_facing_NORTH = e; fence_facing_EAST = s; fence_facing_SOUTH = w; fence_facing_WEST = n; }
            case WEST -> { fence_facing_NORTH = w; fence_facing_EAST = n; fence_facing_SOUTH = e; fence_facing_WEST = s; }
            default -> {}
        }
    }
    private void rotateWallFaces(BlockFace buildFacing) {
        if (buildFacing == BlockFace.SOUTH) return;
        String n = wall_facing_NORTH, e = wall_facing_EAST, s = wall_facing_SOUTH, w = wall_facing_WEST;
        switch (buildFacing) {
            case NORTH -> { wall_facing_NORTH = s; wall_facing_EAST = w; wall_facing_SOUTH = n; wall_facing_WEST = e; }
            case EAST -> { wall_facing_NORTH = e; wall_facing_EAST = s; wall_facing_SOUTH = w; wall_facing_WEST = n; }
            case WEST -> { wall_facing_NORTH = w; wall_facing_EAST = n; wall_facing_SOUTH = e; wall_facing_WEST = s; }
            default -> {}
        }
    }
}
