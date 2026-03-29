package dev.guayand0.commands;

import dev.guayand0.Mineconomy;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.Map;

public class BalanceCommand implements CommandExecutor {

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
        ph.put("%target%", target.getName());
        ph.put("%amount%", amountFormat.format(plugin.getEconomyManager().getBalance(target)));
        plugin.sendMessage(sender, "messages.balance.show", ph);
    }
}
