import csv
import json

filepath = input("Enter the path to the CSV file: ").strip("\"")
name = input("Enter the name of the build (without extension): ")

# Read all lines once
with open(filepath, "r") as file:
    csv_lines = list(csv.reader(file))

NpcX = 0
NpcY = 0
NpcZ = 0

# Compute reference coordinates (middle X/Z, lowest Y)
RefX = round((min(int(row[1]) for row in csv_lines) + max(int(row[1]) for row in csv_lines)) / 2)
RefY = min(int(row[2]) for row in csv_lines)
RefZ = round((min(int(row[3]) for row in csv_lines) + max(int(row[3]) for row in csv_lines)) / 2)

blocks_list = []

for line in csv_lines:
    block_raw = line[0]
    exactX = int(line[1])
    exactY = int(line[2])
    exactZ = int(line[3])

    x = exactX - RefX
    y = exactY - RefY
    z = exactZ - RefZ

    # Parse block type and components
    parts = block_raw.split("[")
    material = parts[0].replace("minecraft:", "").upper()

    # Oxidized copper is used as the NPC position marker, so we skip it in the block list and save its coordinates separately
    if material == "OXIDIZED_COPPER":
        NpcX = x
        NpcY = y
        NpcZ = z
        continue

    # Initialize dict
    block_entry = {
        "block": material,
        "x": x,
        "y": y,
        "z": z
    }

    if len(parts) > 1:
        components = parts[1].replace("]", "").split(";")
        for comp in components:
            if "=" in comp:
                key, value = comp.split("=")
                key = key.strip()
                value = value.strip()

                # Normal Facing - Most blocks
                if key == "facing":
                    block_entry["facing"] = value.upper()
                # Is Block Waterlogged - Most Blocks
                elif key == "waterlogged":
                    block_entry["waterlogged"] = value == "true"
                
                # Which half of the block is full - Stairs
                elif key == "half":
                    if value == "top" or value == "bottom":
                        block_entry["half"] = value.upper()
                # Shape of Stairs
                elif key == "shape":
                    block_entry["shape"] = value.upper()

                # How is the block oriented - Logs
                elif key == "axis":
                    block_entry["axis"] = value.upper()
                
                # Which half of the block is occupied - Slabs
                elif key == "type":
                    if value == "top" or value == "bottom" or value == "double":
                        block_entry["type"] = value.upper()
                    else:
                        block_entry["chest_half"] = value.upper()

                # Is the block Open or Closed - Doors,Gates,Trapdoors
                elif key == "open":
                    block_entry["open"] = value == "true"
                # Is the block Powered - Doors,Gates,Trapdoors & Redstones
                elif key == "powered":
                    block_entry["powered"] = value == "true"
                # Which side of the block is the hinge
                elif key == "hinge":
                    block_entry["hinge"] = value.upper()

                # Is the block ON the wall - Buttons & Signs
                elif key == "in_wall":
                    block_entry["inWall"] = value == "true"
                # Which face of the block is the wall - Buttons & Signs
                elif key == "face":
                    block_entry["face"] = value.upper()

                # Connections of Fence & Gates
                elif key == "north" or key == "east" or key == "south" or key == "west":
                    if value == "false" or value == "true": # Fences
                        block_entry[f"fence_facing_{key.upper()}"] = value == "true"
                    else: # Walls
                        block_entry[f"wall_facing_{key.upper()}"] = value.upper()
                # Is wall top up - Walls
                elif key == "up":
                    block_entry["up"] = value == "true"

                # Is the block hanging - Lanterns
                elif key == "hanging":
                    block_entry["hanging"] = value == "true"

    blocks_list.append(block_entry)

    # Add an updater block
    block_entry = {
        "block": "BARRIER",
        "x": x,
        "y": y,
        "z": z + 1
    }
    blocks_list.append(block_entry)
    block_entry = {
        "block": "AIR",
        "x": x,
        "y": y,
        "z": z + 1
    }
    blocks_list.append(block_entry)

# Add NPC position as the final block in the list
blocks_list.append({
    "block": "NPC",
    "x": NpcX,
    "y": NpcY,
    "z": NpcZ
})

# Save JSON
with open(f"{name}.json", "w") as f:
    json.dump(blocks_list, f, indent=4)

print(f"Build JSON saved to {name}.json with {len(blocks_list)} blocks.")