package dev.guayand0.utils;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerUtils {

    private static final int REQUEST_TIMEOUT_MILLIS = 3000;
    private static final Map<String, UUID> UUID_BY_NAME_CACHE = new ConcurrentHashMap<>();
    private static final Map<UUID, String> NAME_BY_UUID_CACHE = new ConcurrentHashMap<>();
    private static final Set<String> UUID_LOOKUP_MISSES = ConcurrentHashMap.newKeySet();
    private static final Set<UUID> NAME_LOOKUP_MISSES = ConcurrentHashMap.newKeySet();

    public UUID getUUIDFromName(String playerName) {
        if (playerName == null || playerName.trim().isEmpty()) {
            return null;
        }

        String normalizedName = playerName.trim();
        String lowerCaseName = normalizedName.toLowerCase();

        UUID cachedUuid = UUID_BY_NAME_CACHE.get(lowerCaseName);
        if (cachedUuid != null) {
            return cachedUuid;
        }

        if (UUID_LOOKUP_MISSES.contains(lowerCaseName)) {
            return null;
        }

        UUID resolvedUuid = fetchUuidFromMojang(normalizedName);
        if (resolvedUuid != null) {
            cachePlayerData(resolvedUuid, normalizedName);
            return resolvedUuid;
        }

        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(normalizedName);
        if (offlinePlayer != null && offlinePlayer.getUniqueId() != null && offlinePlayer.getName() != null && !offlinePlayer.getName().isEmpty()) {
            cachePlayerData(offlinePlayer.getUniqueId(), offlinePlayer.getName());
            return offlinePlayer.getUniqueId();
        }

        UUID_LOOKUP_MISSES.add(lowerCaseName);
        return null;
    }

    public String getNameFromUUID(UUID uuid) {
        if (uuid == null) {
            return null;
        }

        String cachedName = NAME_BY_UUID_CACHE.get(uuid);
        if (cachedName != null) {
            return cachedName;
        }

        if (NAME_LOOKUP_MISSES.contains(uuid)) {
            return null;
        }

        String resolvedName = fetchNameFromMojang(uuid);
        if (resolvedName != null && !resolvedName.isEmpty()) {
            cachePlayerData(uuid, resolvedName);
            return resolvedName;
        }

        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(uuid);
        if (offlinePlayer != null && offlinePlayer.getName() != null && !offlinePlayer.getName().isEmpty()) {
            cachePlayerData(uuid, offlinePlayer.getName());
            return offlinePlayer.getName();
        }

        NAME_LOOKUP_MISSES.add(uuid);
        return null;
    }

    private void cachePlayerData(UUID uuid, String playerName) {
        if (uuid == null || playerName == null || playerName.isEmpty()) {
            return;
        }

        String lowerCaseName = playerName.toLowerCase();
        UUID_BY_NAME_CACHE.put(lowerCaseName, uuid);
        NAME_BY_UUID_CACHE.put(uuid, playerName);
        UUID_LOOKUP_MISSES.remove(lowerCaseName);
        NAME_LOOKUP_MISSES.remove(uuid);
    }

    private UUID fetchUuidFromMojang(String playerName) {
        try {
            JsonObject jsonObject = fetchJson("https://api.mojang.com/users/profiles/minecraft/" + playerName);
            if (jsonObject == null || !jsonObject.has("id")) {
                return null;
            }

            UUID uuid = parseMojangUuid(jsonObject.get("id").getAsString());
            if (uuid != null && jsonObject.has("name")) {
                cachePlayerData(uuid, jsonObject.get("name").getAsString());
            }
            return uuid;
        } catch (Exception ignored) {
            return null;
        }
    }

    private String fetchNameFromMojang(UUID uuid) {
        try {
            JsonObject jsonObject = fetchJson("https://sessionserver.mojang.com/session/minecraft/profile/" + uuid.toString().replace("-", ""));
            if (jsonObject == null || !jsonObject.has("name")) {
                return fetchLatestNameFromHistory(uuid);
            }
            return jsonObject.get("name").getAsString();
        } catch (Exception ignored) {
            return fetchLatestNameFromHistory(uuid);
        }
    }

    private JsonObject fetchJson(String url) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(REQUEST_TIMEOUT_MILLIS);
        connection.setReadTimeout(REQUEST_TIMEOUT_MILLIS);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("User-Agent", "Mineconomy");

        int responseCode = connection.getResponseCode();
        if (responseCode != HttpURLConnection.HTTP_OK) {
            connection.disconnect();
            return null;
        }

        try (InputStreamReader reader = new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } finally {
            connection.disconnect();
        }
    }

    private UUID parseMojangUuid(String rawUuid) {
        if (rawUuid == null || rawUuid.length() != 32) {
            return null;
        }

        String dashedUuid = rawUuid.substring(0, 8) + "-" +
                rawUuid.substring(8, 12) + "-" +
                rawUuid.substring(12, 16) + "-" +
                rawUuid.substring(16, 20) + "-" +
                rawUuid.substring(20);

        try {
            return UUID.fromString(dashedUuid);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private String fetchLatestNameFromHistory(UUID uuid) {
        try {
            HttpURLConnection connection = (HttpURLConnection) new URL("https://api.mojang.com/user/profiles/" + uuid.toString().replace("-", "") + "/names").openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(REQUEST_TIMEOUT_MILLIS);
            connection.setReadTimeout(REQUEST_TIMEOUT_MILLIS);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent", "Mineconomy");

            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                connection.disconnect();
                return null;
            }

            try (InputStreamReader reader = new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8)) {
                com.google.gson.JsonArray jsonArray = JsonParser.parseReader(reader).getAsJsonArray();
                if (jsonArray.size() == 0) {
                    return null;
                }

                JsonObject latestEntry = jsonArray.get(jsonArray.size() - 1).getAsJsonObject();
                return latestEntry.has("name") ? latestEntry.get("name").getAsString() : null;
            } finally {
                connection.disconnect();
            }
        } catch (Exception ignored) {
            return null;
        }
    }
}
