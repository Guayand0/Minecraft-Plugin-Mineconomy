package dev.guayand0.economy.backend.sql;

import dev.guayand0.economy.backend.AbstractStorageBackend;
import dev.guayand0.economy.type.StorageType;
import org.bukkit.plugin.Plugin;

import java.net.URI;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class MysqlEconomyStorage extends AbstractStorageBackend {

    private static final String DEFAULT_MYSQL_CONNECTION_PARAMS =
            "cachePrepStmts=true&prepStmtCacheSize=250&prepStmtCacheSqlLimit=2048" +
                    "&useServerPrepStmts=true&useLocalSessionState=true" +
                    "&cacheResultSetMetadata=true&cacheServerConfiguration=true" +
                    "&elideSetAutoCommits=true&maintainTimeStats=false&tcpKeepAlive=true";

    private final Set<UUID> knownAccounts = ConcurrentHashMap.newKeySet();
    private final MysqlConnectionInfo connectionInfo;
    private Connection mysqlConnection;
    private PreparedStatement mysqlSelectBalanceStatement;
    private PreparedStatement mysqlExistsStatement;
    private PreparedStatement mysqlInsertIfMissingStatement;
    private PreparedStatement mysqlWriteBalanceStatement;
    private PreparedStatement mysqlUpdatePlayerNameStatement;
    private PreparedStatement mysqlRegisteredAccountsStatement;
    private PreparedStatement mysqlTopBalancesStatement;

    public MysqlEconomyStorage(Plugin plugin) {
        super(plugin);
        this.connectionInfo = buildMysqlConnectionInfo();
        initialize();
    }

    @Override
    public StorageType getType() {
        return StorageType.MYSQL;
    }

    @Override
    protected boolean doHasAccount(UUID uuid) {
        if (knownAccounts.contains(uuid)) {
            return true;
        }

        try {
            PreparedStatement statement = getMysqlExistsStatement();
            statement.setString(1, uuid.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                boolean exists = resultSet.next();
                if (exists) {
                    knownAccounts.add(uuid);
                }
                return exists;
            }
        } catch (SQLException exception) {
            return retryMysqlBooleanOperation(() -> doHasAccount(uuid), exception, "Could not check player economy in MYSQL storage");
        }
    }

    @Override
    protected void doCreateAccount(UUID uuid) {
        if (knownAccounts.contains(uuid) || doHasAccount(uuid)) {
            return;
        }

        try {
            PreparedStatement statement = getMysqlInsertIfMissingStatement();
            statement.setString(1, uuid.toString());
            statement.executeUpdate();
            knownAccounts.add(uuid);
        } catch (SQLException exception) {
            retryMysqlVoidOperation(() -> doCreateAccount(uuid), exception, "Could not create player economy account in MYSQL storage");
        }
    }

    @Override
    protected double doGetBalance(UUID uuid) {
        try {
            PreparedStatement statement = getMysqlSelectBalanceStatement();
            statement.setString(1, uuid.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    knownAccounts.add(uuid);
                    return resultSet.getDouble("balance");
                }
            }
        } catch (SQLException exception) {
            return retryMysqlDoubleOperation(() -> doGetBalance(uuid), exception, "Could not read player economy from MYSQL storage");
        }

        doCreateAccount(uuid);
        knownAccounts.add(uuid);
        return 0.0D;
    }

    @Override
    protected void doSetBalance(UUID uuid, double amount) {
        try {
            PreparedStatement statement = getMysqlWriteBalanceStatement();
            statement.setString(1, uuid.toString());
            statement.setDouble(2, amount);
            statement.executeUpdate();
            knownAccounts.add(uuid);
        } catch (SQLException exception) {
            retryMysqlVoidOperation(() -> doSetBalance(uuid, amount), exception, "Could not save player economy to MYSQL storage");
        }
    }

    @Override
    protected void doUpdatePlayerName(UUID uuid, String playerName) {
        try {
            PreparedStatement statement = getMysqlUpdatePlayerNameStatement();
            statement.setString(1, playerName);
            statement.setString(2, uuid.toString());
            statement.executeUpdate();
            knownAccounts.add(uuid);
        } catch (SQLException exception) {
            retryMysqlVoidOperation(() -> doUpdatePlayerName(uuid, playerName), exception, "Could not update player name in MYSQL storage");
        }
    }

    @Override
    protected List<AccountBalance> doGetTopBalances(int limit) {
        if (limit <= 0) {
            return new ArrayList<>();
        }

        List<AccountBalance> topBalances = new ArrayList<>();
        try {
            PreparedStatement statement = getMysqlTopBalancesStatement();
            statement.setInt(1, limit);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    UUID uuid = UUID.fromString(resultSet.getString("uuid"));
                    knownAccounts.add(uuid);
                    topBalances.add(new AccountBalance(uuid, resultSet.getString("player_name"), resultSet.getDouble("balance")));
                }
            }
            return topBalances;
        } catch (SQLException exception) {
            return retryMysqlTopOperation(() -> doGetTopBalances(limit), exception, "Could not read top economy from MYSQL storage");
        }
    }

    @Override
    protected List<AccountBalance> doGetRegisteredAccounts() {
        List<AccountBalance> accounts = new ArrayList<>();
        try {
            PreparedStatement statement = getMysqlRegisteredAccountsStatement();
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    UUID uuid = UUID.fromString(resultSet.getString("uuid"));
                    knownAccounts.add(uuid);
                    accounts.add(new AccountBalance(uuid, resultSet.getString("player_name"), resultSet.getDouble("balance")));
                }
            }
            return accounts;
        } catch (SQLException exception) {
            return retryMysqlTopOperation(this::doGetRegisteredAccounts, exception, "Could not read registered accounts from MYSQL storage");
        }
    }

    @Override
    public void close() {
        closeMysqlResources();
        knownAccounts.clear();
    }

    private void initialize() {
        String sql = "CREATE TABLE IF NOT EXISTS economy (" +
                "uuid CHAR(36) PRIMARY KEY," +
                "player_name VARCHAR(16) DEFAULT NULL," +
                "balance DOUBLE NOT NULL DEFAULT 0" +
                ")";

        try {
            try (Statement statement = getMysqlConnection().createStatement()) {
                statement.executeUpdate(sql);
                ensurePlayerNameColumn(statement);
            }
            initializeMysqlStatements();
        } catch (Exception exception) {
            closeMysqlResources();
            throw new RuntimeException("Could not initialize MYSQL storage", exception);
        }
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
            mysqlUpdatePlayerNameStatement = connection.prepareStatement(
                    "UPDATE economy SET player_name = ? WHERE uuid = ?"
            );
            mysqlRegisteredAccountsStatement = connection.prepareStatement(
                    "SELECT uuid, player_name, balance FROM economy"
            );
            mysqlTopBalancesStatement = connection.prepareStatement(
                    "SELECT uuid, player_name, balance FROM economy ORDER BY balance DESC, uuid ASC LIMIT ?"
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
        mysqlConnection = DriverManager.getConnection(connectionInfo.url, connectionInfo.user, connectionInfo.password);
        mysqlConnection.setAutoCommit(true);
        mysqlConnection.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
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

    private PreparedStatement getMysqlUpdatePlayerNameStatement() throws SQLException {
        if (mysqlUpdatePlayerNameStatement == null || mysqlUpdatePlayerNameStatement.isClosed()) {
            initializeMysqlStatements();
        }
        return mysqlUpdatePlayerNameStatement;
    }

    private PreparedStatement getMysqlRegisteredAccountsStatement() throws SQLException {
        if (mysqlRegisteredAccountsStatement == null || mysqlRegisteredAccountsStatement.isClosed()) {
            initializeMysqlStatements();
        }
        return mysqlRegisteredAccountsStatement;
    }

    private PreparedStatement getMysqlTopBalancesStatement() throws SQLException {
        if (mysqlTopBalancesStatement == null || mysqlTopBalancesStatement.isClosed()) {
            initializeMysqlStatements();
        }
        return mysqlTopBalancesStatement;
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

            if (!query.isEmpty()) {
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

    private void ensurePlayerNameColumn(Statement statement) throws SQLException {
        try {
            statement.executeUpdate("ALTER TABLE economy ADD COLUMN player_name VARCHAR(16) DEFAULT NULL");
        } catch (SQLException exception) {
            String message = exception.getMessage();
            if (message == null || !message.toLowerCase().contains("duplicate column")) {
                throw exception;
            }
        }
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

    private List<AccountBalance> retryMysqlTopOperation(SqlTopSupplier supplier, SQLException cause, String message) {
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
            if (mysqlUpdatePlayerNameStatement != null && !mysqlUpdatePlayerNameStatement.isClosed()) {
                mysqlUpdatePlayerNameStatement.close();
            }
        } catch (SQLException ignored) {
        }

        try {
            if (mysqlTopBalancesStatement != null && !mysqlTopBalancesStatement.isClosed()) {
                mysqlTopBalancesStatement.close();
            }
        } catch (SQLException ignored) {
        }

        try {
            if (mysqlRegisteredAccountsStatement != null && !mysqlRegisteredAccountsStatement.isClosed()) {
                mysqlRegisteredAccountsStatement.close();
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
        mysqlUpdatePlayerNameStatement = null;
        mysqlRegisteredAccountsStatement = null;
        mysqlTopBalancesStatement = null;
        mysqlConnection = null;
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

    private interface SqlTopSupplier {
        List<AccountBalance> get() throws SQLException;
    }
}
