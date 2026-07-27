package io.github.humanrice.eterniabuilder;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

final class BuildLoader {
    private static final Gson GSON = new Gson();

    private BuildLoader() {}

    // Converts the Json to a list of blocks to place
    static List<BlockEntry> load(Path path) {
        if (!Files.exists(path)) return List.of();

        try (Reader reader = Files.newBufferedReader(path)) {
            List<BlockEntry> entries = GSON.fromJson(reader, new TypeToken<List<BlockEntry>>() {}.getType());
            return entries == null ? List.of() : entries;
        } catch (Exception ignored) {
            return List.of();
        }
    }
}
