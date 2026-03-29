package dev.guayand0.commands;

import dev.guayand0.Mineconomy;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class MineconomyCommand implements CommandExecutor {

    private final Mineconomy plugin;

    public MineconomyCommand(Mineconomy plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        try {
            if (!sender.hasPermission(plugin.pluginName + ".admin")) {
                plugin.sendMessage(sender, "messages.help", plugin.placeholders);
                return true;
            }

            if (args.length == 0) {
                plugin.sendMessage(sender, "messages.admin-help", plugin.placeholders);
                return true;
            }

            switch (args[0].toLowerCase()) {

                case "reload":
                    plugin.saveDefaultConfig();
                    plugin.reloadConfig();
                    plugin.reloadMessages();
                    plugin.registrarPluginPlaceholders();
                    plugin.sendMessage(sender, "messages.reload", plugin.placeholders);
                    return true;

                case "info":
                    plugin.sendMessage(sender, "messages.info", plugin.placeholders);
                    return true;

                default:
                    plugin.sendMessage(sender, "messages.admin-help", plugin.placeholders);
                    return true;
            }

        } catch (Exception e) {
            e.printStackTrace();
            plugin.sendMessage(sender, "messages.command-error", plugin.placeholders);
        }
        
        return true;
    }
}
