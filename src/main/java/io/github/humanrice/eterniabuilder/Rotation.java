package io.github.humanrice.eterniabuilder;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Rail;

final class Rotation {
    private Rotation() {}

    // Edit the relative location of the block based on the given rotation
    static Location location(Location loc, BlockFace facing) {
        World world = loc.getWorld();
        double x = loc.getX(), y = loc.getY(), z = loc.getZ();
        return switch (facing) {
            case WEST -> new Location(world, -z, y, x);
            case NORTH -> new Location(world, -x, y, -z);
            case EAST -> new Location(world, z, y, -x);
            default -> loc.clone();
        };
    }

    // Rotate the block face based on given rotation
    static BlockFace face(BlockFace original, BlockFace facing) {
        if (facing == BlockFace.SOUTH) return original;
        if (facing == BlockFace.NORTH) return original.getOppositeFace();

        BlockFace rotated = switch (original) {
            case NORTH -> BlockFace.WEST;
            case WEST -> BlockFace.SOUTH;
            case SOUTH -> BlockFace.EAST;
            case EAST -> BlockFace.NORTH;
            default -> original;
        };
        return facing == BlockFace.WEST ? rotated.getOppositeFace() : rotated;
    }

    // Change the shape of the rail based on the rotation
    static Rail.Shape rail(Rail.Shape shape, BlockFace facing) {
        if (shape == null || facing == BlockFace.SOUTH) return shape;

        return switch (facing) {
            case NORTH -> switch (shape) {
                case NORTH_EAST -> Rail.Shape.SOUTH_WEST;
                case NORTH_WEST -> Rail.Shape.SOUTH_EAST;
                case SOUTH_EAST -> Rail.Shape.NORTH_WEST;
                case SOUTH_WEST -> Rail.Shape.NORTH_EAST;
                case ASCENDING_NORTH -> Rail.Shape.ASCENDING_SOUTH;
                case ASCENDING_EAST -> Rail.Shape.ASCENDING_WEST;
                case ASCENDING_SOUTH -> Rail.Shape.ASCENDING_NORTH;
                case ASCENDING_WEST -> Rail.Shape.ASCENDING_EAST;
                default -> shape;
            };
            case EAST -> switch (shape) {
                case NORTH_EAST -> Rail.Shape.NORTH_WEST;
                case NORTH_WEST -> Rail.Shape.SOUTH_WEST;
                case SOUTH_EAST -> Rail.Shape.NORTH_EAST;
                case SOUTH_WEST -> Rail.Shape.SOUTH_EAST;
                case NORTH_SOUTH -> Rail.Shape.EAST_WEST;
                case EAST_WEST -> Rail.Shape.NORTH_SOUTH;
                case ASCENDING_NORTH -> Rail.Shape.ASCENDING_WEST;
                case ASCENDING_EAST -> Rail.Shape.ASCENDING_NORTH;
                case ASCENDING_SOUTH -> Rail.Shape.ASCENDING_EAST;
                case ASCENDING_WEST -> Rail.Shape.ASCENDING_SOUTH;
            };
            case WEST -> switch (shape) {
                case NORTH_EAST -> Rail.Shape.SOUTH_EAST;
                case NORTH_WEST -> Rail.Shape.NORTH_EAST;
                case SOUTH_EAST -> Rail.Shape.SOUTH_WEST;
                case SOUTH_WEST -> Rail.Shape.NORTH_WEST;
                case NORTH_SOUTH -> Rail.Shape.EAST_WEST;
                case EAST_WEST -> Rail.Shape.NORTH_SOUTH;
                case ASCENDING_NORTH -> Rail.Shape.ASCENDING_EAST;
                case ASCENDING_EAST -> Rail.Shape.ASCENDING_SOUTH;
                case ASCENDING_SOUTH -> Rail.Shape.ASCENDING_WEST;
                case ASCENDING_WEST -> Rail.Shape.ASCENDING_NORTH;
            };
            default -> shape;
        };
    }
}
