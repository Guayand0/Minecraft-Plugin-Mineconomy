package dev.guayand0.economy.backend.sql;

import dev.guayand0.economy.EconomyManager;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class MysqlEconomyLookupService implements AutoCloseable {

    private final EconomyManager economyManager;

    public MysqlEconomyLookupService(EconomyManager economyManager) {
        this.economyManager = economyManager;
    }

    public double getBalance(String tableName, UUID uuid) {
        return economyManager.getMysqlBalanceFromTable(tableName, uuid);
    }

    public List<TopBalanceEntry> getTopBalances(String tableName, int limit) {
        if (limit <= 0) {
            return new ArrayList<>();
        }

        List<TopBalanceEntry> topBalances = new ArrayList<>();
        for (MysqlEconomyStorage.MysqlTopEntry entry : economyManager.getMysqlTopBalancesFromTable(tableName, limit)) {
            topBalances.add(new TopBalanceEntry(entry.getPlayerName(), entry.getBalance()));
        }
        return topBalances;
    }

    @Override
    public void close() {
    }

    public static final class TopBalanceEntry {
        private final String playerName;
        private final double balance;

        public TopBalanceEntry(String playerName, double balance) {
            this.playerName = playerName;
            this.balance = balance;
        }

        public String getPlayerName() {
            return playerName;
        }

        public double getBalance() {
            return balance;
        }
    }
}
