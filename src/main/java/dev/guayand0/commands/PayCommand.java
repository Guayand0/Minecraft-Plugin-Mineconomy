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

public class PayCommand implements CommandExecutor {

    private final Mineconomy plugin;
    private final DecimalFormat amountFormat = new DecimalFormat("0.##");

    public PayCommand(Mineconomy plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Map<String, String> ph = new HashMap<>(plugin.placeholders);

        if (!plugin.getConfig().getBoolean("config.pay-command-enabled", true)) {
            return true;
        }

        if (!(sender instanceof Player)) {
            plugin.sendMessage(sender, "messages.only-players", ph);
            return true;
        }

        if (!sender.hasPermission("mineconomy.use")) {
            plugin.sendMessage(sender, "messages.no-permission", ph);
            return true;
        }

        if (args.length != 2) {
            plugin.sendMessage(sender, "messages.help", ph);
            return true;
        }

        Player player = (Player) sender;
        OfflinePlayer target = plugin.getEconomyManager().findPlayer(args[0]);
        if (target == null) {
            ph.put("%target%", args[0]);
            plugin.sendMessage(sender, "messages.player-not-found", ph);
            return true;
        }

        if (player.getUniqueId().equals(target.getUniqueId())) {
            plugin.sendMessage(sender, "messages.pay-self", ph);
            return true;
        }

        double amount = parseAmount(sender, ph, args[1]);
        if (amount < 0) {
            return true;
        }

        if (!plugin.getEconomyManager().removeBalance(player, amount)) {
            plugin.sendMessage(sender, "messages.balance.insufficient-balance", ph);
            return true;
        }

        plugin.getEconomyManager().addBalance(target, amount);

        ph.put("%target%", target.getName());
        ph.put("%amount%", amountFormat.format(amount));
        plugin.sendMessage(sender, "messages.pay.sent", ph);

        if (target.isOnline() && target.getPlayer() != null) {
            Map<String, String> targetPlaceholders = new HashMap<>(ph);
            targetPlaceholders.put("%target%", player.getName());
            plugin.sendMessage(target.getPlayer(), "messages.pay.received", targetPlaceholders);
        }

        return true;
    }

    private double parseAmount(CommandSender sender, Map<String, String> ph, String rawAmount) {
        try {
            double amount = Double.parseDouble(rawAmount);
            if (amount <= 0) {
                plugin.sendMessage(sender, "messages.invalid-positive-amount", ph);
                return -1;
            }

            return amount;
        } catch (NumberFormatException exception) {
            plugin.sendMessage(sender, "messages.invalid-positive-amount", ph);
            return -1;
        }
    }
}
