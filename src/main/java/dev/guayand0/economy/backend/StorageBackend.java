package dev.guayand0.economy.backend;

import dev.guayand0.economy.type.StorageType;

import java.util.List;
import java.util.UUID;

public interface StorageBackend {
    StorageType getType();

    boolean hasAccount(UUID uuid);

    void createAccount(UUID uuid);

    void updatePlayerName(UUID uuid, String playerName);

    double getBalance(UUID uuid);

    void setBalance(UUID uuid, double amount);

    double getTotalStorageAverageMillis();

    long getTotalStorageOperationCount();

    double getReadStorageAverageMillis();

    long getReadStorageOperationCount();

    double getWriteStorageAverageMillis();

    long getWriteStorageOperationCount();

    List<AccountBalance> getRegisteredAccounts();

    List<AccountBalance> getTopBalances(int limit);

    void close();

    final class AccountBalance {
        private final UUID uuid;
        private final String playerName;
        private final double balance;

        public AccountBalance(UUID uuid, String playerName, double balance) {
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
