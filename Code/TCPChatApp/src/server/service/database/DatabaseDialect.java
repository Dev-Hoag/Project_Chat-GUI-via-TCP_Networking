package server.service.database;

public enum DatabaseDialect {
    MYSQL,
    POSTGRESQL;

    public static DatabaseDialect fromJdbcUrl(String url) {
        if (url != null && url.toLowerCase().contains(":mysql:")) {
            return MYSQL;
        }
        return POSTGRESQL;
    }
}
