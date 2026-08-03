# Eternia Builder

Eternia Builder is a custom Minecraft Paper plugin developed for the Eternia SMP. The plugin allows server administrators and builders to create custom structures that players can automatically construct in the world using specialized Builder Blocks.

Builds are created in Minecraft, converted into a JSON format, and then loaded into the plugin. Once configured, players can use a Builder Block to automatically construct the structure in the world.

The system is designed to make large and complex structures easier to build while allowing the Eternia SMP team to create fully customized builds for the server.

---

## Features

### Three Build Levels

Eternia Builder supports three levels of builds, allowing structures to be organized by size, complexity, or progression.

### Fully Customizable Builds

Builds can be created directly in Minecraft and converted into JSON files for use with the plugin. This allows builders to create structures with complete control over their design.

### MythicMobs NPC Support

Builds can include designated locations for MythicMobs NPCs. NPC locations are identified during the build creation process and can be configured to spawn NPCs as part of the structure.

---

## How It Works

The general workflow for creating and using a build is:

```text
Create Build in Minecraft
        |
        v
Save Build as CSV using Axiom
        |
        v
Convert CSV to JSON using Python Script
        |
        v
Place JSON in the Plugin Builds Folder
        |
        v
Use /builder <filename> in Minecraft
        |
        v
Build is Automatically Constructed
```

---

## Creating a Build

### 1. Create the Structure

Build your desired structure directly in Minecraft.

If your structure requires a MythicMobs NPC, place an **Oxidized Copper Block** at the location where the NPC should be positioned.

These blocks act as placeholders that the plugin can identify when processing the build.

---

### 2. Export the Build Using Axiom

Using the Axiom building tool:

1. Select the completed structure.
2. Open the **File** menu.
3. Select **Save as CSV**.
4. Save the CSV file to a location you can access.

The exported CSV file contains the information required to recreate the structure.

---

### 3. Convert the CSV to JSON

Use the provided Python conversion script to convert the Axiom CSV file into the JSON format required by Eternia Builder.

The resulting JSON file contains the structure data that the plugin uses to construct the build in Minecraft.

---

### 4. Add the Build to the Plugin

Place the generated JSON file inside the plugin's builds directory:

```text
plugins/
└── EterniaBuildPlugin/
    └── builds/
        └── {name}{level}.json
```

Build files should follow the naming convention:

```text
{name}{level}.json
```

For example:

```text
village1.json
village2.json
village3.json
```

The `{name}` identifies the build, while `{level}` identifies the build level.

---

### 5. Build the Structure

Once the JSON file has been added to the plugin, use the following command in Minecraft:

```text
/builder <filename>
```

For example:

```text
/builder village1
```

The plugin will load the corresponding build file and automatically construct the structure in the world.

---

## Build File Structure

Build files are stored as JSON files within the plugin's `builds` directory.

The expected file naming format is:

```text
{name}{level}.json
```

Where:

* `{name}` is the name of the build.
* `{level}` is the build level, from 1 to 3.

Example:

```text
castle1.json
castle2.json
castle3.json
```

This allows multiple versions or progression levels of the same structure to be stored and accessed through the plugin.

---

## MythicMobs NPCs

Eternia Builder supports structures that contain MythicMobs NPCs.

To designate an NPC location:

1. Place an **Oxidized Copper Block** where the NPC should appear.
2. Include the block in your Axiom selection.
3. Export the build as a CSV.
4. Convert the CSV to JSON.
5. Add the resulting JSON file to the plugin's `builds` directory.

The plugin can then use the designated location when handling the MythicMobs NPC associated with the build.

---

## Requirements

* Minecraft Server
* Paper
* Eternia Builder Plugin
* Axiom, for creating and exporting builds
* Python, for converting CSV build files to JSON
* MythicMobs, if NPC functionality is being used

---

## Installation

1. Download or build the Eternia Builder plugin.
2. Place the plugin `.jar` file into your server's `plugins` directory.
3. Start or restart the Minecraft server.
4. Locate the generated Eternia Builder plugin folder.
5. Place your JSON build files inside:

```text
plugins/EterniaBuildPlugin/builds/
```

6. Use the `/builder` command to load and construct your builds.

---

## Example Workflow

A typical build creation workflow looks like this:

```text
1. Build a structure in Minecraft
2. Place Oxidized Copper Blocks for NPC locations
3. Select the structure using Axiom
4. Export the selection as a CSV
5. Run the CSV through the Python conversion script
6. Rename the resulting JSON file
7. Place the JSON file in:
   plugins/EterniaBuildPlugin/builds/
8. Run:
   /builder <filename>
9. The structure is automatically built
```

---

## Project Purpose

Eternia Builder was created specifically for the **Eternia SMP** to simplify the process of constructing custom structures within the server.

By converting manually created Minecraft builds into reusable JSON templates, the plugin allows the Eternia SMP team to quickly deploy structures throughout the world while maintaining complete control over their design and implementation.

This system is intended to support the creation of custom locations, buildings, and other structures while providing a streamlined building experience for players.
