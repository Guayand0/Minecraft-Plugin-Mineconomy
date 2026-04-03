package dev.guayand0.utils.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class YamlDefaultsSynchronizer {

    private final JavaPlugin plugin;

    public YamlDefaultsSynchronizer(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void syncConfigKeys() {
        syncYamlFile("config.yml");
    }

    public void syncMessagesKeys() {
        syncYamlFile("messages.yml");
    }

    private void syncYamlFile(String resourceName) {
        File targetFile = new File(plugin.getDataFolder(), resourceName);
        if (!targetFile.exists()) {
            return;
        }

        YamlConfiguration defaults = loadDefaults(resourceName);
        if (defaults == null) {
            return;
        }

        YamlConfiguration current = YamlConfiguration.loadConfiguration(targetFile);
        List<String> lines;

        try {
            lines = new ArrayList<>(Files.readAllLines(targetFile.toPath(), StandardCharsets.UTF_8));
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not read " + resourceName + " to add missing keys: " + exception.getMessage());
            return;
        }

        boolean changed = false;
        for (String rootKey : defaults.getKeys(false)) {
            changed |= ensureChildEntries(lines, current, defaults, "", rootKey);
        }

        if (!changed) {
            return;
        }

        try {
            String lineSeparator = detectLineSeparator(targetFile);
            Files.writeString(targetFile.toPath(), String.join(lineSeparator, lines) + lineSeparator, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not write missing keys to " + resourceName + ": " + exception.getMessage());
        }
    }

    private YamlConfiguration loadDefaults(String resourceName) {
        try (InputStream inputStream = plugin.getResource(resourceName)) {
            if (inputStream == null) {
                plugin.getLogger().warning("Default resource not found: " + resourceName);
                return null;
            }

            return YamlConfiguration.loadConfiguration(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not load defaults for " + resourceName + ": " + exception.getMessage());
            return null;
        }
    }

    private boolean ensureChildEntries(List<String> lines, YamlConfiguration current, YamlConfiguration defaults, String parentPath, String childKey) {
        String childPath = parentPath.isEmpty() ? childKey : parentPath + "." + childKey;

        if (!current.contains(childPath)) {
            String ancestorPath = findNearestExistingAncestor(current, parentPath);
            String missingPath = ancestorPath.isEmpty() ? childPath : childPath.substring(ancestorPath.length() + 1);
            insertMissingPath(lines, current, defaults, ancestorPath, missingPath);
            return true;
        }

        if (!defaults.isConfigurationSection(childPath)) {
            return false;
        }

        boolean changed = false;
        ConfigurationSection section = defaults.getConfigurationSection(childPath);
        if (section == null) {
            return false;
        }

        for (String nestedChild : section.getKeys(false)) {
            changed |= ensureChildEntries(lines, current, defaults, childPath, nestedChild);
        }

        return changed;
    }

    private String findNearestExistingAncestor(YamlConfiguration current, String path) {
        String currentPath = path;
        while (!currentPath.isEmpty() && !current.contains(currentPath)) {
            int lastDot = currentPath.lastIndexOf('.');
            currentPath = lastDot >= 0 ? currentPath.substring(0, lastDot) : "";
        }
        return currentPath;
    }

    private void insertMissingPath(List<String> lines, YamlConfiguration current, YamlConfiguration defaults, String ancestorPath, String missingPath) {
        List<String> pathSegments = new ArrayList<>();
        if (!ancestorPath.isEmpty()) {
            pathSegments.addAll(Arrays.asList(ancestorPath.split("\\.")));
        }
        if (!missingPath.isEmpty()) {
            pathSegments.addAll(Arrays.asList(missingPath.split("\\.")));
        }

        String fullPath = String.join(".", pathSegments);
        Object value = defaults.get(fullPath);
        if (value == null && defaults.isConfigurationSection(fullPath)) {
            ConfigurationSection section = defaults.getConfigurationSection(fullPath);
            if (section != null) {
                value = section.getValues(false);
            }
        }

        List<String> blockLines = buildBlockLines(missingPath, value, getIndentLevel(ancestorPath));
        int insertIndex = findInsertIndex(lines, ancestorPath);

        if (insertIndex < lines.size() && !lines.get(insertIndex).trim().isEmpty()) {
            blockLines.add("");
        }

        lines.addAll(insertIndex, blockLines);
        current.set(fullPath, value);
    }

    private List<String> buildBlockLines(String path, Object value, int baseIndent) {
        YamlConfiguration tempConfig = new YamlConfiguration();
        tempConfig.set(path, value);
        String serialized = tempConfig.saveToString().trim();
        List<String> blockLines = new ArrayList<>();

        if (serialized.isEmpty()) {
            return blockLines;
        }

        for (String line : serialized.split("\\r?\\n")) {
            blockLines.add(" ".repeat(Math.max(0, baseIndent)) + line);
        }

        return blockLines;
    }

    private int findInsertIndex(List<String> lines, String ancestorPath) {
        if (ancestorPath.isEmpty()) {
            return lines.isEmpty() ? 0 : lines.size();
        }

        SectionLocation location = findSectionLocation(lines, ancestorPath);
        return location == null ? lines.size() : location.endIndex;
    }

    private SectionLocation findSectionLocation(List<String> lines, String path) {
        List<String> segments = Arrays.asList(path.split("\\."));
        int searchStart = 0;
        int sectionStart = -1;
        int sectionEnd = lines.size();

        for (int depth = 0; depth < segments.size(); depth++) {
            String segment = segments.get(depth);
            int indent = depth * 2;
            sectionStart = findSectionStart(lines, searchStart, sectionEnd, segment, indent);
            if (sectionStart < 0) {
                return null;
            }
            sectionEnd = findSectionEnd(lines, sectionStart, indent);
            searchStart = sectionStart + 1;
        }

        return new SectionLocation(sectionStart, sectionEnd);
    }

    private int findSectionStart(List<String> lines, int fromIndex, int toIndex, String key, int indent) {
        String expectedPrefix = " ".repeat(Math.max(0, indent)) + key + ":";
        for (int index = fromIndex; index < Math.min(lines.size(), toIndex); index++) {
            String line = lines.get(index);
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }

            if (line.startsWith(expectedPrefix)) {
                return index;
            }
        }
        return -1;
    }

    private int findSectionEnd(List<String> lines, int sectionStart, int indent) {
        for (int index = sectionStart + 1; index < lines.size(); index++) {
            String line = lines.get(index);
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }

            int lineIndent = countLeadingSpaces(line);
            if (lineIndent <= indent) {
                return index;
            }
        }
        return lines.size();
    }

    private int getIndentLevel(String path) {
        if (path == null || path.isEmpty()) {
            return 0;
        }
        return (path.split("\\.").length) * 2;
    }

    private int countLeadingSpaces(String line) {
        int count = 0;
        while (count < line.length() && line.charAt(count) == ' ') {
            count++;
        }
        return count;
    }

    private String detectLineSeparator(File file) throws IOException {
        String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
        if (content.contains("\r\n")) {
            return "\r\n";
        }
        if (content.contains("\n")) {
            return "\n";
        }
        return System.lineSeparator();
    }

    private static final class SectionLocation {
        private final int startIndex;
        private final int endIndex;

        private SectionLocation(int startIndex, int endIndex) {
            this.startIndex = startIndex;
            this.endIndex = endIndex;
        }
    }
}
