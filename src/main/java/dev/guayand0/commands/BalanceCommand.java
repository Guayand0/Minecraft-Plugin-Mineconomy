package dev.guayand0.commands;

import dev.guayand0.Mineconomy;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

import java.util.Map;

public class BalanceCommand extends AbstractBalanceCommand {

    public BalanceCommand(Mineconomy plugin) {
        super(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Map<String, String> ph = createPlaceholders();
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

}
