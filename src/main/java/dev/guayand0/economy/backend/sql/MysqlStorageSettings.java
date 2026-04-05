package dev.guayand0.economy.backend.sql;

import org.bukkit.plugin.Plugin;

import java.net.URI;
import java.util.Locale;
import java.util.regex.Pattern;

public final class MysqlStorageSettings {

    private static final String DEFAULT_MYSQL_CONNECTION_PARAMS =
            "cachePrepStmts=true&prepStmtCacheSize=250&prepStmtCacheSqlLimit=2048" +
                    "&useServerPrepStmts=true&useLocalSessionState=true" +
                    "&cacheResultSetMetadata=true&cacheServerConfiguration=true" +
                    "&elideSetAutoCommits=true&maintainTimeStats=false&tcpKeepAlive=true";

    private static final Pattern VALID_TABLE_NAME = Pattern.compile("[A-Za-z0-9_]+");

    private final String tableName;
    private final String jdbcUrl;
    private final String user;
    private final String password;

    private MysqlStorageSettings(String tableName, String jdbcUrl, String user, String password) {
        this.tableName = tableName;
        this.jdbcUrl = jdbcUrl;
        this.user = user;
        this.password = password;
    }

    public static MysqlStorageSettings fromConfig(Plugin plugin) {
        String tableName = sanitizeTableName(plugin.getConfig().getString("config.storage.table", "economy"));
        MysqlConnectionInfo connectionInfo = buildMysqlConnectionInfo(plugin);
        return new MysqlStorageSettings(tableName, connectionInfo.url, connectionInfo.user, connectionInfo.password);
    }

    public static MysqlStorageSettings forTable(Plugin plugin, String tableName) {
        MysqlConnectionInfo connectionInfo = buildMysqlConnectionInfo(plugin);
        return new MysqlStorageSettings(
                sanitizeTableName(tableName),
                connectionInfo.url,
                connectionInfo.user,
                connectionInfo.password
        );
    }

    public String getTableName() {
        return tableName;
    }

    public String getJdbcUrl() {
        return jdbcUrl;
    }

    public String getUser() {
        return user;
    }

    public String getPassword() {
        return password;
    }

    private static String sanitizeTableName(String configuredTableName) {
        String tableName = configuredTableName == null ? "" : configuredTableName.trim();
        if (tableName.isEmpty()) {
            return "economy";
        }

        if (!VALID_TABLE_NAME.matcher(tableName).matches()) {
            throw new IllegalArgumentException("Invalid MYSQL table name: " + configuredTableName);
        }

        return tableName.toLowerCase(Locale.ROOT);
    }

    private static MysqlConnectionInfo buildMysqlConnectionInfo(Plugin plugin) {
        String mysqlUri = plugin.getConfig().getString("config.storage.mysql-uri", "").trim();
        if (!mysqlUri.isEmpty()) {
            if (mysqlUri.startsWith("jdbc:mysql://")) {
                return new MysqlConnectionInfo(applyDefaultMysqlParams(mysqlUri), getMysqlUser(plugin), getMysqlPassword(plugin));
            }

            if (mysqlUri.startsWith("mysql://")) {
                return buildMysqlConnectionInfoFromUri(plugin, mysqlUri);
            }

            return new MysqlConnectionInfo(
                    applyDefaultMysqlParams("jdbc:mysql://" + mysqlUri),
                    getMysqlUser(plugin),
                    getMysqlPassword(plugin)
            );
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

        return new MysqlConnectionInfo(builder.toString(), getMysqlUser(plugin), getMysqlPassword(plugin));
    }

    private static MysqlConnectionInfo buildMysqlConnectionInfoFromUri(Plugin plugin, String mysqlUri) {
        try {
            URI uri = URI.create(mysqlUri);
            String userInfo = uri.getUserInfo();
            String user = getMysqlUser(plugin);
            String password = getMysqlPassword(plugin);

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

    private static String getMysqlUser(Plugin plugin) {
        return plugin.getConfig().getString("config.storage.user", "root");
    }

    private static String getMysqlPassword(Plugin plugin) {
        return plugin.getConfig().getString("config.storage.password", "");
    }

    private static String mergeMysqlConnectionParams(String rawParams) {
        if (rawParams == null || rawParams.trim().isEmpty()) {
            return DEFAULT_MYSQL_CONNECTION_PARAMS;
        }

        String params = rawParams.startsWith("?") ? rawParams.substring(1) : rawParams;
        if (params.isEmpty()) {
            return DEFAULT_MYSQL_CONNECTION_PARAMS;
        }

        return params + "&" + DEFAULT_MYSQL_CONNECTION_PARAMS;
    }

    private static String applyDefaultMysqlParams(String jdbcUrl) {
        String separator = jdbcUrl.contains("?") ? "&" : "?";
        return jdbcUrl + separator + DEFAULT_MYSQL_CONNECTION_PARAMS;
    }

    static final class MysqlConnectionInfo {
        private final String url;
        private final String user;
        private final String password;

        private MysqlConnectionInfo(String url, String user, String password) {
            this.url = url;
            this.user = user;
            this.password = password;
        }
    }
}
