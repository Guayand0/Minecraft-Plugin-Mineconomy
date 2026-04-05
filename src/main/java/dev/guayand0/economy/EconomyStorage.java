package dev.guayand0.economy;

import dev.guayand0.economy.backend.StorageBackend;
import dev.guayand0.economy.type.StorageType;
import dev.guayand0.economy.backend.file.JsonEconomyStorage;
import dev.guayand0.economy.backend.sql.MysqlEconomyStorage;
import dev.guayand0.economy.backend.sql.SqliteEconomyStorage;
import dev.guayand0.economy.backend.file.YamlEconomyStorage;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.UUID;

public class EconomyStorage {

    private final StorageBackend backend;

    public EconomyStorage(Plugin plugin) {
        StorageType configuredType = StorageType.fromConfig(plugin.getConfig().getString("config.storage.type", "SQLITE"));
        StorageBackend createdBackend;

        try {
            createdBackend = createBackend(plugin, configuredType);
        } catch (Exception exception) {
            plugin.getLogger().warning("[" + configuredType + "] failed to initialize, switching to YAML...");
            createdBackend = new YamlEconomyStorage(plugin);
        }

        this.backend = createdBackend;
    }

    public StorageType getType() {
        return backend.getType();
    }

    public boolean hasAccount(UUID uuid) {
        return backend.hasAccount(uuid);
    }

    public void createAccount(UUID uuid) {
        backend.createAccount(uuid);
    }

    public void updatePlayerName(UUID uuid, String playerName) {
        backend.updatePlayerName(uuid, playerName);
    }

    public double getBalance(UUID uuid) {
        return backend.getBalance(uuid);
    }

    public void setBalance(UUID uuid, double amount) {
        backend.setBalance(uuid, amount);
    }

    public double getTotalStorageAverageMillis() {
        return backend.getTotalStorageAverageMillis();
    }

    public long getTotalStorageOperationCount() {
        return backend.getTotalStorageOperationCount();
    }

    public double getReadStorageAverageMillis() {
        return backend.getReadStorageAverageMillis();
    }

    public long getReadStorageOperationCount() {
        return backend.getReadStorageOperationCount();
    }

    public double getWriteStorageAverageMillis() {
        return backend.getWriteStorageAverageMillis();
    }

    public long getWriteStorageOperationCount() {
        return backend.getWriteStorageOperationCount();
    }

    public List<StorageBackend.AccountBalance> getRegisteredAccounts() {
        return backend.getRegisteredAccounts();
    }

    public List<StorageBackend.AccountBalance> getTopBalances(int limit) {
        return backend.getTopBalances(limit);
    }

    public void close() {
        backend.close();
    }

    public int getActiveMysqlConnectionCount() {
        if (backend instanceof MysqlEconomyStorage) {
            return ((MysqlEconomyStorage) backend).getActiveConnectionCount();
        }
        return 0;
    }

    public double getMysqlBalanceFromTable(String tableName, UUID uuid) {
        if (backend instanceof MysqlEconomyStorage) {
            return ((MysqlEconomyStorage) backend).getBalanceFromTable(tableName, uuid);
        }
        throw new IllegalStateException("MYSQL table lookup is only available for MYSQL storage");
    }

    public List<MysqlEconomyStorage.MysqlTopEntry> getMysqlTopBalancesFromTable(String tableName, int limit) {
        if (backend instanceof MysqlEconomyStorage) {
            return ((MysqlEconomyStorage) backend).getTopBalancesFromTable(tableName, limit);
        }
        throw new IllegalStateException("MYSQL table lookup is only available for MYSQL storage");
    }

    private StorageBackend createBackend(Plugin plugin, StorageType type) {
        switch (type) {
            case YAML:
                return new YamlEconomyStorage(plugin);
            case JSON:
                return new JsonEconomyStorage(plugin);
            case SQLITE:
                return new SqliteEconomyStorage(plugin);
            case MYSQL:
                return new MysqlEconomyStorage(plugin);
            default:
                throw new IllegalArgumentException("Unsupported storage type: " + type);
        }
    }
}
