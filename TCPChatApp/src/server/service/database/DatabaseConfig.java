package server.service.database;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

/**
 * Loads database credentials from env vars or local .env.
 */
public final class DatabaseConfig {
    private static final String ENV_URL = "DB_URL";
    private static final String ENV_USER = "DB_USER";
    private static final String ENV_PASSWORD = "DB_PASSWORD";
    private static final String LEGACY_ENV_URL = "SUPABASE_DB_URL";
    private static final String LEGACY_ENV_USER = "SUPABASE_DB_USER";
    private static final String LEGACY_ENV_PASSWORD = "SUPABASE_DB_PASSWORD";

    private final String url;
    private final String user;
    private final String password;
    private final DatabaseDialect dialect;

    private DatabaseConfig(String url, String user, String password) {
        this.url = url;
        this.user = user;
        this.password = password;
        this.dialect = DatabaseDialect.fromJdbcUrl(url);
    }

    public static DatabaseConfig load() {
        Map<String, String> envFile = loadDotEnv();
        String url = firstValue(envFile, ENV_URL, LEGACY_ENV_URL);
        String user = firstValue(envFile, ENV_USER, LEGACY_ENV_USER);
        String password = firstValue(envFile, ENV_PASSWORD, LEGACY_ENV_PASSWORD);
        return new DatabaseConfig(url, user, password);
    }

    public boolean isConfigured() {
        return isNotBlank(url) && isNotBlank(user) && password != null;
    }

    public String getUrl() {
        return url;
    }

    public String getUser() {
        return user;
    }

    public String getPassword() {
        return password;
    }

    public DatabaseDialect getDialect() {
        return dialect;
    }

    private static String firstValue(Map<String, String> envFile, String primaryKey, String legacyKey) {
        String envPrimary = System.getenv(primaryKey);
        if (envPrimary != null) {
            return envPrimary;
        }
        if (envFile.containsKey(primaryKey)) {
            return envFile.get(primaryKey);
        }
        String envLegacy = System.getenv(legacyKey);
        if (envLegacy != null) {
            return envLegacy;
        }
        if (envFile.containsKey(legacyKey)) {
            return envFile.get(legacyKey);
        }
        return null;
    }

    private static String getConfigValue(String key, Map<String, String> envFile) {
        String value = System.getenv(key);
        if (isNotBlank(value)) {
            return value;
        }
        return envFile.get(key);
    }

    private static Map<String, String> loadDotEnv() {
        Map<String, String> values = new HashMap<String, String>();
        File file = locateDotEnv();
        if (!file.exists()) {
            return values;
        }
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                int equalsIndex = trimmed.indexOf('=');
                if (equalsIndex <= 0) {
                    continue;
                }
                String key = trimmed.substring(0, equalsIndex).trim();
                String value = trimmed.substring(equalsIndex + 1).trim();
                values.put(key, stripQuotes(value));
            }
        } catch (IOException e) {
            System.out.println("[WARN] Cannot read .env file: " + e.getMessage());
        }
        return values;
    }

    private static File locateDotEnv() {
        for (File start : new File[] { new File(System.getProperty("user.dir")), resolveCodeSourceDir() }) {
            File current = start;
            while (current != null) {
                File candidate = new File(current, ".env");
                if (candidate.exists()) {
                    return candidate;
                }
                current = current.getParentFile();
            }
        }
        File fallback = new File(".env");
        if (fallback.exists()) {
            return fallback;
        }
        return fallback;
    }

    private static File resolveCodeSourceDir() {
        try {
            URL location = DatabaseConfig.class.getProtectionDomain().getCodeSource().getLocation();
            if (location == null) {
                return new File(System.getProperty("user.dir"));
            }
            File codeSource = new File(location.toURI());
            return codeSource.isDirectory() ? codeSource : codeSource.getParentFile();
        } catch (URISyntaxException e) {
            return new File(System.getProperty("user.dir"));
        }
    }

    private static String stripQuotes(String value) {
        if (value.length() >= 2) {
            char first = value.charAt(0);
            char last = value.charAt(value.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                return value.substring(1, value.length() - 1);
            }
        }
        return value;
    }

    private static boolean isNotBlank(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
