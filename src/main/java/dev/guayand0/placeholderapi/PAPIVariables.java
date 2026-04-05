package dev.guayand0.placeholderapi;

import dev.guayand0.Mineconomy;
import dev.guayand0.economy.EconomyManager;
import dev.guayand0.economy.backend.sql.MysqlEconomyLookupService;
import dev.guayand0.economy.type.StorageType;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.List;

public class PAPIVariables extends PlaceholderExpansion {

    private static final String[] SHORT_SUFFIXES = {"", "K", "M", "B", "T", "Q"};

    private final Mineconomy plugin;
    private final DecimalFormat amountFormat = new DecimalFormat("0.##");
    private final DecimalFormat shortAmountFormat = new DecimalFormat("0.#");

    public PAPIVariables(Mineconomy plugin) {
        this.plugin = plugin;
        shortAmountFormat.setRoundingMode(RoundingMode.DOWN);
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public boolean canRegister() {
        return true;
    }

    @Override
    public String getAuthor() {
        return "Guayand0";
    }

    @Override
    public String getIdentifier() {
        return plugin.pluginName;
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public String onPlaceholderRequest(Player player, String identifier) {
        return resolvePlaceholder(player, identifier);
    }

    @Override
    public String onRequest(OfflinePlayer player, String identifier) {
        Player onlinePlayer = player instanceof Player ? (Player) player : null;
        return resolvePlaceholder(onlinePlayer, identifier);
    }

    private String resolvePlaceholder(Player player, String identifier) {
        if (identifier == null || identifier.isEmpty()) {
            return "";
        }

        String currentTableAliasValue = getConfiguredMysqlAliasValue(player, identifier);
        if (currentTableAliasValue != null) {
            return currentTableAliasValue;
        }

        String dynamicMysqlValue = getDynamicMysqlPlaceholderValue(player, identifier);
        if (dynamicMysqlValue != null) {
            return dynamicMysqlValue;
        }

        if ("player_name".equalsIgnoreCase(identifier)) {
            return player != null ? player.getName() : "";
        }

        if ("mysql_current_economy".equalsIgnoreCase(identifier)) {
            return plugin.getEconomyManager().getStorageType() == StorageType.MYSQL
                    ? plugin.getConfiguredMysqlTable()
                    : plugin.getEconomyManager().getStorageType().name();
        }

        if ("storage_type".equalsIgnoreCase(identifier)) {
            return plugin.getEconomyManager().getStorageType().name();
        }

        if ("player_balance".equalsIgnoreCase(identifier)) {
            return player != null ? amountFormat.format(plugin.getEconomyManager().getBalance(player)) : "0";
        }

        if ("player_balance_short".equalsIgnoreCase(identifier)) {
            return player != null ? formatShortAmount(plugin.getEconomyManager().getBalance(player)) : "0";
        }

        if ("player_top".equalsIgnoreCase(identifier)) {
            if (player == null) {
                return "0";
            }
            return String.valueOf(plugin.getEconomyManager().getTopPosition(player.getUniqueId()));
        }

        if (identifier.startsWith("top_player_name_")) {
            int rank = parsePositiveInt(identifier, "top_player_name_");
            if (rank < 0) {
                return invalidRankValue(rank);
            }
            return getTopPlayerName(rank);
        }

        if (identifier.startsWith("top_player_balance_short_")) {
            int rank = parsePositiveInt(identifier, "top_player_balance_short_");
            if (rank < 0) {
                return invalidRankValue(rank);
            }
            return getTopPlayerBalanceShort(rank);
        }

        if (identifier.startsWith("top_player_balance_")) {
            int rank = parsePositiveInt(identifier, "top_player_balance_");
            if (rank < 0) {
                return invalidRankValue(rank);
            }
            return getTopPlayerBalance(rank);
        }

        return null;
    }

    private String getConfiguredMysqlAliasValue(Player player, String identifier) {
        if (plugin.getEconomyManager().getStorageType() != StorageType.MYSQL) {
            return null;
        }

        String configuredTable = plugin.getConfiguredMysqlTable();
        if (identifier.equalsIgnoreCase(configuredTable + "_player_balance")) {
            return player != null ? amountFormat.format(plugin.getEconomyManager().getBalance(player)) : "0";
        }

        if (identifier.equalsIgnoreCase(configuredTable + "_player_top")) {
            if (player == null) {
                return "0";
            }
            return String.valueOf(plugin.getEconomyManager().getTopPosition(player.getUniqueId()));
        }

        if (identifier.regionMatches(true, 0, configuredTable + "_top_player_name_", 0, (configuredTable + "_top_player_name_").length())) {
            int rank = parsePositiveInt(identifier, configuredTable + "_top_player_name_");
            if (rank < 0) {
                return invalidRankValue(rank);
            }
            return getTopPlayerName(rank);
        }

        if (identifier.regionMatches(true, 0, configuredTable + "_top_player_balance_", 0, (configuredTable + "_top_player_balance_").length())) {
            int rank = parsePositiveInt(identifier, configuredTable + "_top_player_balance_");
            if (rank < 0) {
                return invalidRankValue(rank);
            }
            return getTopPlayerBalance(rank);
        }

        return null;
    }

    private String getDynamicMysqlPlaceholderValue(Player player, String identifier) {
        if (!identifier.regionMatches(true, 0, "mysql_", 0, "mysql_".length())) {
            return null;
        }

        String mysqlIdentifier = identifier.substring("mysql_".length());
        int firstSeparator = mysqlIdentifier.indexOf('_');
        if (firstSeparator <= 0 || firstSeparator >= mysqlIdentifier.length() - 1) {
            return null;
        }

        String economyName = mysqlIdentifier.substring(0, firstSeparator).trim();
        if (economyName.isEmpty()) {
            return null;
        }

        String subIdentifier = mysqlIdentifier.substring(firstSeparator + 1);
        if (plugin.getEconomyManager().getStorageType() != StorageType.MYSQL) {
        if ("player_balance".equalsIgnoreCase(subIdentifier)
                    || "player_balance_short".equalsIgnoreCase(subIdentifier)
                    || "player_top".equalsIgnoreCase(subIdentifier)
                    || subIdentifier.startsWith("top_player_name_")
                    || subIdentifier.startsWith("top_player_balance_")
                    || subIdentifier.startsWith("top_player_balance_short_")) {
                return "MYSQL_ONLY";
            }
            return null;
        }

        MysqlEconomyLookupService lookupService = plugin.getMysqlEconomyLookupService();
        if (lookupService == null) {
            return null;
        }

        if ("player_balance".equalsIgnoreCase(subIdentifier)) {
            if (player == null) {
                return "0";
            }
            return amountFormat.format(lookupService.getBalance(economyName, player.getUniqueId()));
        }

        if ("player_balance_short".equalsIgnoreCase(subIdentifier)) {
            if (player == null) {
                return "0";
            }
            return formatShortAmount(lookupService.getBalance(economyName, player.getUniqueId()));
        }

        if (subIdentifier.startsWith("top_player_balance_short_")) {
            int rank = parsePositiveInt(subIdentifier, "top_player_balance_short_");
            if (rank < 0) {
                return invalidRankValue(rank);
            }

            List<MysqlEconomyLookupService.TopBalanceEntry> topEntries = lookupService.getTopBalances(economyName, rank);
            return topEntries.size() >= rank ? formatShortAmount(topEntries.get(rank - 1).getBalance()) : "0";
        }

        if (subIdentifier.startsWith("top_player_balance_")) {
            int rank = parsePositiveInt(subIdentifier, "top_player_balance_");
            if (rank < 0) {
                return invalidRankValue(rank);
            }

            List<MysqlEconomyLookupService.TopBalanceEntry> topEntries = lookupService.getTopBalances(economyName, rank);
            return topEntries.size() >= rank ? amountFormat.format(topEntries.get(rank - 1).getBalance()) : "0";
        }

        if (subIdentifier.startsWith("top_player_name_")) {
            int rank = parsePositiveInt(subIdentifier, "top_player_name_");
            if (rank < 0) {
                return invalidRankValue(rank);
            }

            List<MysqlEconomyLookupService.TopBalanceEntry> topEntries = lookupService.getTopBalances(economyName, rank);
            if (topEntries.size() < rank) {
                return "";
            }

            String playerName = topEntries.get(rank - 1).getPlayerName();
            return playerName == null ? "" : playerName;
        }

        if (subIdentifier.startsWith("player_top")) {
            if (player == null) {
                return "0";
            }

            List<MysqlEconomyLookupService.TopBalanceEntry> topEntries = lookupService.getTopBalances(economyName, Integer.MAX_VALUE);
            double playerBalance = lookupService.getBalance(economyName, player.getUniqueId());
            String formattedBalance = amountFormat.format(playerBalance);

            for (int i = 0; i < topEntries.size(); i++) {
                if (formattedBalance.equals(amountFormat.format(topEntries.get(i).getBalance()))) {
                    return String.valueOf(i + 1);
                }
            }
            return "0";
        }

        return null;
    }

    private String getTopPlayerName(int rank) {
        List<EconomyManager.TopEntry> topEntries = plugin.getEconomyManager().getTopEntries(rank);
        return topEntries.size() >= rank ? topEntries.get(rank - 1).getPlayerName() : "";
    }

    private String getTopPlayerBalance(int rank) {
        List<EconomyManager.TopEntry> topEntries = plugin.getEconomyManager().getTopEntries(rank);
        return topEntries.size() >= rank ? amountFormat.format(topEntries.get(rank - 1).getBalance()) : "0";
    }

    private String getTopPlayerBalanceShort(int rank) {
        List<EconomyManager.TopEntry> topEntries = plugin.getEconomyManager().getTopEntries(rank);
        return topEntries.size() >= rank ? formatShortAmount(topEntries.get(rank - 1).getBalance()) : "0";
    }

    private String formatShortAmount(double amount) {
        double normalizedAmount = Math.max(0.0D, amount);
        if (normalizedAmount < 1000.0D) {
            return shortAmountFormat.format(normalizedAmount);
        }

        int suffixIndex = 0;
        double shortenedAmount = normalizedAmount;
        while (shortenedAmount >= 1000.0D && suffixIndex < SHORT_SUFFIXES.length - 1) {
            shortenedAmount /= 1000.0D;
            suffixIndex++;
        }

        return shortAmountFormat.format(shortenedAmount) + SHORT_SUFFIXES[suffixIndex];
    }

    private int parsePositiveInt(String identifier, String prefix) {
        try {
            int rank = Integer.parseInt(identifier.substring(prefix.length()));
            return rank > 0 ? rank : -2;
        } catch (NumberFormatException exception) {
            return -1;
        }
    }

    private String invalidRankValue(int result) {
        return result == -2 ? "NEGATIVE_VALUE" : "NOT_A_NUMBER";
    }
}
