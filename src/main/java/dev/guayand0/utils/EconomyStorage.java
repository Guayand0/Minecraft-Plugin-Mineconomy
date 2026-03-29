package dev.guayand0.utils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URI;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class EconomyStorage {

    private static final String ECONOMY_PATH = "economy";
    private static final String DEFAULT_MYSQL_CONNECTION_PARAMS =
            "cachePrepStmts=true&prepStmtCacheSize=250&prepStmtCacheSqlLimit=2048" +
                    "&useServerPrepStmts=true&useLocalSessionState=true" +
                    "&cacheResultSetMetadata=true&cacheServerConfiguration=true" +
                    "&elideSetAutoCommits=true&maintainTimeStats=false&tcpKeepAlive=true";

    private final Plugin plugin;
    private final File rootFolder;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final StorageType type;
    private final Map<UUID, Double> mysqlBalanceCache = new ConcurrentHashMap<>();
    private final Set<UUID> mysqlKnownAccounts = ConcurrentHashMap.newKeySet();
    private final MysqlConnectionInfo mysqlConnectionInfo;
    private Connection mysqlConnection;
    private PreparedStatement mysqlSelectBalanceStatement;
    private PreparedStatement mysqlExistsStatement;
    private PreparedStatement mysqlInsertIfMissingStatement;
    private PreparedStatement mysqlWriteBalanceStatement;

    public EconomyStorage(Plugin plugin) {
        this.plugin = plugin;
        this.rootFolder = new File(plugin.getDataFolder(), "player_economy");
        this.type = resolveType(plugin.getConfig().getString("config.storage.type", "SQLITE"));
        this.mysqlConnectionInfo = type == StorageType.MYSQL ? buildMysqlConnectionInfo() : null;

        ensureFolderExists(rootFolder);

        if (type == StorageType.YAML) {
            ensureFolderExists(getTypeFolder());
        } else if (type == StorageType.JSON) {
            ensureFolderExists(getTypeFolder());
        } else if (type == StorageType.SQLITE) {
            ensureFolderExists(getTypeFolder());
            initializeSqlStorage();
        } else if (type == StorageType.MYSQL) {
            initializeMysqlStatements();
            initializeSqlStorage();
        }
    }

    public StorageType getType() {
        return type;
    }

    public synchronized boolean hasAccount(UUID uuid) {
        switch (type) {
            case YAML:
                return getYamlFile(uuid).exists() || getLegacyYamlFile(uuid).exists();
            case JSON:
                return getJsonFile(uuid).exists();
            case SQLITE:
            case MYSQL:
                return existsInDatabase(uuid);
            default:
                return false;
        }
    }

    public synchronized void createAccount(UUID uuid) {
        if (type == StorageType.MYSQL) {
            ensureMysqlAccount(uuid);
            return;
        }

        if (hasAccount(uuid)) {
            if (type == StorageType.YAML) {
                migrateLegacyYamlIfNeeded(uuid);
            }
            return;
        }

        setBalance(uuid, 0.0D);
    }

    public synchronized double getBalance(UUID uuid) {
        switch (type) {
            case YAML:
                return readYamlBalance(uuid);
            case JSON:
                return readJsonBalance(uuid);
            case SQLITE:
            case MYSQL:
                return readDatabaseBalance(uuid);
            default:
                return 0.0D;
        }
    }

    public synchronized void setBalance(UUID uuid, double amount) {
        double normalizedAmount = Math.max(0.0D, amount);

        switch (type) {
            case YAML:
                writeYamlBalance(uuid, normalizedAmount);
                return;
            case JSON:
                writeJsonBalance(uuid, normalizedAmount);
                return;
            case SQLITE:
            case MYSQL:
                writeDatabaseBalance(uuid, normalizedAmount);
                return;
            default:
        }
    }

    public void close() {
        closeMysqlResources();
        mysqlBalanceCache.clear();
        mysqlKnownAccounts.clear();
    }

    private double readYamlBalance(UUID uuid) {
        migrateLegacyYamlIfNeeded(uuid);
        File file = getYamlFile(uuid);
        if (!file.exists()) {
            writeYamlBalance(uuid, 0.0D);
            return 0.0D;
        }

        return YamlConfiguration.loadConfiguration(file).getDouble(ECONOMY_PATH, 0.0D);
    }

    private void writeYamlBalance(UUID uuid, double amount) {
        File file = getYamlFile(uuid);
        FileConfiguration configuration = YamlConfiguration.loadConfiguration(file);
        configuration.set(ECONOMY_PATH, amount);
        saveYaml(file, configuration);
    }

    private double readJsonBalance(UUID uuid) {
        File file = getJsonFile(uuid);
        if (!file.exists()) {
            writeJsonBalance(uuid, 0.0D);
            return 0.0D;
        }

        try (FileReader reader = new FileReader(file)) {
            JsonObject jsonObject = new JsonParser().parse(reader).getAsJsonObject();
            return jsonObject.has(ECONOMY_PATH) ? jsonObject.get(ECONOMY_PATH).getAsDouble() : 0.0D;
        } catch (Exception exception) {
            throw new RuntimeException("Could not read player economy file: " + file.getName(), exception);
        }
    }

    private void writeJsonBalance(UUID uuid, double amount) {
        File file = getJsonFile(uuid);
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty(ECONOMY_PATH, amount);

        try (FileWriter writer = new FileWriter(file)) {
            gson.toJson(jsonObject, writer);
        } catch (IOException exception) {
            throw new RuntimeException("Could not save player economy file: " + file.getName(), exception);
        }
    }

    private boolean existsInDatabase(UUID uuid) {
        if (type == StorageType.MYSQL && mysqlKnownAccounts.contains(uuid)) {
            return true;
        }

        if (type == StorageType.MYSQL) {
            try {
                PreparedStatement statement = getMysqlExistsStatement();
                statement.setString(1, uuid.toString());
                try (ResultSet resultSet = statement.executeQuery()) {
                    boolean exists = resultSet.next();
                    if (exists) {
                        mysqlKnownAccounts.add(uuid);
                    }
                    return exists;
                }
            } catch (SQLException exception) {
                return retryMysqlBooleanOperation(() -> existsInDatabase(uuid), exception, "Could not check player economy in MYSQL storage");
            }
        }

        String sql = "SELECT balance FROM economy WHERE uuid = ? LIMIT 1";
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uuid.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                boolean exists = resultSet.next();
                return exists;
            }
        } catch (SQLException exception) {
            throw new RuntimeException("Could not check player economy in " + type + " storage", exception);
        }
    }

    private double readDatabaseBalance(UUID uuid) {
        if (type == StorageType.MYSQL) {
            Double cachedBalance = mysqlBalanceCache.get(uuid);
            if (cachedBalance != null) {
                return cachedBalance;
            }

            try {
                PreparedStatement statement = getMysqlSelectBalanceStatement();
                statement.setString(1, uuid.toString());
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (resultSet.next()) {
                        double balance = resultSet.getDouble("balance");
                        mysqlBalanceCache.put(uuid, balance);
                        mysqlKnownAccounts.add(uuid);
                        return balance;
                    }
                }
            } catch (SQLException exception) {
                return retryMysqlDoubleOperation(() -> readDatabaseBalance(uuid), exception, "Could not read player economy from MYSQL storage");
            }

            ensureMysqlAccount(uuid);
            mysqlBalanceCache.put(uuid, 0.0D);
            mysqlKnownAccounts.add(uuid);
            return 0.0D;
        }

        String sql = "SELECT balance FROM economy WHERE uuid = ?";
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uuid.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getDouble("balance");
                }
            }
        } catch (SQLException exception) {
            throw new RuntimeException("Could not read player economy from " + type + " storage", exception);
        }

        writeDatabaseBalance(uuid, 0.0D);
        return 0.0D;
    }

    private void writeDatabaseBalance(UUID uuid, double amount) {
        if (type == StorageType.MYSQL) {
            Double cachedBalance = mysqlBalanceCache.get(uuid);
            if (cachedBalance != null && Double.compare(cachedBalance, amount) == 0) {
                mysqlKnownAccounts.add(uuid);
                return;
            }

            try {
                PreparedStatement statement = getMysqlWriteBalanceStatement();
                statement.setString(1, uuid.toString());
                statement.setDouble(2, amount);
                statement.executeUpdate();
                mysqlBalanceCache.put(uuid, amount);
                mysqlKnownAccounts.add(uuid);
                return;
            } catch (SQLException exception) {
                retryMysqlVoidOperation(() -> writeDatabaseBalance(uuid, amount), exception, "Could not save player economy to MYSQL storage");
                return;
            }
        }

        String sql = "INSERT INTO economy (uuid, balance) VALUES (?, ?) " +
                "ON CONFLICT(uuid) DO UPDATE SET balance = excluded.balance";

        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uuid.toString());
            statement.setDouble(2, amount);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new RuntimeException("Could not save player economy to " + type + " storage", exception);
        }
    }

    private void initializeSqlStorage() {
        String sql = "CREATE TABLE IF NOT EXISTS economy (" +
                "uuid CHAR(36) PRIMARY KEY," +
                "balance DOUBLE NOT NULL DEFAULT 0" +
                ")";

        if (type == StorageType.MYSQL) {
            try (Statement statement = getMysqlConnection().createStatement()) {
                statement.executeUpdate(sql);
            } catch (SQLException exception) {
                throw new RuntimeException("Could not initialize MYSQL storage", exception);
            }
            return;
        }

        try (Connection connection = openConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        } catch (SQLException exception) {
            throw new RuntimeException("Could not initialize " + type + " storage", exception);
        }
    }

    private Connection openConnection() throws SQLException {
        if (type == StorageType.SQLITE) {
            return DriverManager.getConnection("jdbc:sqlite:" + getSqliteFile().getAbsolutePath());
        }

        return DriverManager.getConnection(mysqlConnectionInfo.url, mysqlConnectionInfo.user, mysqlConnectionInfo.password);
    }

    private MysqlConnectionInfo buildMysqlConnectionInfo() {
        String mysqlUri = plugin.getConfig().getString("config.storage.mysql-uri", "").trim();
        if (!mysqlUri.isEmpty()) {
            if (mysqlUri.startsWith("jdbc:mysql://")) {
                return new MysqlConnectionInfo(applyDefaultMysqlParams(mysqlUri), getMysqlUser(), getMysqlPassword());
            }

            if (mysqlUri.startsWith("mysql://")) {
                return buildMysqlConnectionInfoFromUri(mysqlUri);
            }

            return new MysqlConnectionInfo(applyDefaultMysqlParams("jdbc:mysql://" + mysqlUri), getMysqlUser(), getMysqlPassword());
        }

        String host = plugin.getConfig().getString("config.storage.host", "localhost");
        int port = plugin.getConfig().getInt("config.storage.port", 3306);
        String database = plugin.getConfig().getString("config.storage.database", "mineconomy");
        String params = mergeMysqlConnectionParams(plugin.getConfig().getString("config.storage.connection_params", "").trim());

        StringBuilder builder = new StringBuilder("jdbc:mysql://")
                .append(host)
                .append(":")
                .append(port)
                .append("/")
                .append(database);

        if (!params.isEmpty()) {
            builder.append(params.startsWith("?") ? params : "?" + params);
        }

        return new MysqlConnectionInfo(builder.toString(), getMysqlUser(), getMysqlPassword());
    }

    private void initializeMysqlStatements() {
        try {
            Connection connection = getMysqlConnection();
            mysqlExistsStatement = connection.prepareStatement("SELECT 1 FROM economy WHERE uuid = ? LIMIT 1");
            mysqlSelectBalanceStatement = connection.prepareStatement("SELECT balance FROM economy WHERE uuid = ? LIMIT 1");
            mysqlInsertIfMissingStatement = connection.prepareStatement(
                    "INSERT INTO economy (uuid, balance) VALUES (?, 0) " +
                            "ON DUPLICATE KEY UPDATE uuid = uuid"
            );
            mysqlWriteBalanceStatement = connection.prepareStatement(
                    "INSERT INTO economy (uuid, balance) VALUES (?, ?) " +
                            "ON DUPLICATE KEY UPDATE balance = VALUES(balance)"
            );
        } catch (SQLException exception) {
            throw new RuntimeException("Could not initialize MYSQL prepared statements", exception);
        }
    }

    private Connection getMysqlConnection() throws SQLException {
        if (mysqlConnection != null && !mysqlConnection.isClosed()) {
            return mysqlConnection;
        }

        closeMysqlResources();
        mysqlConnection = DriverManager.getConnection(mysqlConnectionInfo.url, mysqlConnectionInfo.user, mysqlConnectionInfo.password);
        mysqlConnection.setAutoCommit(true);
        return mysqlConnection;
    }

    private PreparedStatement getMysqlSelectBalanceStatement() throws SQLException {
        if (mysqlSelectBalanceStatement == null || mysqlSelectBalanceStatement.isClosed()) {
            initializeMysqlStatements();
        }
        return mysqlSelectBalanceStatement;
    }

    private PreparedStatement getMysqlExistsStatement() throws SQLException {
        if (mysqlExistsStatement == null || mysqlExistsStatement.isClosed()) {
            initializeMysqlStatements();
        }
        return mysqlExistsStatement;
    }

    private PreparedStatement getMysqlInsertIfMissingStatement() throws SQLException {
        if (mysqlInsertIfMissingStatement == null || mysqlInsertIfMissingStatement.isClosed()) {
            initializeMysqlStatements();
        }
        return mysqlInsertIfMissingStatement;
    }

    private PreparedStatement getMysqlWriteBalanceStatement() throws SQLException {
        if (mysqlWriteBalanceStatement == null || mysqlWriteBalanceStatement.isClosed()) {
            initializeMysqlStatements();
        }
        return mysqlWriteBalanceStatement;
    }

    private MysqlConnectionInfo buildMysqlConnectionInfoFromUri(String mysqlUri) {
        try {
            URI uri = URI.create(mysqlUri);
            String userInfo = uri.getUserInfo();
            String user = getMysqlUser();
            String password = getMysqlPassword();

            if (userInfo != null && !userInfo.isEmpty()) {
                String[] credentials = userInfo.split(":", 2);
                user = credentials[0];
                password = credentials.length > 1 ? credentials[1] : "";
            }

            String host = uri.getHost() == null ? plugin.getConfig().getString("config.storage.host", "localhost") : uri.getHost();
            int port = uri.getPort() == -1 ? plugin.getConfig().getInt("config.storage.port", 3306) : uri.getPort();
            String path = uri.getPath() == null ? "" : uri.getPath();
            String database = path.startsWith("/") ? path.substring(1) : path;
            String query = mergeMysqlConnectionParams(uri.getRawQuery() == null ? "" : uri.getRawQuery());

            StringBuilder builder = new StringBuilder("jdbc:mysql://")
                    .append(host)
                    .append(":")
                    .append(port)
                    .append("/")
                    .append(database);

            if (query != null && !query.isEmpty()) {
                builder.append("?").append(query);
            }

            return new MysqlConnectionInfo(builder.toString(), user, password);
        } catch (Exception exception) {
            throw new IllegalArgumentException("Invalid mysql-uri: " + mysqlUri, exception);
        }
    }

    private String getMysqlUser() {
        return plugin.getConfig().getString("config.storage.user", "root");
    }

    private String getMysqlPassword() {
        return plugin.getConfig().getString("config.storage.password", "");
    }

    private void ensureMysqlAccount(UUID uuid) {
        if (mysqlKnownAccounts.contains(uuid)) {
            return;
        }

        if (existsInDatabase(uuid)) {
            return;
        }

        try {
            PreparedStatement statement = getMysqlInsertIfMissingStatement();
            statement.setString(1, uuid.toString());
            statement.executeUpdate();
            mysqlKnownAccounts.add(uuid);
        } catch (SQLException exception) {
            retryMysqlVoidOperation(() -> ensureMysqlAccount(uuid), exception, "Could not create player economy account in MYSQL storage");
        }
    }

    private String mergeMysqlConnectionParams(String rawParams) {
        if (rawParams == null || rawParams.trim().isEmpty()) {
            return DEFAULT_MYSQL_CONNECTION_PARAMS;
        }

        String params = rawParams.startsWith("?") ? rawParams.substring(1) : rawParams;
        if (params.isEmpty()) {
            return DEFAULT_MYSQL_CONNECTION_PARAMS;
        }

        return params + "&" + DEFAULT_MYSQL_CONNECTION_PARAMS;
    }

    private String applyDefaultMysqlParams(String jdbcUrl) {
        String separator = jdbcUrl.contains("?") ? "&" : "?";
        return jdbcUrl + separator + DEFAULT_MYSQL_CONNECTION_PARAMS;
    }

    private void resetMysqlConnection() {
        closeMysqlResources();
    }

    private double retryMysqlDoubleOperation(SqlDoubleSupplier supplier, SQLException cause, String message) {
        try {
            resetMysqlConnection();
            return supplier.get();
        } catch (SQLException retryException) {
            retryException.addSuppressed(cause);
            throw new RuntimeException(message, retryException);
        }
    }

    private boolean retryMysqlBooleanOperation(SqlBooleanSupplier supplier, SQLException cause, String message) {
        try {
            resetMysqlConnection();
            return supplier.get();
        } catch (SQLException retryException) {
            retryException.addSuppressed(cause);
            throw new RuntimeException(message, retryException);
        }
    }

    private void retryMysqlVoidOperation(SqlVoidRunnable runnable, SQLException cause, String message) {
        try {
            resetMysqlConnection();
            runnable.run();
        } catch (SQLException retryException) {
            retryException.addSuppressed(cause);
            throw new RuntimeException(message, retryException);
        }
    }

    private void migrateLegacyYamlIfNeeded(UUID uuid) {
        File legacyFile = getLegacyYamlFile(uuid);
        File newFile = getYamlFile(uuid);

        if (!legacyFile.exists() || newFile.exists()) {
            return;
        }

        FileConfiguration legacyConfiguration = YamlConfiguration.loadConfiguration(legacyFile);
        FileConfiguration newConfiguration = YamlConfiguration.loadConfiguration(newFile);
        newConfiguration.set(ECONOMY_PATH, legacyConfiguration.getDouble(ECONOMY_PATH, 0.0D));
        saveYaml(newFile, newConfiguration);
    }

    private void saveYaml(File file, FileConfiguration configuration) {
        try {
            configuration.save(file);
        } catch (IOException exception) {
            throw new RuntimeException("Could not save player economy file: " + file.getName(), exception);
        }
    }

    private void closeMysqlResources() {
        try {
            if (mysqlExistsStatement != null && !mysqlExistsStatement.isClosed()) {
                mysqlExistsStatement.close();
            }
        } catch (SQLException ignored) {
        }

        try {
            if (mysqlSelectBalanceStatement != null && !mysqlSelectBalanceStatement.isClosed()) {
                mysqlSelectBalanceStatement.close();
            }
        } catch (SQLException ignored) {
        }

        try {
            if (mysqlInsertIfMissingStatement != null && !mysqlInsertIfMissingStatement.isClosed()) {
                mysqlInsertIfMissingStatement.close();
            }
        } catch (SQLException ignored) {
        }

        try {
            if (mysqlWriteBalanceStatement != null && !mysqlWriteBalanceStatement.isClosed()) {
                mysqlWriteBalanceStatement.close();
            }
        } catch (SQLException ignored) {
        }

        try {
            if (mysqlConnection != null && !mysqlConnection.isClosed()) {
                mysqlConnection.close();
            }
        } catch (SQLException ignored) {
        }

        mysqlExistsStatement = null;
        mysqlSelectBalanceStatement = null;
        mysqlInsertIfMissingStatement = null;
        mysqlWriteBalanceStatement = null;
        mysqlConnection = null;
    }

    private File getTypeFolder() {
        return new File(rootFolder, type.name());
    }

    private File getYamlFile(UUID uuid) {
        return new File(getTypeFolder(), uuid.toString() + ".yml");
    }

    private File getLegacyYamlFile(UUID uuid) {
        return new File(rootFolder, uuid.toString() + ".yml");
    }

    private File getJsonFile(UUID uuid) {
        return new File(getTypeFolder(), uuid.toString() + ".json");
    }

    private File getSqliteFile() {
        return new File(getTypeFolder(), "mineconomy.db");
    }

    private void ensureFolderExists(File folder) {
        if (!folder.exists()) {
            folder.mkdirs();
        }
    }

    private StorageType resolveType(String rawType) {
        try {
            return StorageType.valueOf(rawType.trim().toUpperCase(Locale.ROOT));
        } catch (Exception exception) {
            throw new IllegalArgumentException("Invalid storage type: " + rawType);
        }
    }

    public enum StorageType {
        YAML,
        JSON,
        SQLITE,
        MYSQL
    }

    private static class MysqlConnectionInfo {
        private final String url;
        private final String user;
        private final String password;

        private MysqlConnectionInfo(String url, String user, String password) {
            this.url = url;
            this.user = user;
            this.password = password;
        }
    }

    private interface SqlDoubleSupplier {
        double get() throws SQLException;
    }

    private interface SqlBooleanSupplier {
        boolean get() throws SQLException;
    }

    private interface SqlVoidRunnable {
        void run() throws SQLException;
    }
}
