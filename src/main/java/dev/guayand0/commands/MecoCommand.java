package dev.guayand0.commands;

import dev.guayand0.Mineconomy;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

import java.util.Map;

public class MecoCommand extends AbstractBalanceCommand {

    private Map<String, String> ph;

    public MecoCommand(Mineconomy plugin) {
        super(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        try {
            ph = createPlaceholders();
            boolean isAdmin = sender.hasPermission(plugin.pluginName + ".admin");

            if (args.length == 0) {
                return handleSelfBalance(sender, isAdmin);
            }

            String subcommand = args[0].toLowerCase();

            if ("balance".equals(subcommand) || "money".equals(subcommand)) {
                return handleBalance(sender, args, isAdmin);
            }

            if (!isAdmin) {
                plugin.sendMessage(sender, "messages.help", ph);
                return true;
            }

            switch (subcommand) {
                case "set":
                    return handleSet(sender, args);
                case "add":
                    return handleTransaction(sender, args, true);
                case "take":
                    return handleTransaction(sender, args, false);
                default:
                    plugin.sendMessage(sender, "messages.admin-help", ph);
                    return true;
            }

        } catch (Exception e) {
            e.printStackTrace();
            plugin.sendMessage(sender, "messages.command-error", ph);
        }

        return true;
    }

    private boolean handleSelfBalance(CommandSender sender, boolean isAdmin) {
        return handleSelfBalance(sender, isAdmin, ph);
    }

    private boolean handleBalance(CommandSender sender, String[] args, boolean isAdmin) {
        if (args.length == 1) {
            return handleSelfBalance(sender, isAdmin);
        }

        if ("top".equalsIgnoreCase(args[1])) {
            if (!sender.hasPermission("mineconomy.use.top")) {
                plugin.sendMessage(sender, "messages.no-permission", ph);
                return true;
            }
            int topSize = args.length >= 3 ? parseTopSize(args[2]) : DEFAULT_TOP_SIZE;
            return sendTopBalance(sender, topSize, ph);
        }

        if (!isAdmin) {
            plugin.sendMessage(sender, "messages.no-permission", ph);
            return true;
        }

        return sendTargetBalance(sender, args[1], ph);
    }

    private boolean handleSet(CommandSender sender, String[] args) {
        if (args.length < 3) {
            plugin.sendMessage(sender, "messages.admin-help", ph);
            return true;
        }

        OfflinePlayer target = resolveTarget(sender, args[1]);
        if (target == null) return true;

        double amount = parseAmount(sender, args[2]);
        if (amount < 0) {
            return true;
        }

        plugin.getEconomyManager().setBalance(target, amount);

        notifyAdminChange(sender, "messages.balance.set", target, amount);
        notifyTarget(target, "messages.balance.set-target", amount);
        return true;
    }

    private boolean handleTransaction(CommandSender sender, String[] args, boolean deposit) {
        if (args.length < 3) {
            plugin.sendMessage(sender, "messages.admin-help", ph);
            return true;
        }

        OfflinePlayer target = resolveTarget(sender, args[1]);
        if (target == null) return true;

        double amount = parseAmount(sender, args[2]);
        if (amount < 0) {
            return true;
        }

        boolean success = true;
        if (deposit) {
            plugin.getEconomyManager().addBalance(target, amount);
        } else {
            success = plugin.getEconomyManager().removeBalance(target, amount);
        }

        if (!success) {
            plugin.sendMessage(sender, "messages.balance.insufficient-target", ph);
            return true;
        }

        String senderMessage = deposit ? "messages.balance.given" : "messages.balance.taken";
        String targetMessage = deposit ? "messages.balance.given-target" : "messages.balance.taken-target";

        notifyAdminChange(sender, senderMessage, target, amount);
        notifyTarget(target, targetMessage, amount);
        return true;
    }

    private OfflinePlayer resolveTarget(CommandSender sender, String targetName) {
        OfflinePlayer target = plugin.getEconomyManager().findPlayer(targetName);
        if (target == null) {
            ph.put("%target%", targetName);
            plugin.sendMessage(sender, "messages.player-not-found", ph);
        }

        return target;
    }

    private void notifyAdminChange(CommandSender sender, String path, OfflinePlayer target, double amount) {
        ph.put("%target%", target.getName());
        ph.put("%amount%", amountFormat.format(amount));
        ph.put("%amount_short%", plugin.formatShortAmount(amount));
        plugin.sendMessage(sender, path, ph);
    }

    private void notifyTarget(OfflinePlayer target, String path, double amount) {
        if (!plugin.getConfig().getBoolean("config.notify-target-balance-changes", false)) {
            return;
        }

        if (target.isOnline() && target.getPlayer() != null) {
            ph.put("%amount%", amountFormat.format(amount));
            ph.put("%amount_short%", plugin.formatShortAmount(amount));
            plugin.sendMessage(target.getPlayer(), path, ph);
        }
    }

    private double parseAmount(CommandSender sender, String rawAmount) {
        try {
            double amount = Double.parseDouble(rawAmount);
            if (amount < 0) {
                plugin.sendMessage(sender, "messages.invalid-amount", ph);
                return -1;
            }

            return amount;
        } catch (NumberFormatException exception) {
            plugin.sendMessage(sender, "messages.invalid-amount", ph);
            return -1;
        }
    }

}
