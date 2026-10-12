package util;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

public final class EnvUtil {

    private static final Map<String, String> FILE_VARS = carregar();

    private EnvUtil() {
    }

    private static Map<String, String> carregar() {
        Map<String, String> map = new HashMap<>();
        Path path = Paths.get(".env");
        if (!Files.exists(path)) {
            return map;
        }
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                int eq = line.indexOf('=');
                if (eq > 0) {
                    String key = line.substring(0, eq).trim();
                    String val = line.substring(eq + 1).trim();
                    if ((val.startsWith("\"") && val.endsWith("\"")) || (val.startsWith("'") && val.endsWith("'"))) {
                        val = val.substring(1, val.length() - 1);
                    }
                    map.put(key, val);
                }
            }
        } catch (IOException e) {
            System.err.println("[EnvUtil] Aviso: erro ao carregar .env: " + e.getMessage());
        }
        return map;
    }

    public static String get(String key) {
        String val = System.getenv(key);
        if (val != null && !val.isBlank()) {
            return val;
        }
        return FILE_VARS.get(key);
    }

    public static String get(String key, String defaultValue) {
        String val = get(key);
        return (val != null && !val.isBlank()) ? val : defaultValue;
    }

    public static boolean getBoolean(String key, boolean defaultValue) {
        String val = get(key);
        if (val == null || val.isBlank()) {
            return defaultValue;
        }
        return Boolean.parseBoolean(val.trim());
    }
}
