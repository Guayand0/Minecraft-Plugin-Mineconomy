package dev.guayand0.economy.backend.file;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.guayand0.economy.backend.AbstractStorageBackend;
import dev.guayand0.economy.type.StorageType;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.Reader;
import java.io.Writer;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public class JsonEconomyStorage extends AbstractStorageBackend {

    private final Gson gson = new Gson();
    private final List<AccountBalance> reusableTopBalances = new ArrayList<>();

    public JsonEconomyStorage(Plugin plugin) {
        super(plugin);
        ensureFolderExists(getTypeFolder());
    }

    @Override
    public StorageType getType() {
        return StorageType.JSON;
    }

    @Override
    protected boolean doHasAccount(UUID uuid) {
        return getJsonFile(uuid).exists();
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
        File file = getJsonFile(uuid);
        if (!file.exists()) {
            doSetBalance(uuid, 0.0D);
            return 0.0D;
        }

        try (Reader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            JsonObject jsonObject = JsonParser.parseReader(reader).getAsJsonObject();
            return jsonObject.has(BALANCE_PATH) ? jsonObject.get(BALANCE_PATH).getAsDouble() : 0.0D;
        } catch (Exception exception) {
            throw new RuntimeException("Could not read player economy file: " + file.getName(), exception);
        }
    }

    @Override
    protected void doSetBalance(UUID uuid, double amount) {
        File file = getJsonFile(uuid);
        JsonObject jsonObject = readJsonObject(file);
        jsonObject.addProperty(BALANCE_PATH, amount);

        try (Writer writer = Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8)) {
            gson.toJson(jsonObject, writer);
        } catch (IOException exception) {
            throw new RuntimeException("Could not save player economy file: " + file.getName(), exception);
        }
    }

    @Override
    protected void doUpdatePlayerName(UUID uuid, String playerName) {
        File file = getJsonFile(uuid);
        JsonObject jsonObject = readJsonObject(file);
        jsonObject.addProperty(PLAYER_NAME_PATH, playerName);

        try (Writer writer = Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8)) {
            gson.toJson(jsonObject, writer);
        } catch (IOException exception) {
            throw new RuntimeException("Could not save player economy file: " + file.getName(), exception);
        }
    }

    @Override
    protected List<AccountBalance> doGetTopBalances(int limit) {
        if (limit <= 0) {
            return new ArrayList<>();
        }

        synchronized (reusableTopBalances) {
            reusableTopBalances.clear();

            File[] files = getTypeFolder().listFiles((dir, name) -> name.toLowerCase().endsWith(".json"));
            if (files == null || files.length == 0) {
                return new ArrayList<>();
            }

            for (File file : files) {
                UUID uuid = parseUuid(file.getName(), ".json");
                if (uuid == null) {
                    continue;
                }

                try (Reader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
                    JsonObject jsonObject = JsonParser.parseReader(reader).getAsJsonObject();
                    double balance = jsonObject.has(BALANCE_PATH) ? jsonObject.get(BALANCE_PATH).getAsDouble() : 0.0D;
                    String playerName = jsonObject.has(PLAYER_NAME_PATH) ? jsonObject.get(PLAYER_NAME_PATH).getAsString() : null;
                    reusableTopBalances.add(new AccountBalance(uuid, playerName, balance));
                } catch (Exception exception) {
                    throw new RuntimeException("Could not read player economy file: " + file.getName(), exception);
                }
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

            File[] files = getTypeFolder().listFiles((dir, name) -> name.toLowerCase().endsWith(".json"));
            if (files == null || files.length == 0) {
                return new ArrayList<>();
            }

            for (File file : files) {
                UUID uuid = parseUuid(file.getName(), ".json");
                if (uuid == null) {
                    continue;
                }

                JsonObject jsonObject = readJsonObject(file);
                String playerName = jsonObject.has(PLAYER_NAME_PATH) ? jsonObject.get(PLAYER_NAME_PATH).getAsString() : null;
                double balance = jsonObject.has(BALANCE_PATH) ? jsonObject.get(BALANCE_PATH).getAsDouble() : 0.0D;
                reusableTopBalances.add(new AccountBalance(uuid, playerName, balance));
            }

            List<AccountBalance> result = new ArrayList<>(reusableTopBalances);
            reusableTopBalances.clear();
            return result;
        }
    }

    private File getJsonFile(UUID uuid) {
        return new File(getTypeFolder(), uuid.toString() + ".json");
    }

    private JsonObject readJsonObject(File file) {
        if (!file.exists()) {
            return new JsonObject();
        }

        try (Reader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (Exception exception) {
            throw new RuntimeException("Could not read player economy file: " + file.getName(), exception);
        }
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
