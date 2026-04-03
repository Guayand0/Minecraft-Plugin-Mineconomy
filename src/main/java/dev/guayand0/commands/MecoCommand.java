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

public class MecoCommand implements CommandExecutor {

    private static final int DEFAULT_TOP_SIZE = 10;

    private final Mineconomy plugin;
    private final DecimalFormat amountFormat = new DecimalFormat("0.##");
    private Map<String, String> ph;

    public MecoCommand(Mineconomy plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        try {
            ph = new HashMap<>(plugin.placeholders);
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
        if (!sender.hasPermission("mineconomy.use") && !isAdmin) {
            plugin.sendMessage(sender, "messages.no-permission", ph);
            return true;
        }

        if (!(sender instanceof Player)) {
            plugin.sendMessage(sender, "messages.only-players", ph);
            return true;
        }

        sendBalanceMessage(sender, (Player) sender);
        return true;
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
            return sendTopBalance(sender, topSize);
        }

        if (!isAdmin) {
            plugin.sendMessage(sender, "messages.no-permission", ph);
            return true;
        }

        return sendTargetBalance(sender, args[1]);
    }

    private boolean sendTargetBalance(CommandSender sender, String targetName) {
        OfflinePlayer target = plugin.getEconomyManager().findPlayer(targetName);
        if (target == null) {
            ph.put("%target%", targetName);
            plugin.sendMessage(sender, "messages.player-not-found", ph);
            return true;
        }

        sendBalanceMessage(sender, target);
        return true;
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

    private void sendBalanceMessage(CommandSender sender, OfflinePlayer target) {
        ph.put("%target%", target.getName());
        ph.put("%amount%", amountFormat.format((plugin.getEconomyManager().getBalance(target))));
        plugin.sendMessage(sender, "messages.balance.show", ph);
    }

    private boolean sendTopBalance(CommandSender sender, int requestedTopSize) {
        plugin.sendMessage(sender, "messages.top.title", ph);

        int safeTopSize = Math.max(1, requestedTopSize);
        int position = 1;
        for (EconomyManager.TopEntry entry : plugin.getEconomyManager().getTopEntries(safeTopSize)) {
            Map<String, String> entryPlaceholders = new HashMap<>(ph);
            entryPlaceholders.put("<top>", String.valueOf(position));
            entryPlaceholders.put("%playerTop_" + position + "%", String.valueOf(position));
            entryPlaceholders.put("%playerName%", entry.getPlayerName());
            entryPlaceholders.put("%balance%", amountFormat.format(entry.getBalance()));
            entryPlaceholders.put("%playerName_" + position + "%", entry.getPlayerName());
            entryPlaceholders.put("%balance_" + position + "%", amountFormat.format(entry.getBalance()));
            plugin.sendMessage(sender, "messages.top.entry", entryPlaceholders);
            position++;
        }

        if (sender instanceof Player) {
            Player player = (Player) sender;
            Map<String, String> playerPlaceholders = new HashMap<>(ph);
            playerPlaceholders.put("%playerTop%", String.valueOf(plugin.getEconomyManager().getTopPosition(player.getUniqueId())));
            playerPlaceholders.put("%playerName%", player.getName());
            playerPlaceholders.put("%balance%", amountFormat.format(plugin.getEconomyManager().getBalance(player)));
            plugin.sendMessage(sender, "messages.top.player", playerPlaceholders);
        }

        return true;
    }

    private void notifyAdminChange(CommandSender sender, String path, OfflinePlayer target, double amount) {
        ph.put("%target%", target.getName());
        ph.put("%amount%", amountFormat.format(amount));
        plugin.sendMessage(sender, path, ph);
    }

    private void notifyTarget(OfflinePlayer target, String path, double amount) {
        if (!plugin.getConfig().getBoolean("config.notify-target-balance-changes", false)) {
            return;
        }

        if (target.isOnline() && target.getPlayer() != null) {
            ph.put("%amount%", amountFormat.format(amount));
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

    private int parseTopSize(String rawTopSize) {
        try {
            int topSize = Integer.parseInt(rawTopSize);
            return topSize > 0 ? topSize : DEFAULT_TOP_SIZE;
        } catch (NumberFormatException exception) {
            return DEFAULT_TOP_SIZE;
        }
    }
}
