package dev.guayand0;

//import com.Guayand0.GY0lib.
import dev.guayand0.commands.*;
import dev.guayand0.utils.*;
import dev.guayand0.zlib.*;
import net.milkbowl.vault.economy.Economy;
import org.bstats.bukkit.Metrics;
import org.bstats.charts.SimplePie;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.net.SocketTimeoutException;
import java.util.*;

public class Mineconomy extends JavaPlugin implements Listener {

    public final String prefix = "&4&l[&6&lMineconomy&4&l]&f";
    public final String pluginName = getDescription().getName().toLowerCase();
    public final String currentVersion = getDescription().getVersion();
    public String lastVersion = currentVersion;
    public boolean updateCheckerWork = true;
    public boolean PlaceholderAPIEnable = false;
    public final Map<String, String> placeholders = new HashMap<>();

    public final static int spigotID = 133824;
    public final static int bstatsID = 30394;

    private final MessageUtils MU = new MessageUtils();
    private final UpdateChecker UC = new UpdateChecker();

    private EconomyManager economyManager;
    private VaultEconomyProvider vaultEconomyProvider;
    private SchedulerCompat schedulerCompat;
    private FileConfiguration messagesConfig;

    private Economy economy;

    @Override
    public void onEnable() {
        try {
            saveDefaultConfig();
            reloadMessages();
            registrarPluginPlaceholders();
            Bukkit.getConsoleSender().sendMessage(MU.getColoredText("&7<--------------------------------------------------------------------->"));
            Bukkit.getConsoleSender().sendMessage(MU.getColoredText(prefix + " &f- (&aVersion: &b" + currentVersion + "&f), &fBy &dGuayand0 &f- &6Thanks for downloading!"));
            Bukkit.getConsoleSender().sendMessage(MU.getColoredText("&7<--------------------------------------------------------------------->"));

            economyManager = new EconomyManager(this);
            vaultEconomyProvider = new VaultEconomyProvider(this);
            schedulerCompat = new SchedulerCompat(this);

            if (!setupEconomy()) {
                Bukkit.getConsoleSender().sendMessage(MU.getColoredText(prefix + " &cVault not found!"));
                getServer().getPluginManager().disablePlugin(this);
                return;
            }

            PlaceholderAPIEnable = Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null;

            registrarComandos();
            registrarEventos();

            for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                economyManager.loadPlayerEconomy(onlinePlayer);
            }

            Metrics metrics = new Metrics(this, bstatsID); // Bstats
            metrics.addCustomChart(new SimplePie("database_system", this::getBStatsDatabaseSystem));

            // Ejecutar comprobarActualizaciones() en bucle después de que el servidor haya iniciado completamente
            schedulerCompat.runGlobalTimer(this::checkUpdatesAsync, 100L, 576000L); // Cada 8 horas // 576000L

        } catch (Exception e) {
            getServer().getPluginManager().disablePlugin(this);
            e.printStackTrace();
            Bukkit.getConsoleSender().sendMessage(MU.getColoredText(prefix + " &cError while enabling plugin"));
        }
    }

    @Override
    public void onDisable() {
        if (economyManager != null) {
            economyManager.saveOnlinePlayers();
            economyManager.close();
        }
        Bukkit.getConsoleSender().sendMessage(MU.getColoredText(prefix + " &fDisabled, (&aVersion: &b" + currentVersion + "&f)"));
    }

    private void registrarComandos() {
        this.getCommand(pluginName).setExecutor(new MineconomyCommand(this));
        this.getCommand("meco").setExecutor(new MecoCommand(this));
        this.getCommand("balance").setExecutor(new BalanceCommand(this));
        this.getCommand("pay").setExecutor(new PayCommand(this));

        TabComplete tabComplete = new TabComplete(this);
        this.getCommand(pluginName).setTabCompleter(tabComplete);
        this.getCommand("meco").setTabCompleter(tabComplete);
        this.getCommand("balance").setTabCompleter(tabComplete);
        this.getCommand("pay").setTabCompleter(tabComplete);
    }

    private void registrarEventos() {
        Bukkit.getPluginManager().registerEvents(this, this);
    }

    // Registrar los placeholders del plugin para los mensajes
    public void registrarPluginPlaceholders() {
        placeholders.clear();

        placeholders.put("%plugin%", prefix);
        placeholders.put("%chatplugin%", getConfig().getString("config.chat-prefix", "&4&l[&6&lMineconomy&4&l]&f"));
        placeholders.put("%version%", currentVersion);
        placeholders.put("%latestversion%", lastVersion);
        placeholders.put("%link%", "https://www.spigotmc.org/resources/" + spigotID);
        placeholders.put("%author%", "Guayand0");
        placeholders.put("%dataStorage%", getConfig().getString("config.storage.type", "SQLITE"));
    }

    private String getBStatsDatabaseSystem() {
        String type = getConfig().getString("config.storage.type", "SQLITE");
        if (type == null || type.trim().isEmpty()) {
            return "SQLITE";
        }

        switch (type.trim().toUpperCase(Locale.ROOT)) {
            case "MYSQL": return "MySQL";
            case "SQLITE": return "SQLite";
            case "JSON": return "JSON";
            case "YAML": return "YAML";
            default: return "OTHER";
        }
    }

    private void checkUpdatesAsync() {
        schedulerCompat.runAsync(() -> {
            try {
                String latest = UC.getLatestSpigotVersion(spigotID, 5000);  // Obtener la última versión desde la clase UpdateChecker
                schedulerCompat.runGlobal(() -> {
                    lastVersion = latest != null ? latest : currentVersion;
                    updateCheckerWork = true;
                    registrarPluginPlaceholders();
                    comprobarActualizaciones();
                });
            } catch (Exception ex) {
                String errorMessage = ex instanceof SocketTimeoutException
                        ? prefix + " &cConnection timed out. The version will be checked later"
                        : prefix + " &cError while checking update";
                schedulerCompat.runGlobal(() -> {
                    Bukkit.getConsoleSender().sendMessage(MU.getColoredText(errorMessage));
                    lastVersion = currentVersion;
                    updateCheckerWork = false;
                });
            }
        });
    }

    // Metodo para comprobar nuevas actualizaciones
    public void comprobarActualizaciones() {
        if (UC.compareVersions(currentVersion, lastVersion) < 0) {

            /*for (String line : messagesConfig.getStringList("messages.update-checker")) {
                Bukkit.getConsoleSender().sendMessage(MU.getCheckAllPlaceholdersText(PlaceholderAPIEnable, null, line, placeholders));
            }*/

            Bukkit.getConsoleSender().sendMessage(MU.getColoredText("&b--> --> " + prefix + " &eUpdate Checker &b<-- <--"));
            Bukkit.getConsoleSender().sendMessage(MU.getColoredText("&fNew version available!"));
            Bukkit.getConsoleSender().sendMessage(MU.getColoredText("&fCurrent version: &c" + currentVersion + "&f, latest version: &a" + lastVersion + "&f!"));
            Bukkit.getConsoleSender().sendMessage("");
            Bukkit.getConsoleSender().sendMessage(MU.getColoredText( "     &eSpigotMC -> &fhttps://www.spigotmc.org/resources/" + spigotID));
            Bukkit.getConsoleSender().sendMessage("");
        } else {
            if (!updateCheckerWork) {
                Bukkit.getConsoleSender().sendMessage(MU.getColoredText(prefix + " &aYou are using the last version. &f(&b" + currentVersion + "&f)"));
            }
        }
        updateCheckerWork = true;
    }

    // Usar economía de Vault
    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) { return false; }
        getServer().getServicesManager().register(Economy.class, vaultEconomyProvider, this, ServicePriority.Highest);
        economy = vaultEconomyProvider; return true;
    }

    public Economy getEconomy() {
        return economy;
    }

    public EconomyManager getEconomyManager() {
        return economyManager;
    }

    public boolean getPlaceholderAPI() {
        return PlaceholderAPIEnable;
    }

    public void reloadMessages() {
        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }

        File messagesFile = new File(getDataFolder(), "messages.yml");
        if (!messagesFile.exists()) {
            saveResource("messages.yml", false);
        }

        messagesConfig = YamlConfiguration.loadConfiguration(messagesFile);
    }

    public void sendMessage(CommandSender sender, String path, Map<String, String> placeholders) {
        if (messagesConfig == null) {
            reloadMessages();
        }

        if (messagesConfig == null || !messagesConfig.contains(path)) {
            sendRaw(sender, Collections.singletonList(prefix + " Missing message path: " + path), placeholders);
            return;
        }

        if (messagesConfig.isList(path)) {
            sendRaw(sender, messagesConfig.getStringList(path), placeholders);
        } else {
            sendRaw(sender, Collections.singletonList(messagesConfig.getString(path)), placeholders);
        }
    }

    private void sendRaw(CommandSender sender, List<String> message, Map<String, String> placeholders) {
        for (String line : message) {
            if (sender instanceof Player) {
                Player player = (Player) sender;
                sender.sendMessage(MU.getCheckAllPlaceholdersText(getPlaceholderAPI(), player, line, placeholders));
            } else {
                sender.sendMessage(MU.getCheckAllPlaceholdersText(getPlaceholderAPI(), null, line, placeholders));
            }
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        economyManager.cachePlayer(event.getPlayer());
        economyManager.loadPlayerEconomy(event.getPlayer());

        boolean updateCheckerEnabled = getConfig().getBoolean("config.update-checker", true);
        boolean hasCheckedVersion = lastVersion != null && updateCheckerWork;
        boolean hasUpdateAvailable = hasCheckedVersion && UC.compareVersions(currentVersion, lastVersion) < 0;
        boolean canReceiveUpdateMessage = event.getPlayer().hasPermission(pluginName + ".admin") || event.getPlayer().hasPermission(pluginName + ".updatechecker");

        if (updateCheckerEnabled && hasUpdateAvailable && canReceiveUpdateMessage) {
            schedulerCompat.runGlobalLater(() -> sendMessage(event.getPlayer(), "messages.update-checker", placeholders), 20L); // 20 ticks = 1 segundo
        }
    }

    @EventHandler
    public void onPlayerExit(PlayerQuitEvent event) {
        economyManager.savePlayerEconomy(event.getPlayer());
    }
}
