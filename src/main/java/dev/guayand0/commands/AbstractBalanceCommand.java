package dev.guayand0.commands;

import dev.guayand0.Mineconomy;
import dev.guayand0.commands.subcommands.TopSubCommand;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.Map;

public abstract class AbstractBalanceCommand implements CommandExecutor {

    protected static final int DEFAULT_TOP_SIZE = 10;

    protected final Mineconomy plugin;
    protected final DecimalFormat amountFormat = new DecimalFormat("0.##");
    private final TopSubCommand topSubCommand;

    protected AbstractBalanceCommand(Mineconomy plugin) {
        this.plugin = plugin;
        this.topSubCommand = new TopSubCommand(plugin, amountFormat);
    }

    protected Map<String, String> createPlaceholders() {
        return new HashMap<>(plugin.placeholders);
    }

    protected boolean handleSelfBalance(CommandSender sender, boolean isAdmin, Map<String, String> ph) {
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

    protected boolean sendTargetBalance(CommandSender sender, String targetName, Map<String, String> ph) {
        OfflinePlayer target = plugin.getEconomyManager().findPlayer(targetName);
        if (target == null) {
            ph.put("%target%", targetName);
            plugin.sendMessage(sender, "messages.player-not-found", ph);
            return true;
        }

        sendBalanceMessage(sender, target, ph);
        return true;
    }

    protected void sendBalanceMessage(CommandSender sender, OfflinePlayer target, Map<String, String> ph) {
        double balance = plugin.getEconomyManager().getBalance(target);
        ph.put("%target%", target.getName());
        ph.put("%amount%", amountFormat.format(balance));
        ph.put("%amount_short%", plugin.formatShortAmount(balance));
        ph.put("%balance%", amountFormat.format(balance));
        ph.put("%balance_short%", plugin.formatShortAmount(balance));
        plugin.sendMessage(sender, "messages.balance.show", ph);
    }

    protected boolean sendTopBalance(CommandSender sender, int requestedTopSize, Map<String, String> ph) {
        return topSubCommand.sendTopBalance(sender, requestedTopSize, ph);
    }

    protected int parseTopSize(String rawTopSize) {
        try {
            int topSize = Integer.parseInt(rawTopSize);
            return topSize > 0 ? topSize : DEFAULT_TOP_SIZE;
        } catch (NumberFormatException exception) {
            return DEFAULT_TOP_SIZE;
        }
    }
}
