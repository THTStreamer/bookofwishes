package com.theforbiddenwishingbook.service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.theforbiddenwishingbook.TheForbiddenWishingBook;
import com.theforbiddenwishingbook.config.ModConfig;
import net.minecraft.server.level.ServerPlayer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class WishHistoryService {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter FILE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public record WishLog(
            String timestamp,
            String playerName,
            String uuid,
            List<String> wishes,
            String aiResponse,
            List<String> payment,
            String status,
            String dimension,
            int x, int y, int z
    ) {}

    public static void logWish(ServerPlayer player, List<String> wishes, String aiResponse,
                               List<String> payment, String status) {
        if (!ModConfig.LOG_WISH_HISTORY.get()) return;

        try {
            Path historyDir = Path.of("config", "thebookofwishes", "wish_history");
            Files.createDirectories(historyDir);

            String dateStr = LocalDateTime.now().format(FILE_FORMAT);
            Path logFile = historyDir.resolve(player.getUUID() + "_" + dateStr + ".json");

            WishLog log = new WishLog(
                    LocalDateTime.now().format(TIMESTAMP_FORMAT),
                    player.getName().getString(),
                    player.getUUID().toString(),
                    wishes,
                    aiResponse,
                    payment,
                    status,
                    player.level().dimension().location().toString(),
                    player.blockPosition().getX(),
                    player.blockPosition().getY(),
                    player.blockPosition().getZ()
            );

            String json = GSON.toJson(log);
            Files.writeString(logFile, json + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);

            TheForbiddenWishingBook.LOGGER.info("Wish logged for {}: {}",
                    player.getName().getString(), status);
        } catch (IOException e) {
            TheForbiddenWishingBook.LOGGER.error("Failed to log wish: {}", e.getMessage());
        }
    }

    public static List<WishLog> getPlayerHistory(UUID playerUUID) {
        List<WishLog> history = new ArrayList<>();
        Path historyDir = Path.of("config", "thebookofwishes", "wish_history");

        if (!Files.exists(historyDir)) return history;

        try {
            Files.list(historyDir)
                    .filter(p -> p.getFileName().toString().startsWith(playerUUID.toString()))
                    .sorted()
                    .forEach(file -> {
                        try {
                            String content = Files.readString(file);
                            for (String line : content.split("\n")) {
                                if (!line.isBlank()) {
                                    history.add(GSON.fromJson(line, WishLog.class));
                                }
                            }
                        } catch (IOException e) {
                            TheForbiddenWishingBook.LOGGER.error("Failed to read history file: {}", e.getMessage());
                        }
                    });
        } catch (IOException e) {
            TheForbiddenWishingBook.LOGGER.error("Failed to list history files: {}", e.getMessage());
        }

        return history;
    }
}
