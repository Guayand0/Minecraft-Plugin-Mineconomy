package dev.guayand0.commands.subcommands;

import dev.guayand0.Mineconomy;
import dev.guayand0.economy.EconomyManager;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.Map;

public class TopSubCommand {

    private final Mineconomy plugin;
    private final DecimalFormat amountFormat;

    public TopSubCommand(Mineconomy plugin, DecimalFormat amountFormat) {
        this.plugin = plugin;
        this.amountFormat = amountFormat;
    }

    public boolean sendTopBalance(CommandSender sender, int requestedTopSize, Map<String, String> ph) {
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
}
