package dev.guayand0.zlib;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MessageUtils {

    /**
     * Translates color codes in the provided message using '&' as the color code symbol.
     *
     * @param message The message to be colored.
     * @return The colored message.
     */
    public String getColoredText(String message) {
        return ChatColor.translateAlternateColorCodes('&', message);
    }

    /**
     * Sets PlaceholderAPI placeholders for the player and translates color codes in the provided message.
     *
     * @param player The player to set placeholders for.
     * @param message The message containing placeholders and color codes.
     * @return The message with placeholders replaced and color codes applied.
     */
    public String getPAPIAndColoredText(Player player, String message) {
        return ChatColor.translateAlternateColorCodes('&', PlaceholderAPI.setPlaceholders(player, message));
    }

    /**
     * Replaces placeholders in the given message with their corresponding values.
     *
     * @param message The original message containing placeholders.
     * @param placeholders A map where keys are placeholders and values are their replacements.
     * @return The message with all placeholders replaced by their corresponding values.
     */
    public String replacePlaceholdersText(String message, Map<String, String> placeholders) {
        if (message == null || placeholders == null) return message;

        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();

            if (value == null) {
                value = "";
            }

            // Placeholder case-insensitive
            Pattern pattern = Pattern.compile(Pattern.quote(key), Pattern.CASE_INSENSITIVE);
            Matcher matcher = pattern.matcher(message);

            message = matcher.replaceAll(value);
        }

        return message;
    }

    /**
     * Checks if PlaceholderAPI is enabled and returns the message with PlaceholderAPI placeholders and Plugin placeholders replaced and colored text applied.
     *
     * @param isPAPIEnabled A boolean indicating whether PlaceholderAPI is enabled.
     * @param player The player to set placeholders for.
     * @param message The message containing placeholders and color codes.
     * @param placeholders A map where keys are placeholders and values are their replacements.
     * @return The message with placeholders replaced and color codes applied
     */
    public String getCheckAllPlaceholdersText(boolean isPAPIEnabled, Player player, String message, Map<String, String> placeholders) {
        if (isPAPIEnabled && player != null) {
            return getPAPIAndColoredText(player, replacePlaceholdersText(message, placeholders));
        } else {
            return getColoredText(replacePlaceholdersText(message, placeholders));
        }
    }

}
