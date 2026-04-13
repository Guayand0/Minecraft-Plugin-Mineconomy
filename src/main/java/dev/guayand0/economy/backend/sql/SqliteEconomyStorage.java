package dev.guayand0.economy.backend.sql;

import dev.guayand0.economy.backend.AbstractStorageBackend;
import dev.guayand0.economy.type.StorageType;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class SqliteEconomyStorage extends AbstractStorageBackend {

    private Connection sqliteConnection;
    private PreparedStatement sqliteExistsStatement;
    private PreparedStatement sqliteSelectBalanceStatement;
    private PreparedStatement sqliteWriteBalanceStatement;
    private PreparedStatement sqliteUpdatePlayerNameStatement;
    private PreparedStatement sqliteRegisteredAccountsStatement;
    private PreparedStatement sqliteRegisteredAccountCountStatement;
    private PreparedStatement sqliteTopBalancesStatement;

    public SqliteEconomyStorage(Plugin plugin) {
        super(plugin);
        ensureFolderExists(getTypeFolder());
        initialize();
    }

    @Override
    public StorageType getType() {
        return StorageType.SQLITE;
    }

    @Override
    protected boolean doHasAccount(UUID uuid) {
        try {
            PreparedStatement statement = getSqliteExistsStatement();
            statement.setString(1, uuid.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        } catch (SQLException exception) {
            resetSqliteConnection();
            throw new RuntimeException("Could not check player economy in SQLITE storage", exception);
        }
    }

    @Override
    protected boolean doCreateAccount(UUID uuid) {
        if (doHasAccount(uuid)) {
            return false;
        }

        doSetBalance(uuid, 0.0D);
        return true;
    }

    @Override
    protected double doGetBalance(UUID uuid) {
        try {
            PreparedStatement statement = getSqliteSelectBalanceStatement();
            statement.setString(1, uuid.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getDouble("balance");
                }
            }
        } catch (SQLException exception) {
            resetSqliteConnection();
            throw new RuntimeException("Could not read player economy from SQLITE storage", exception);
        }

        doSetBalance(uuid, 0.0D);
        return 0.0D;
    }

    @Override
    protected void doSetBalance(UUID uuid, double amount) {
        try {
            PreparedStatement statement = getSqliteWriteBalanceStatement();
            statement.setString(1, uuid.toString());
            statement.setDouble(2, amount);
            statement.executeUpdate();
        } catch (SQLException exception) {
            resetSqliteConnection();
            throw new RuntimeException("Could not save player economy to SQLITE storage", exception);
        }
    }

    @Override
    protected void doUpdatePlayerName(UUID uuid, String playerName) {
        try {
            PreparedStatement statement = getSqliteUpdatePlayerNameStatement();
            statement.setString(1, playerName);
            statement.setString(2, uuid.toString());
            statement.executeUpdate();
        } catch (SQLException exception) {
            resetSqliteConnection();
            throw new RuntimeException("Could not update player name in SQLITE storage", exception);
        }
    }

    @Override
    protected List<AccountBalance> doGetTopBalances(int limit) {
        if (limit <= 0) {
            return new ArrayList<>();
        }

        List<AccountBalance> topBalances = new ArrayList<>();
        try {
            PreparedStatement statement = getSqliteTopBalancesStatement();
            statement.setInt(1, limit);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    topBalances.add(new AccountBalance(
                            UUID.fromString(resultSet.getString("uuid")),
                            resultSet.getString("player_name"),
                            resultSet.getDouble("balance")
                    ));
                }
            }
            return topBalances;
        } catch (SQLException exception) {
            resetSqliteConnection();
            throw new RuntimeException("Could not read top economy from SQLITE storage", exception);
        }
    }

    @Override
    public void close() {
        closeSqliteResources();
    }

    @Override
    protected List<AccountBalance> doGetRegisteredAccounts() {
        List<AccountBalance> accounts = new ArrayList<>();
        try {
            PreparedStatement statement = getSqliteRegisteredAccountsStatement();
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    accounts.add(new AccountBalance(
                            UUID.fromString(resultSet.getString("uuid")),
                            resultSet.getString("player_name"),
                            resultSet.getDouble("balance")
                    ));
                }
            }
            return accounts;
        } catch (SQLException exception) {
            resetSqliteConnection();
            throw new RuntimeException("Could not read registered accounts from SQLITE storage", exception);
        }
    }

    @Override
    protected int doGetRegisteredAccountCount() {
        try {
            PreparedStatement statement = getSqliteRegisteredAccountCountStatement();
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getInt(1) : 0;
            }
        } catch (SQLException exception) {
            resetSqliteConnection();
            throw new RuntimeException("Could not count registered accounts from SQLITE storage", exception);
        }
    }

    private void initialize() {
        String sql = "CREATE TABLE IF NOT EXISTS economy (" +
                "uuid CHAR(36) PRIMARY KEY," +
                "player_name TEXT DEFAULT NULL," +
                "balance DOUBLE NOT NULL DEFAULT 0" +
                ")";

        try {
            try (Statement statement = getSqliteConnection().createStatement()) {
                statement.executeUpdate(sql);
                ensurePlayerNameColumn(statement);
            }
            initializeSqliteStatements();
        } catch (Exception exception) {
            closeSqliteResources();
            throw new RuntimeException("Could not initialize SQLITE storage", exception);
        }
    }

    private void initializeSqliteStatements() {
        try {
            Connection connection = getSqliteConnection();
            sqliteExistsStatement = connection.prepareStatement("SELECT 1 FROM economy WHERE uuid = ? LIMIT 1");
            sqliteSelectBalanceStatement = connection.prepareStatement("SELECT balance FROM economy WHERE uuid = ? LIMIT 1");
            sqliteWriteBalanceStatement = connection.prepareStatement(
                    "INSERT INTO economy (uuid, balance) VALUES (?, ?) " +
                            "ON CONFLICT(uuid) DO UPDATE SET balance = excluded.balance"
            );
            sqliteUpdatePlayerNameStatement = connection.prepareStatement(
                    "UPDATE economy SET player_name = ? WHERE uuid = ?"
            );
            sqliteRegisteredAccountsStatement = connection.prepareStatement(
                    "SELECT uuid, player_name, balance FROM economy"
            );
            sqliteRegisteredAccountCountStatement = connection.prepareStatement(
                    "SELECT COUNT(*) FROM economy"
            );
            sqliteTopBalancesStatement = connection.prepareStatement(
                    "SELECT uuid, player_name, balance FROM economy ORDER BY balance DESC, uuid ASC LIMIT ?"
            );
        } catch (SQLException exception) {
            throw new RuntimeException("Could not initialize SQLITE prepared statements", exception);
        }
    }

    private Connection getSqliteConnection() throws SQLException {
        if (sqliteConnection != null && !sqliteConnection.isClosed()) {
            return sqliteConnection;
        }

        closeSqliteResources();
        sqliteConnection = DriverManager.getConnection("jdbc:sqlite:" + getSqliteFile().getAbsolutePath());
        sqliteConnection.setAutoCommit(true);

        try (Statement statement = sqliteConnection.createStatement()) {
            statement.execute("PRAGMA journal_mode=WAL");
            statement.execute("PRAGMA synchronous=NORMAL");
            statement.execute("PRAGMA temp_store=MEMORY");
            statement.execute("PRAGMA cache_size=-20000");
            statement.execute("PRAGMA busy_timeout=5000");
        }

        return sqliteConnection;
    }

    private PreparedStatement getSqliteExistsStatement() throws SQLException {
        if (sqliteExistsStatement == null || sqliteExistsStatement.isClosed()) {
            initializeSqliteStatements();
        }
        return sqliteExistsStatement;
    }

    private PreparedStatement getSqliteSelectBalanceStatement() throws SQLException {
        if (sqliteSelectBalanceStatement == null || sqliteSelectBalanceStatement.isClosed()) {
            initializeSqliteStatements();
        }
        return sqliteSelectBalanceStatement;
    }

    private PreparedStatement getSqliteWriteBalanceStatement() throws SQLException {
        if (sqliteWriteBalanceStatement == null || sqliteWriteBalanceStatement.isClosed()) {
            initializeSqliteStatements();
        }
        return sqliteWriteBalanceStatement;
    }

    private PreparedStatement getSqliteUpdatePlayerNameStatement() throws SQLException {
        if (sqliteUpdatePlayerNameStatement == null || sqliteUpdatePlayerNameStatement.isClosed()) {
            initializeSqliteStatements();
        }
        return sqliteUpdatePlayerNameStatement;
    }

    private PreparedStatement getSqliteRegisteredAccountsStatement() throws SQLException {
        if (sqliteRegisteredAccountsStatement == null || sqliteRegisteredAccountsStatement.isClosed()) {
            initializeSqliteStatements();
        }
        return sqliteRegisteredAccountsStatement;
    }

    private PreparedStatement getSqliteRegisteredAccountCountStatement() throws SQLException {
        if (sqliteRegisteredAccountCountStatement == null || sqliteRegisteredAccountCountStatement.isClosed()) {
            initializeSqliteStatements();
        }
        return sqliteRegisteredAccountCountStatement;
    }

    private PreparedStatement getSqliteTopBalancesStatement() throws SQLException {
        if (sqliteTopBalancesStatement == null || sqliteTopBalancesStatement.isClosed()) {
            initializeSqliteStatements();
        }
        return sqliteTopBalancesStatement;
    }

    private void resetSqliteConnection() {
        closeSqliteResources();
    }

    private void ensurePlayerNameColumn(Statement statement) throws SQLException {
        try {
            statement.execute("ALTER TABLE economy ADD COLUMN player_name TEXT DEFAULT NULL");
        } catch (SQLException exception) {
            String message = exception.getMessage();
            if (message == null || !message.toLowerCase().contains("duplicate column")) {
                throw exception;
            }
        }
    }

    private void closeSqliteResources() {
        try {
            if (sqliteExistsStatement != null && !sqliteExistsStatement.isClosed()) {
                sqliteExistsStatement.close();
            }
        } catch (SQLException ignored) {
        }

        try {
            if (sqliteSelectBalanceStatement != null && !sqliteSelectBalanceStatement.isClosed()) {
                sqliteSelectBalanceStatement.close();
            }
        } catch (SQLException ignored) {
        }

        try {
            if (sqliteWriteBalanceStatement != null && !sqliteWriteBalanceStatement.isClosed()) {
                sqliteWriteBalanceStatement.close();
            }
        } catch (SQLException ignored) {
        }

        try {
            if (sqliteUpdatePlayerNameStatement != null && !sqliteUpdatePlayerNameStatement.isClosed()) {
                sqliteUpdatePlayerNameStatement.close();
            }
        } catch (SQLException ignored) {
        }

        try {
            if (sqliteRegisteredAccountsStatement != null && !sqliteRegisteredAccountsStatement.isClosed()) {
                sqliteRegisteredAccountsStatement.close();
            }
        } catch (SQLException ignored) {
        }

        try {
            if (sqliteRegisteredAccountCountStatement != null && !sqliteRegisteredAccountCountStatement.isClosed()) {
                sqliteRegisteredAccountCountStatement.close();
            }
        } catch (SQLException ignored) {
        }

        try {
            if (sqliteTopBalancesStatement != null && !sqliteTopBalancesStatement.isClosed()) {
                sqliteTopBalancesStatement.close();
            }
        } catch (SQLException ignored) {
        }

        try {
            if (sqliteConnection != null && !sqliteConnection.isClosed()) {
                sqliteConnection.close();
            }
        } catch (SQLException ignored) {
        }

        sqliteExistsStatement = null;
        sqliteSelectBalanceStatement = null;
        sqliteWriteBalanceStatement = null;
        sqliteUpdatePlayerNameStatement = null;
        sqliteRegisteredAccountsStatement = null;
        sqliteRegisteredAccountCountStatement = null;
        sqliteTopBalancesStatement = null;
        sqliteConnection = null;
    }

    private File getSqliteFile() {
        return new File(getTypeFolder(), "mineconomy.db");
    }
}
