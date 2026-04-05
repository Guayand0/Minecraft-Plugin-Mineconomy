package dev.guayand0.economy;

import dev.guayand0.economy.backend.StorageBackend;
import dev.guayand0.economy.backend.sql.MysqlEconomyStorage;
import dev.guayand0.economy.type.StorageType;
import dev.guayand0.utils.PlayerUtils;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class EconomyManager {

    private final EconomyStorage storage;
    private final PlayerUtils playerUtils = new PlayerUtils();
    private final Map<UUID, Double> cachedBalances = new ConcurrentHashMap<>();
    private final Map<String, UUID> knownPlayersByName = new ConcurrentHashMap<>();
    private final Map<String, String> knownPlayerDisplayNames = new ConcurrentHashMap<>();
    private final Map<String, String> registeredPlayerDisplayNames = new ConcurrentHashMap<>();

    public EconomyManager(Plugin plugin) {
        this.storage = new EconomyStorage(plugin);
        cacheKnownPlayers();
        cacheRegisteredPlayers();
    }

    public void close() {
        storage.close();
    }

    public void loadPlayerEconomy(Player player) {
        cachePlayer(player);
        storage.createAccount(player.getUniqueId());
        storage.updatePlayerName(player.getUniqueId(), player.getName());
        markRegisteredPlayer(player);
        if (usesLocalBalanceCache()) {
            cachedBalances.put(player.getUniqueId(), storage.getBalance(player.getUniqueId()));
        } else {
            cachedBalances.remove(player.getUniqueId());
        }
    }

    public void savePlayerEconomy(Player player) {
        savePlayerEconomy(player.getUniqueId());
    }

    public void savePlayerEconomy(UUID uuid) {
        if (usesLocalBalanceCache()) {
            storage.setBalance(uuid, getBalance(uuid));
        }
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
            return Bukkit.getOfflinePlayer(uuid);
        }

        if (storage.getType() == StorageType.MYSQL) {
            UUID resolvedUuid = playerUtils.getUUIDFromName(name);
            if (resolvedUuid != null && storage.hasAccount(resolvedUuid)) {
                String lowerCaseName = name.toLowerCase(Locale.ROOT);
                knownPlayersByName.put(lowerCaseName, resolvedUuid);
                knownPlayerDisplayNames.put(lowerCaseName, name);
                registeredPlayerDisplayNames.put(lowerCaseName, name);
                return Bukkit.getOfflinePlayer(resolvedUuid);
            }
        }

        return null;
    }

    public boolean hasAccount(OfflinePlayer player) {
        return storage.hasAccount(player.getUniqueId());
    }

    public boolean createAccount(OfflinePlayer player) {
        cachePlayer(player);
        storage.createAccount(player.getUniqueId());
        if (player.getName() != null && !player.getName().isEmpty()) {
            storage.updatePlayerName(player.getUniqueId(), player.getName());
        }
        markRegisteredPlayer(player);
        if (player.isOnline() && usesLocalBalanceCache()) {
            cachedBalances.putIfAbsent(player.getUniqueId(), storage.getBalance(player.getUniqueId()));
        }
        return true;
    }

    public double getBalance(OfflinePlayer player) {
        cachePlayer(player);
        return getBalance(player.getUniqueId());
    }

    public double getBalance(UUID uuid) {
        if (usesLocalBalanceCache()) {
            Double cachedBalance = cachedBalances.get(uuid);
            if (cachedBalance != null) {
                return cachedBalance;
            }

            double balance = storage.getBalance(uuid);
            cachedBalances.put(uuid, balance);
            return balance;
        }

        return storage.getBalance(uuid);
    }

    public void setBalance(OfflinePlayer player, double amount) {
        cachePlayer(player);
        double normalizedAmount = Math.max(0.0D, amount);
        markRegisteredPlayer(player);
        if (usesLocalBalanceCache()) {
            cachedBalances.put(player.getUniqueId(), normalizedAmount);
        } else if (!usesLocalBalanceCache()) {
            cachedBalances.remove(player.getUniqueId());
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

    public List<String> getRegisteredPlayerNames() {
        refreshRegisteredPlayers();
        List<String> names = new ArrayList<>(registeredPlayerDisplayNames.values());
        Collections.sort(names, String.CASE_INSENSITIVE_ORDER);
        return names;
    }

    public StorageType getStorageType() {
        return storage.getType();
    }

    public String getAverageStorageOperationMillisFormatted() {
        return String.format(Locale.US, "%.3f", storage.getTotalStorageAverageMillis());
    }

    public long getTotalStorageOperationCount() {
        return storage.getTotalStorageOperationCount();
    }

    public String getReadStorageAverageMillisFormatted() {
        return String.format(Locale.US, "%.3f", storage.getReadStorageAverageMillis());
    }

    public long getReadStorageOperationCount() {
        return storage.getReadStorageOperationCount();
    }

    public String getWriteStorageAverageMillisFormatted() {
        return String.format(Locale.US, "%.3f", storage.getWriteStorageAverageMillis());
    }

    public long getWriteStorageOperationCount() {
        return storage.getWriteStorageOperationCount();
    }

    public int getLoadedPlayersCount() {
        return cachedBalances.size();
    }

    public int getActiveMysqlConnectionCount() {
        return storage.getActiveMysqlConnectionCount();
    }

    public double getMysqlBalanceFromTable(String tableName, UUID uuid) {
        return storage.getMysqlBalanceFromTable(tableName, uuid);
    }

    public List<MysqlEconomyStorage.MysqlTopEntry> getMysqlTopBalancesFromTable(String tableName, int limit) {
        return storage.getMysqlTopBalancesFromTable(tableName, limit);
    }

    public List<TopEntry> getTopEntries(int requestedAmount) {
        if (requestedAmount <= 0) {
            return Collections.emptyList();
        }

        List<StorageBackend.AccountBalance> topBalances = storage.getTopBalances(requestedAmount);
        if (topBalances.isEmpty()) {
            return Collections.emptyList();
        }

        List<TopEntry> topEntries = new ArrayList<>(topBalances.size());
        for (StorageBackend.AccountBalance accountBalance : topBalances) {
            String playerName = accountBalance.getPlayerName();
            if (playerName == null || playerName.isEmpty()) {
                playerName = getDisplayName(accountBalance.getUuid());
            }
            if (playerName == null || playerName.isEmpty()) {
                playerName = accountBalance.getUuid().toString();
            }

            topEntries.add(new TopEntry(accountBalance.getUuid(), playerName, accountBalance.getBalance()));
        }

        topEntries.sort(
                Comparator.comparingDouble(TopEntry::getBalance).reversed()
                        .thenComparing(TopEntry::getPlayerName, String.CASE_INSENSITIVE_ORDER)
        );
        return topEntries;
    }

    public int getTopPosition(UUID uuid) {
        List<TopEntry> topEntries = getTopEntries(Integer.MAX_VALUE);
        for (int i = 0; i < topEntries.size(); i++) {
            if (topEntries.get(i).getUuid().equals(uuid)) {
                return i + 1;
            }
        }

        return 0;
    }

    public void cachePlayer(OfflinePlayer player) {
        if (player != null && player.getName() != null && !player.getName().isEmpty()) {
            String lowerCaseName = player.getName().toLowerCase();
            knownPlayersByName.put(lowerCaseName, player.getUniqueId());
            knownPlayerDisplayNames.put(lowerCaseName, player.getName());
        }
    }

    public void clearLoadedPlayerCache() {
        cachedBalances.clear();
    }

    private void cacheKnownPlayers() {
        for (OfflinePlayer offlinePlayer : Bukkit.getOfflinePlayers()) {
            cachePlayer(offlinePlayer);
        }
    }

    private void cacheRegisteredPlayers() {
        registeredPlayerDisplayNames.clear();

        for (StorageBackend.AccountBalance accountBalance : storage.getRegisteredAccounts()) {
            String playerName = accountBalance.getPlayerName();
            if (playerName == null || playerName.isEmpty()) {
                playerName = getDisplayName(accountBalance.getUuid());
            }

            if (playerName == null || playerName.isEmpty()) {
                continue;
            }

            String lowerCaseName = playerName.toLowerCase(Locale.ROOT);
            registeredPlayerDisplayNames.put(lowerCaseName, playerName);
            knownPlayersByName.put(lowerCaseName, accountBalance.getUuid());
            knownPlayerDisplayNames.put(lowerCaseName, playerName);
        }
    }

    private void refreshRegisteredPlayers() {
        for (Map.Entry<String, UUID> entry : knownPlayersByName.entrySet()) {
            if (storage.hasAccount(entry.getValue())) {
                String displayName = knownPlayerDisplayNames.get(entry.getKey());
                if (displayName != null && !displayName.isEmpty()) {
                    registeredPlayerDisplayNames.put(entry.getKey(), displayName);
                }
            }
        }
        cacheRegisteredPlayers();
    }

    private void markRegisteredPlayer(OfflinePlayer player) {
        if (player != null && player.getName() != null && !player.getName().isEmpty()) {
            registeredPlayerDisplayNames.put(player.getName().toLowerCase(), player.getName());
        }
    }

    private boolean usesLocalBalanceCache() {
        return storage.getType() != StorageType.MYSQL;
    }

    private String getDisplayName(UUID uuid) {
        for (Map.Entry<String, UUID> entry : knownPlayersByName.entrySet()) {
            if (!entry.getValue().equals(uuid)) {
                continue;
            }

            String displayName = knownPlayerDisplayNames.get(entry.getKey());
            if (displayName != null && !displayName.isEmpty()) {
                return displayName;
            }
        }

        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(uuid);
        if (offlinePlayer.getName() != null && !offlinePlayer.getName().isEmpty()) {
            cachePlayer(offlinePlayer);
            return offlinePlayer.getName();
        }

        if (storage.getType() == StorageType.MYSQL) {
            String resolvedName = playerUtils.getNameFromUUID(uuid);
            if (resolvedName != null && !resolvedName.isEmpty()) {
                String lowerCaseName = resolvedName.toLowerCase(Locale.ROOT);
                knownPlayersByName.put(lowerCaseName, uuid);
                knownPlayerDisplayNames.put(lowerCaseName, resolvedName);
                registeredPlayerDisplayNames.put(lowerCaseName, resolvedName);
                return resolvedName;
            }
        }

        return null;
    }

    public static class TopEntry {
        private final UUID uuid;
        private final String playerName;
        private final double balance;

        public TopEntry(UUID uuid, String playerName, double balance) {
            this.uuid = uuid;
            this.playerName = playerName;
            this.balance = balance;
        }

        public UUID getUuid() {
            return uuid;
        }

        public String getPlayerName() {
            return playerName;
        }

        public double getBalance() {
            return balance;
        }
    }
}
