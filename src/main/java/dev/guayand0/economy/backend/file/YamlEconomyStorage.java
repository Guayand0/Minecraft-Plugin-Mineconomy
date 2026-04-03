package dev.guayand0.economy.backend.file;

import dev.guayand0.economy.backend.AbstractStorageBackend;
import dev.guayand0.economy.type.StorageType;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public class YamlEconomyStorage extends AbstractStorageBackend {

    private final List<AccountBalance> reusableTopBalances = new ArrayList<>();

    public YamlEconomyStorage(Plugin plugin) {
        super(plugin);
        ensureFolderExists(getTypeFolder());
    }

    @Override
    public StorageType getType() {
        return StorageType.YAML;
    }

    @Override
    protected boolean doHasAccount(UUID uuid) {
        return getYamlFile(uuid).exists();
    }

    @Override
    protected void doCreateAccount(UUID uuid) {
        if (doHasAccount(uuid)) {
            return;
        }

        doSetBalance(uuid, 0.0D);
    }

    @Override
    protected double doGetBalance(UUID uuid) {
        File file = getYamlFile(uuid);
        if (!file.exists()) {
            doSetBalance(uuid, 0.0D);
            return 0.0D;
        }

        return YamlConfiguration.loadConfiguration(file).getDouble(BALANCE_PATH, 0.0D);
    }

    @Override
    protected void doSetBalance(UUID uuid, double amount) {
        File file = getYamlFile(uuid);
        FileConfiguration configuration = file.exists() ? YamlConfiguration.loadConfiguration(file) : new YamlConfiguration();
        configuration.set(BALANCE_PATH, amount);
        saveYaml(file, configuration);
    }

    @Override
    protected void doUpdatePlayerName(UUID uuid, String playerName) {
        File file = getYamlFile(uuid);
        FileConfiguration configuration = file.exists() ? YamlConfiguration.loadConfiguration(file) : new YamlConfiguration();
        configuration.set(PLAYER_NAME_PATH, playerName);
        saveYaml(file, configuration);
    }

    @Override
    protected List<AccountBalance> doGetTopBalances(int limit) {
        if (limit <= 0) {
            return new ArrayList<>();
        }

        synchronized (reusableTopBalances) {
            reusableTopBalances.clear();

            File[] files = getTypeFolder().listFiles((dir, name) -> name.toLowerCase().endsWith(".yml"));
            if (files == null || files.length == 0) {
                return new ArrayList<>();
            }

            for (File file : files) {
                UUID uuid = parseUuid(file.getName(), ".yml");
                if (uuid == null) {
                    continue;
                }

                FileConfiguration configuration = YamlConfiguration.loadConfiguration(file);
                double balance = configuration.getDouble(BALANCE_PATH, 0.0D);
                reusableTopBalances.add(new AccountBalance(uuid, configuration.getString(PLAYER_NAME_PATH), balance));
            }

            reusableTopBalances.sort(
                    Comparator.comparingDouble(AccountBalance::getBalance).reversed()
                            .thenComparing(accountBalance -> accountBalance.getUuid().toString(), String.CASE_INSENSITIVE_ORDER)
            );

            int resultSize = Math.min(limit, reusableTopBalances.size());
            List<AccountBalance> result = new ArrayList<>(resultSize);
            for (int i = 0; i < resultSize; i++) {
                result.add(reusableTopBalances.get(i));
            }

            reusableTopBalances.clear();
            return result;
        }
    }

    @Override
    public void close() {
    }

    @Override
    protected List<AccountBalance> doGetRegisteredAccounts() {
        synchronized (reusableTopBalances) {
            reusableTopBalances.clear();

            File[] files = getTypeFolder().listFiles((dir, name) -> name.toLowerCase().endsWith(".yml"));
            if (files == null || files.length == 0) {
                return new ArrayList<>();
            }

            for (File file : files) {
                UUID uuid = parseUuid(file.getName(), ".yml");
                if (uuid == null) {
                    continue;
                }

                FileConfiguration configuration = YamlConfiguration.loadConfiguration(file);
                reusableTopBalances.add(new AccountBalance(
                        uuid,
                        configuration.getString(PLAYER_NAME_PATH),
                        configuration.getDouble(BALANCE_PATH, 0.0D)
                ));
            }

            List<AccountBalance> result = new ArrayList<>(reusableTopBalances);
            reusableTopBalances.clear();
            return result;
        }
    }

    private void saveYaml(File file, FileConfiguration configuration) {
        try {
            configuration.save(file);
        } catch (IOException exception) {
            throw new RuntimeException("Could not save player economy file: " + file.getName(), exception);
        }
    }

    private File getYamlFile(UUID uuid) {
        return new File(getTypeFolder(), uuid.toString() + ".yml");
    }

    private UUID parseUuid(String fileName, String suffix) {
        if (!fileName.toLowerCase().endsWith(suffix)) {
            return null;
        }

        try {
            return UUID.fromString(fileName.substring(0, fileName.length() - suffix.length()));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
