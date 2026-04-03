package dev.guayand0.placeholderapi;

import dev.guayand0.Mineconomy;
import dev.guayand0.economy.EconomyManager;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;

import java.text.DecimalFormat;
import java.util.List;

public class PAPIVariables extends PlaceholderExpansion {

    private final Mineconomy plugin;
    private final DecimalFormat amountFormat = new DecimalFormat("0.##");

    public PAPIVariables(Mineconomy plugin) {
        this.plugin = plugin;
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
        if (identifier == null || identifier.isEmpty()) {
            return "";
        }

        if ("player_name".equalsIgnoreCase(identifier)) {
            return player != null ? player.getName() : "";
        }

        if ("player_balance".equalsIgnoreCase(identifier)) {
            return player != null ? amountFormat.format(plugin.getEconomyManager().getBalance(player)) : "0";
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

        if (identifier.startsWith("top_player_balance_")) {
            int rank = parsePositiveInt(identifier, "top_player_balance_");
            if (rank < 0) {
                return invalidRankValue(rank);
            }
            return getTopPlayerBalance(rank);
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
