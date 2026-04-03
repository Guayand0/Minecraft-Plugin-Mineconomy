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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class TabComplete implements TabCompleter {

    private static final long PLAYER_SNAPSHOT_CACHE_MILLIS = 2000L;

    private final Mineconomy plugin;
    private final Map<String, PlayerSnapshot> playerSnapshots = new ConcurrentHashMap<>();

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
                } else if (args.length == 2 && Arrays.asList("balance", "money").contains(args[0].toLowerCase())
                        && sender.hasPermission("mineconomy.use.top")) {
                    completions.addAll(Arrays.asList("top"));
                } else if (args.length == 3 && Arrays.asList("balance", "money").contains(args[0].toLowerCase())
                        && "top".equalsIgnoreCase(args[1])) {
                    completions.addAll(Arrays.asList("1", "3", "5"));
                }

                return filterAndSort(completions, currentArg);
            }

            if (args.length == 1) {
                completions.addAll(Arrays.asList("set", "add", "take", "balance", "money"));

            } else if (args.length == 2) {

                switch (args[0].toLowerCase()) {
                    case "balance":
                    case "money":
                        completions.addAll(getTopAndPlayersSnapshot(sender, "meco:" + args[0].toLowerCase()));
                        break;
                    case "set":
                    case "add":
                    case "take":
                        completions.addAll(getPlayerSnapshot(sender, "meco:" + args[0].toLowerCase()));
                        break;
                }

            } else if (args.length == 3) {

                switch (args[0].toLowerCase()) {
                    case "balance":
                    case "money":
                        if ("top".equalsIgnoreCase(args[1])) {
                            completions.addAll(Arrays.asList("1", "3", "5"));
                        }
                        break;
                    case "set":
                    case "add":
                    case "take":
                        completions.addAll(Arrays.asList("0"));
                        break;
                }

            }

        } else if (Arrays.asList("balance", "money").contains(command.getName().toLowerCase())) {
            if (!plugin.getConfig().getBoolean("config.balance-command-enabled", true)) {
                return Collections.emptyList();
            }

            if (args.length == 1) {
                if (sender.hasPermission("mineconomy.use.top")) {
                    completions.add("top");
                }
                if (hasAdminPermission) {
                    completions.addAll(getPlayerSnapshot(sender, command.getName().toLowerCase()));
                }
            } else if (args.length == 2 && "top".equalsIgnoreCase(args[0])) {
                completions.addAll(Arrays.asList("1", "3", "5"));
            }
        } else if (command.getName().equalsIgnoreCase("pay")) {
            if (!plugin.getConfig().getBoolean("config.pay-command-enabled", true)) {
                return Collections.emptyList();
            }

            if (args.length == 1) {
                completions.addAll(getPlayerSnapshot(sender, "pay"));
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
                .collect(Collectors.toList());
    }

    private List<String> getPlayerSnapshot(CommandSender sender, String contextKey) {
        long now = System.currentTimeMillis();
        String senderKey = sender.getName().toLowerCase(Locale.ROOT) + "|" + contextKey;
        PlayerSnapshot snapshot = playerSnapshots.get(senderKey);

        if (snapshot != null && snapshot.expiresAtMillis > now) {
            return snapshot.playerNames;
        }

        List<String> playerNames = new ArrayList<>(plugin.getEconomyManager().getRegisteredPlayerNames());
        playerSnapshots.put(senderKey, new PlayerSnapshot(playerNames, now + PLAYER_SNAPSHOT_CACHE_MILLIS));
        return playerNames;
    }

    private List<String> getTopAndPlayersSnapshot(CommandSender sender, String contextKey) {
        List<String> completions = new ArrayList<>();
        completions.add("top");
        completions.addAll(getPlayerSnapshot(sender, contextKey));
        return completions;
    }

    private static class PlayerSnapshot {
        private final List<String> playerNames;
        private final long expiresAtMillis;

        private PlayerSnapshot(List<String> playerNames, long expiresAtMillis) {
            this.playerNames = playerNames;
            this.expiresAtMillis = expiresAtMillis;
        }
    }
}
