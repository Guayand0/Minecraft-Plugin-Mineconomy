package dev.guayand0.utils;

import dev.guayand0.Mineconomy;
import net.milkbowl.vault.economy.AbstractEconomy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.text.DecimalFormat;
import java.util.Collections;
import java.util.List;

public class VaultEconomyProvider extends AbstractEconomy {

    private final Mineconomy plugin;
    private final EconomyManager economyManager;
    private final DecimalFormat amountFormat = new DecimalFormat("0.00");

    public VaultEconomyProvider(Mineconomy plugin) {
        this.plugin = plugin;
        this.economyManager = plugin.getEconomyManager();
    }

    @Override
    public boolean isEnabled() {
        return plugin.isEnabled();
    }

    @Override
    public String getName() {
        return plugin.getName();
    }

    @Override
    public boolean hasBankSupport() {
        return false;
    }

    @Override
    public int fractionalDigits() {
        return 2;
    }

    @Override
    public String format(double amount) {
        return amountFormat.format(amount);
    }

    @Override
    public String currencyNamePlural() {
        return "coins";
    }

    @Override
    public String currencyNameSingular() {
        return "coin";
    }

    @Override
    public boolean hasAccount(String playerName) {
        return hasAccount(resolvePlayer(playerName));
    }

    @Override
    public boolean hasAccount(OfflinePlayer player) {
        return player != null && economyManager.hasAccount(player);
    }

    @Override
    public boolean hasAccount(String playerName, String worldName) {
        return hasAccount(playerName);
    }

    @Override
    public boolean hasAccount(OfflinePlayer player, String worldName) {
        return hasAccount(player);
    }

    @Override
    public double getBalance(String playerName) {
        return getBalance(resolvePlayer(playerName));
    }

    @Override
    public double getBalance(OfflinePlayer player) {
        return player == null ? 0.0D : economyManager.getBalance(player);
    }

    @Override
    public double getBalance(String playerName, String worldName) {
        return getBalance(playerName);
    }

    @Override
    public double getBalance(OfflinePlayer player, String worldName) {
        return getBalance(player);
    }

    @Override
    public boolean has(String playerName, double amount) {
        return getBalance(playerName) >= amount;
    }

    @Override
    public boolean has(OfflinePlayer player, double amount) {
        return getBalance(player) >= amount;
    }

    @Override
    public boolean has(String playerName, String worldName, double amount) {
        return has(playerName, amount);
    }

    @Override
    public boolean has(OfflinePlayer player, String worldName, double amount) {
        return has(player, amount);
    }

    @Override
    public EconomyResponse withdrawPlayer(String playerName, double amount) {
        return withdrawPlayer(resolvePlayer(playerName), amount);
    }

    @Override
    public EconomyResponse withdrawPlayer(OfflinePlayer player, double amount) {
        if (player == null) {
            return response(0.0D, 0.0D, EconomyResponse.ResponseType.FAILURE, "Player not found");
        }

        if (amount < 0.0D) {
            return response(amount, economyManager.getBalance(player), EconomyResponse.ResponseType.FAILURE, "Cannot withdraw negative funds");
        }

        if (!economyManager.removeBalance(player, amount)) {
            return response(amount, economyManager.getBalance(player), EconomyResponse.ResponseType.FAILURE, "Insufficient funds");
        }

        return response(amount, economyManager.getBalance(player), EconomyResponse.ResponseType.SUCCESS, null);
    }

    @Override
    public EconomyResponse withdrawPlayer(String playerName, String worldName, double amount) {
        return withdrawPlayer(playerName, amount);
    }

    @Override
    public EconomyResponse withdrawPlayer(OfflinePlayer player, String worldName, double amount) {
        return withdrawPlayer(player, amount);
    }

    @Override
    public EconomyResponse depositPlayer(String playerName, double amount) {
        return depositPlayer(resolvePlayer(playerName), amount);
    }

    @Override
    public EconomyResponse depositPlayer(OfflinePlayer player, double amount) {
        if (player == null) {
            return response(0.0D, 0.0D, EconomyResponse.ResponseType.FAILURE, "Player not found");
        }

        if (amount < 0.0D) {
            return response(amount, economyManager.getBalance(player), EconomyResponse.ResponseType.FAILURE, "Cannot deposit negative funds");
        }

        economyManager.addBalance(player, amount);
        return response(amount, economyManager.getBalance(player), EconomyResponse.ResponseType.SUCCESS, null);
    }

    @Override
    public EconomyResponse depositPlayer(String playerName, String worldName, double amount) {
        return depositPlayer(playerName, amount);
    }

    @Override
    public EconomyResponse depositPlayer(OfflinePlayer player, String worldName, double amount) {
        return depositPlayer(player, amount);
    }

    @Override
    public EconomyResponse createBank(String name, String player) {
        return response(0.0D, 0.0D, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Banks are not supported");
    }

    @Override
    public EconomyResponse createBank(String name, OfflinePlayer player) {
        return response(0.0D, 0.0D, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Banks are not supported");
    }

    @Override
    public EconomyResponse deleteBank(String name) {
        return response(0.0D, 0.0D, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Banks are not supported");
    }

    @Override
    public EconomyResponse bankBalance(String name) {
        return response(0.0D, 0.0D, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Banks are not supported");
    }

    @Override
    public EconomyResponse bankHas(String name, double amount) {
        return response(0.0D, 0.0D, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Banks are not supported");
    }

    @Override
    public EconomyResponse bankWithdraw(String name, double amount) {
        return response(0.0D, 0.0D, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Banks are not supported");
    }

    @Override
    public EconomyResponse bankDeposit(String name, double amount) {
        return response(0.0D, 0.0D, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Banks are not supported");
    }

    @Override
    public EconomyResponse isBankOwner(String name, String playerName) {
        return response(0.0D, 0.0D, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Banks are not supported");
    }

    @Override
    public EconomyResponse isBankOwner(String name, OfflinePlayer player) {
        return response(0.0D, 0.0D, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Banks are not supported");
    }

    @Override
    public EconomyResponse isBankMember(String name, String playerName) {
        return response(0.0D, 0.0D, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Banks are not supported");
    }

    @Override
    public EconomyResponse isBankMember(String name, OfflinePlayer player) {
        return response(0.0D, 0.0D, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Banks are not supported");
    }

    @Override
    public List<String> getBanks() {
        return Collections.emptyList();
    }

    @Override
    public boolean createPlayerAccount(String playerName) {
        return createPlayerAccount(resolvePlayer(playerName));
    }

    @Override
    public boolean createPlayerAccount(OfflinePlayer player) {
        if (player == null) {
            return false;
        }

        return economyManager.createAccount(player);
    }

    @Override
    public boolean createPlayerAccount(String playerName, String worldName) {
        return createPlayerAccount(playerName);
    }

    private OfflinePlayer resolvePlayer(String playerName) {
        if (playerName == null || playerName.trim().isEmpty()) {
            return null;
        }

        OfflinePlayer player = economyManager.findPlayer(playerName);
        if (player != null) {
            return player;
        }

        return Bukkit.getOfflinePlayer(playerName);
    }

    private EconomyResponse response(double amount, double balance, EconomyResponse.ResponseType type, String errorMessage) {
        return new EconomyResponse(amount, balance, type, errorMessage);
    }
}
