package dev.guayand0.utils;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class EconomyManager {

    private final EconomyStorage storage;
    private final Map<UUID, Double> cachedBalances = new ConcurrentHashMap<>();
    private final Map<String, UUID> knownPlayersByName = new ConcurrentHashMap<>();
    private final Map<String, String> knownPlayerDisplayNames = new ConcurrentHashMap<>();

    public EconomyManager(Plugin plugin) {
        this.storage = new EconomyStorage(plugin);
        cacheKnownPlayers();
    }

    public void close() {
        storage.close();
    }

    public void loadPlayerEconomy(Player player) {
        cachePlayer(player);
        storage.createAccount(player.getUniqueId());
        cachedBalances.put(player.getUniqueId(), storage.getBalance(player.getUniqueId()));
    }

    public void savePlayerEconomy(Player player) {
        savePlayerEconomy(player.getUniqueId());
    }

    public void savePlayerEconomy(UUID uuid) {
        storage.setBalance(uuid, getBalance(uuid));
    }

    public void saveOnlinePlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            savePlayerEconomy(player);
        }
    }

    public OfflinePlayer findPlayer(String name) {
        Player onlinePlayer = Bukkit.getPlayerExact(name);
        if (onlinePlayer != null) {
            cachePlayer(onlinePlayer);
            return onlinePlayer;
        }

        UUID uuid = knownPlayersByName.get(name.toLowerCase());
        if (uuid != null) {
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(uuid);
            return offlinePlayer;
        }

        return null;
    }

    public boolean hasAccount(OfflinePlayer player) {
        return storage.hasAccount(player.getUniqueId());
    }

    public boolean createAccount(OfflinePlayer player) {
        cachePlayer(player);
        storage.createAccount(player.getUniqueId());
        if (player.isOnline()) {
            cachedBalances.putIfAbsent(player.getUniqueId(), storage.getBalance(player.getUniqueId()));
        }
        return true;
    }

    public double getBalance(OfflinePlayer player) {
        cachePlayer(player);
        return getBalance(player.getUniqueId());
    }

    public double getBalance(UUID uuid) {
        Double cachedBalance = cachedBalances.get(uuid);
        if (cachedBalance != null) {
            return cachedBalance;
        }

        return storage.getBalance(uuid);
    }

    public void setBalance(OfflinePlayer player, double amount) {
        cachePlayer(player);
        double normalizedAmount = Math.max(0.0D, amount);
        if (player.isOnline()) {
            cachedBalances.put(player.getUniqueId(), normalizedAmount);
        }
        storage.setBalance(player.getUniqueId(), normalizedAmount);
    }

    public void addBalance(OfflinePlayer player, double amount) {
        setBalance(player, getBalance(player) + amount);
    }

    public boolean removeBalance(OfflinePlayer player, double amount) {
        double currentBalance = getBalance(player);
        if (amount > currentBalance) {
            return false;
        }

        setBalance(player, currentBalance - amount);
        return true;
    }

    public List<String> getKnownPlayerNames() {
        List<String> names = new ArrayList<>(knownPlayerDisplayNames.values());
        Collections.sort(names, String.CASE_INSENSITIVE_ORDER);
        return names;
    }

    public void cachePlayer(OfflinePlayer player) {
        if (player != null && player.getName() != null && !player.getName().isEmpty()) {
            String lowerCaseName = player.getName().toLowerCase();
            knownPlayersByName.put(lowerCaseName, player.getUniqueId());
            knownPlayerDisplayNames.put(lowerCaseName, player.getName());
        }
    }

    private void cacheKnownPlayers() {
        for (OfflinePlayer offlinePlayer : Bukkit.getOfflinePlayers()) {
            cachePlayer(offlinePlayer);
        }
    }
}
