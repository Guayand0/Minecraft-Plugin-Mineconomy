package dev.guayand0.commands;

import dev.guayand0.Mineconomy;
import dev.guayand0.economy.EconomyManager;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.Map;

public class BalanceCommand implements CommandExecutor {

    private static final int DEFAULT_TOP_SIZE = 10;

    private final Mineconomy plugin;
    private final DecimalFormat amountFormat = new DecimalFormat("0.##");

    public BalanceCommand(Mineconomy plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Map<String, String> ph = new HashMap<>(plugin.placeholders);
        boolean isAdmin = sender.hasPermission(plugin.pluginName + ".admin");

        if (!plugin.getConfig().getBoolean("config.balance-command-enabled", true)) {
            return true;
        }

        if (args.length == 0) {
            return handleSelfBalance(sender, isAdmin, ph);
        }

        if ("top".equalsIgnoreCase(args[0])) {
            if (!sender.hasPermission("mineconomy.use.top")) {
                plugin.sendMessage(sender, "messages.no-permission", ph);
                return true;
            }
            int topSize = args.length >= 2 ? parseTopSize(args[1]) : DEFAULT_TOP_SIZE;
            return sendTopBalance(sender, topSize, ph);
        }

        if (!isAdmin) {
            plugin.sendMessage(sender, "messages.no-permission", ph);
            return true;
        }

        if (args.length != 1) {
            plugin.sendMessage(sender, "messages.admin-help", ph);
            return true;
        }

        return sendTargetBalance(sender, args[0], ph);
    }

    private boolean handleSelfBalance(CommandSender sender, boolean isAdmin, Map<String, String> ph) {
        if (!sender.hasPermission("mineconomy.use") && !isAdmin) {
            plugin.sendMessage(sender, "messages.no-permission", ph);
            return true;
        }

        if (!(sender instanceof Player)) {
            plugin.sendMessage(sender, "messages.only-players", ph);
            return true;
        }

        sendBalanceMessage(sender, (Player) sender, ph);
        return true;
    }

    private boolean sendTargetBalance(CommandSender sender, String targetName, Map<String, String> ph) {
        OfflinePlayer target = plugin.getEconomyManager().findPlayer(targetName);
        if (target == null) {
            ph.put("%target%", targetName);
            plugin.sendMessage(sender, "messages.player-not-found", ph);
            return true;
        }

        sendBalanceMessage(sender, target, ph);
        return true;
    }

    private void sendBalanceMessage(CommandSender sender, OfflinePlayer target, Map<String, String> ph) {
        double balance = plugin.getEconomyManager().getBalance(target);
        ph.put("%target%", target.getName());
        ph.put("%amount%", amountFormat.format(balance));
        ph.put("%amount_short%", plugin.formatShortAmount(balance));
        ph.put("%balance%", amountFormat.format(balance));
        ph.put("%balance_short%", plugin.formatShortAmount(balance));
        plugin.sendMessage(sender, "messages.balance.show", ph);
    }

    private boolean sendTopBalance(CommandSender sender, int requestedTopSize, Map<String, String> ph) {
        plugin.sendMessage(sender, "messages.top.title", ph);

        int safeTopSize = Math.max(1, requestedTopSize);
        int position = 1;
        for (EconomyManager.TopEntry entry : plugin.getEconomyManager().getTopEntries(safeTopSize)) {
            Map<String, String> entryPlaceholders = new HashMap<>(ph);
            entryPlaceholders.put("<top>", String.valueOf(position));
            entryPlaceholders.put("%playerTop_" + position + "%", String.valueOf(position));
            entryPlaceholders.put("%playerName%", entry.getPlayerName());
            entryPlaceholders.put("%balance%", amountFormat.format(entry.getBalance()));
            entryPlaceholders.put("%balance_short%", plugin.formatShortAmount(entry.getBalance()));
            entryPlaceholders.put("%playerName_" + position + "%", entry.getPlayerName());
            entryPlaceholders.put("%balance_" + position + "%", amountFormat.format(entry.getBalance()));
            entryPlaceholders.put("%balance_short_" + position + "%", plugin.formatShortAmount(entry.getBalance()));
            entryPlaceholders.put("%balance_short_<top>%", plugin.formatShortAmount(entry.getBalance()));
            plugin.sendMessage(sender, "messages.top.entry", entryPlaceholders);
            position++;
        }

        if (sender instanceof Player) {
            Player player = (Player) sender;
            Map<String, String> playerPlaceholders = new HashMap<>(ph);
            double balance = plugin.getEconomyManager().getBalance(player);
            playerPlaceholders.put("%playerTop%", String.valueOf(plugin.getEconomyManager().getTopPosition(player.getUniqueId())));
            playerPlaceholders.put("%playerName%", player.getName());
            playerPlaceholders.put("%balance%", amountFormat.format(balance));
            playerPlaceholders.put("%balance_short%", plugin.formatShortAmount(balance));
            plugin.sendMessage(sender, "messages.top.player", playerPlaceholders);
        }

        return true;
    }

    private int parseTopSize(String rawTopSize) {
        try {
            int topSize = Integer.parseInt(rawTopSize);
            return topSize > 0 ? topSize : DEFAULT_TOP_SIZE;
        } catch (NumberFormatException exception) {
            return DEFAULT_TOP_SIZE;
        }
    }
}
