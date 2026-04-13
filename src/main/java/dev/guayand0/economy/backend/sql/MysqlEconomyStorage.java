package dev.guayand0.economy.backend.sql;

import dev.guayand0.economy.backend.AbstractStorageBackend;
import dev.guayand0.economy.type.StorageType;
import org.bukkit.plugin.Plugin;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class MysqlEconomyStorage extends AbstractStorageBackend {

    private final Object mysqlLock = new Object();
    private final Set<UUID> knownAccounts = ConcurrentHashMap.newKeySet();
    private final MysqlStorageSettings settings;
    private Connection mysqlConnection;
    private PreparedStatement mysqlSelectBalanceStatement;
    private PreparedStatement mysqlExistsStatement;
    private PreparedStatement mysqlInsertIfMissingStatement;
    private PreparedStatement mysqlWriteBalanceStatement;
    private PreparedStatement mysqlUpdatePlayerNameStatement;
    private PreparedStatement mysqlRegisteredAccountsStatement;
    private PreparedStatement mysqlRegisteredAccountCountStatement;
    private PreparedStatement mysqlTopBalancesStatement;
    private final Map<String, PreparedStatement> mysqlBalanceStatementsByTable = new ConcurrentHashMap<>();
    private final Map<String, PreparedStatement> mysqlTopStatementsByTable = new ConcurrentHashMap<>();
    private volatile long lastLookupFailureLogAt;
    private volatile boolean mysqlLookupUnavailable;

    public MysqlEconomyStorage(Plugin plugin) {
        super(plugin);
        this.settings = MysqlStorageSettings.fromConfig(plugin);
        initialize();
    }

    @Override
    public StorageType getType() {
        return StorageType.MYSQL;
    }

    @Override
    protected boolean doHasAccount(UUID uuid) {
        synchronized (mysqlLock) {
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
    }

    @Override
    protected boolean doCreateAccount(UUID uuid) {
        synchronized (mysqlLock) {
            if (knownAccounts.contains(uuid) || doHasAccount(uuid)) {
                return false;
            }

            try {
                PreparedStatement statement = getMysqlInsertIfMissingStatement();
                statement.setString(1, uuid.toString());
                int rows = statement.executeUpdate();
                knownAccounts.add(uuid);
                return rows > 0;
            } catch (SQLException exception) {
                return retryMysqlBooleanOperation(() -> doCreateAccount(uuid), exception, "Could not create player economy account in MYSQL storage");
            }
        }
    }

    @Override
    protected double doGetBalance(UUID uuid) {
        synchronized (mysqlLock) {
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
    }

    @Override
    protected void doSetBalance(UUID uuid, double amount) {
        synchronized (mysqlLock) {
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
    }

    @Override
    protected void doUpdatePlayerName(UUID uuid, String playerName) {
        synchronized (mysqlLock) {
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
    }

    @Override
    protected List<AccountBalance> doGetTopBalances(int limit) {
        synchronized (mysqlLock) {
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
    }

    @Override
    protected List<AccountBalance> doGetRegisteredAccounts() {
        synchronized (mysqlLock) {
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
    }

    @Override
    protected int doGetRegisteredAccountCount() {
        synchronized (mysqlLock) {
            try {
                PreparedStatement statement = getMysqlRegisteredAccountCountStatement();
                try (ResultSet resultSet = statement.executeQuery()) {
                    return resultSet.next() ? resultSet.getInt(1) : 0;
                }
            } catch (SQLException exception) {
                try {
                    resetMysqlConnection();
                    PreparedStatement statement = getMysqlRegisteredAccountCountStatement();
                    try (ResultSet resultSet = statement.executeQuery()) {
                        return resultSet.next() ? resultSet.getInt(1) : 0;
                    }
                } catch (SQLException retryException) {
                    retryException.addSuppressed(exception);
                    logLookupFailure("registered account count", settings.getTableName(), retryException);
                    return 0;
                }
            }
        }
    }

    @Override
    public void close() {
        synchronized (mysqlLock) {
            closeMysqlResources();
            knownAccounts.clear();
        }
    }

    public int getActiveConnectionCount() {
        synchronized (mysqlLock) {
            try {
                return mysqlConnection != null && !mysqlConnection.isClosed() ? 1 : 0;
            } catch (SQLException exception) {
                return 0;
            }
        }
    }

    public double getBalanceFromTable(String tableName, UUID uuid) {
        String normalizedTableName = MysqlStorageSettings.forTable(plugin, tableName).getTableName();
        synchronized (mysqlLock) {
            try {
                PreparedStatement statement = getMysqlBalanceStatementForTable(normalizedTableName);
                statement.setString(1, uuid.toString());
                try (ResultSet resultSet = statement.executeQuery()) {
                    markLookupHealthy();
                    return resultSet.next() ? resultSet.getDouble("balance") : 0.0D;
                }
            } catch (SQLException exception) {
                resetMysqlConnection();
                logLookupFailure("balance lookup", normalizedTableName, exception);
                return 0.0D;
            }
        }
    }

    public List<MysqlTopEntry> getTopBalancesFromTable(String tableName, int limit) {
        String normalizedTableName = MysqlStorageSettings.forTable(plugin, tableName).getTableName();
        synchronized (mysqlLock) {
            if (limit <= 0) {
                return new ArrayList<>();
            }

            List<MysqlTopEntry> topBalances = new ArrayList<>();
            try {
                PreparedStatement statement = getMysqlTopStatementForTable(normalizedTableName);
                statement.setInt(1, limit);
                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        topBalances.add(new MysqlTopEntry(
                                resultSet.getString("player_name"),
                                resultSet.getDouble("balance")
                        ));
                    }
                }
                markLookupHealthy();
                return topBalances;
            } catch (SQLException exception) {
                resetMysqlConnection();
                logLookupFailure("top lookup", normalizedTableName, exception);
                return topBalances;
            }
        }
    }

    private void initialize() {
        String sql = "CREATE TABLE IF NOT EXISTS " + settings.getTableName() + " (" +
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
            String tableName = settings.getTableName();
            mysqlExistsStatement = connection.prepareStatement("SELECT 1 FROM " + tableName + " WHERE uuid = ? LIMIT 1");
            mysqlSelectBalanceStatement = connection.prepareStatement("SELECT balance FROM " + tableName + " WHERE uuid = ? LIMIT 1");
            mysqlInsertIfMissingStatement = connection.prepareStatement(
                    "INSERT INTO " + tableName + " (uuid, balance) VALUES (?, 0) " +
                            "ON DUPLICATE KEY UPDATE uuid = uuid"
            );
            mysqlWriteBalanceStatement = connection.prepareStatement(
                    "INSERT INTO " + tableName + " (uuid, balance) VALUES (?, ?) " +
                            "ON DUPLICATE KEY UPDATE balance = VALUES(balance)"
            );
            mysqlUpdatePlayerNameStatement = connection.prepareStatement(
                    "UPDATE " + tableName + " SET player_name = ? WHERE uuid = ?"
            );
            mysqlRegisteredAccountsStatement = connection.prepareStatement(
                    "SELECT uuid, player_name, balance FROM " + tableName
            );
            mysqlRegisteredAccountCountStatement = connection.prepareStatement(
                    "SELECT COUNT(*) FROM " + tableName
            );
            mysqlTopBalancesStatement = connection.prepareStatement(
                    "SELECT uuid, player_name, balance FROM " + tableName + " ORDER BY balance DESC, uuid ASC LIMIT ?"
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
        mysqlConnection = DriverManager.getConnection(settings.getJdbcUrl(), settings.getUser(), settings.getPassword());
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

    private PreparedStatement getMysqlRegisteredAccountCountStatement() throws SQLException {
        if (mysqlRegisteredAccountCountStatement == null || mysqlRegisteredAccountCountStatement.isClosed()) {
            initializeMysqlStatements();
        }
        return mysqlRegisteredAccountCountStatement;
    }

    private PreparedStatement getMysqlTopBalancesStatement() throws SQLException {
        if (mysqlTopBalancesStatement == null || mysqlTopBalancesStatement.isClosed()) {
            initializeMysqlStatements();
        }
        return mysqlTopBalancesStatement;
    }

    private PreparedStatement getMysqlBalanceStatementForTable(String tableName) throws SQLException {
        PreparedStatement statement = mysqlBalanceStatementsByTable.get(tableName);
        if (statement != null && !statement.isClosed()) {
            return statement;
        }

        PreparedStatement createdStatement = getMysqlConnection().prepareStatement(
                "SELECT balance FROM " + tableName + " WHERE uuid = ? LIMIT 1"
        );
        mysqlBalanceStatementsByTable.put(tableName, createdStatement);
        return createdStatement;
    }

    private PreparedStatement getMysqlTopStatementForTable(String tableName) throws SQLException {
        PreparedStatement statement = mysqlTopStatementsByTable.get(tableName);
        if (statement != null && !statement.isClosed()) {
            return statement;
        }

        PreparedStatement createdStatement = getMysqlConnection().prepareStatement(
                "SELECT player_name, balance FROM " + tableName + " ORDER BY balance DESC, uuid ASC LIMIT ?"
        );
        mysqlTopStatementsByTable.put(tableName, createdStatement);
        return createdStatement;
    }

    private void resetMysqlConnection() {
        closeMysqlResources();
    }

    private void logLookupFailure(String operation, String tableName, SQLException exception) {
        long now = System.currentTimeMillis();
        long cooldownMillis = TimeUnit.SECONDS.toMillis(30);
        if (now - lastLookupFailureLogAt < cooldownMillis) {
            return;
        }

        lastLookupFailureLogAt = now;
        mysqlLookupUnavailable = true;
        plugin.getLogger().warning("MYSQL " + operation + " failed for table '" + tableName + "'. Returning fallback values until the connection recovers: " + exception.getMessage());
    }

    private void markLookupHealthy() {
        if (!mysqlLookupUnavailable) {
            return;
        }

        mysqlLookupUnavailable = false;
        plugin.getLogger().info("MYSQL lookups recovered successfully.");
    }

    private void ensurePlayerNameColumn(Statement statement) throws SQLException {
        try {
            statement.executeUpdate("ALTER TABLE " + settings.getTableName() + " ADD COLUMN player_name VARCHAR(16) DEFAULT NULL");
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
            logLookupFailure(message, settings.getTableName(), retryException);
            return 0.0D;
        }
    }

    private boolean retryMysqlBooleanOperation(SqlBooleanSupplier supplier, SQLException cause, String message) {
        try {
            resetMysqlConnection();
            return supplier.get();
        } catch (SQLException retryException) {
            retryException.addSuppressed(cause);
            logLookupFailure(message, settings.getTableName(), retryException);
            return false;
        }
    }

    private List<AccountBalance> retryMysqlTopOperation(SqlTopSupplier supplier, SQLException cause, String message) {
        try {
            resetMysqlConnection();
            return supplier.get();
        } catch (SQLException retryException) {
            retryException.addSuppressed(cause);
            logLookupFailure(message, settings.getTableName(), retryException);
            return new ArrayList<>();
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
        closeStatementMap(mysqlBalanceStatementsByTable);
        closeStatementMap(mysqlTopStatementsByTable);

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
            if (mysqlRegisteredAccountCountStatement != null && !mysqlRegisteredAccountCountStatement.isClosed()) {
                mysqlRegisteredAccountCountStatement.close();
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
        mysqlRegisteredAccountCountStatement = null;
        mysqlTopBalancesStatement = null;
        mysqlBalanceStatementsByTable.clear();
        mysqlTopStatementsByTable.clear();
        mysqlConnection = null;
    }

    private void closeStatementMap(Map<String, PreparedStatement> statements) {
        for (PreparedStatement statement : statements.values()) {
            try {
                if (statement != null && !statement.isClosed()) {
                    statement.close();
                }
            } catch (SQLException ignored) {
            }
        }
    }

    public static final class MysqlTopEntry {
        private final String playerName;
        private final double balance;

        public MysqlTopEntry(String playerName, double balance) {
            this.playerName = playerName;
            this.balance = balance;
        }

        public String getPlayerName() {
            return playerName;
        }

        public double getBalance() {
            return balance;
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
