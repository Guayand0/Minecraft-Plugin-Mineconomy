package dev.guayand0.utils;

import dev.guayand0.Mineconomy;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class TabComplete implements TabCompleter {

    private final Mineconomy plugin;

    public TabComplete(Mineconomy plugin) {
        this.plugin = plugin;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {

        if (args.length == 0) {
            return Collections.emptyList();
        }

        List<String> completions = new ArrayList<>();
        String currentArg = args[args.length - 1].toLowerCase(Locale.ROOT);

        boolean hasAdminPermission = sender.hasPermission(plugin.getName().toLowerCase() + ".admin");

        if (command.getName().equalsIgnoreCase(plugin.getName().toLowerCase())) {

            if (!hasAdminPermission) {
                return new ArrayList<>();
            }

            if (args.length == 1) {
                completions.addAll(Arrays.asList("help", "reload", "info"));
            }

        } else if (command.getName().equalsIgnoreCase("meco")) {

            if (!hasAdminPermission) {

                if (args.length == 1) {
                    completions.addAll(Arrays.asList("balance", "money"));
                }

                return filterAndSort(completions, currentArg);
            }

            if (args.length == 1) {
                completions.addAll(Arrays.asList("set", "add", "take", "balance", "money"));

            } else if (args.length == 2) {

                switch (args[0].toLowerCase()) {
                    case "set":
                    case "add":
                    case "take":
                    case "balance":
                    case "money":
                        completions.addAll(plugin.getEconomyManager().getKnownPlayerNames());
                        break;
                }

            } else if (args.length == 3) {

                switch (args[0].toLowerCase()) {
                    case "set":
                    case "add":
                    case "take":
                        completions.addAll(Arrays.asList("0"));
                        break;
                }

            }

        } else if (Arrays.asList("balance", "money").contains(command.getName().toLowerCase())) {
            if (!plugin.getConfig().getBoolean("config.balance-command-enabled", true) || !hasAdminPermission) {
                return Collections.emptyList();
            }

            if (args.length == 1) {
                completions.addAll(plugin.getEconomyManager().getKnownPlayerNames());
            }
        } else if (command.getName().equalsIgnoreCase("pay")) {
            if (!plugin.getConfig().getBoolean("config.pay-command-enabled", true)) {
                return Collections.emptyList();
            }

            if (args.length == 1) {
                completions.addAll(plugin.getEconomyManager().getKnownPlayerNames());
            } else if (args.length == 2) {
                completions.addAll(Arrays.asList("0"));
            }
        }

        return filterAndSort(completions, currentArg);
    }

    private List<String> filterAndSort(List<String> completions, String currentArg) {
        return completions.stream()
                .distinct()
                .filter(option -> option.toLowerCase(Locale.ROOT).startsWith(currentArg))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .collect(Collectors.toList());
    }
}
