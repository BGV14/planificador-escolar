package pe.edu.planner.config;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.regex.Pattern;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.Profiles;

/** Adapts discrete Neon variables without parsing or exposing a credential-bearing URL. */
public final class NeonEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {
    private static final String[] EXPLICIT_KEYS = {
            "PLANNER_DATABASE_URL", "PLANNER_DATABASE_USERNAME", "PLANNER_DATABASE_PASSWORD"};
    private static final Pattern DNS_HOST = Pattern.compile(
            "[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?"
                    + "(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)*");

    @Override
    public int getOrder() {
        // Config data must establish active/default profiles before choosing a database.
        return ConfigDataEnvironmentPostProcessor.ORDER + 1;
    }

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (!environment.acceptsProfiles(Profiles.of("prod"))) {
            return;
        }

        boolean explicit = false;
        for (var key : EXPLICIT_KEYS) {
            explicit |= raw(environment, key) != null;
        }
        if (explicit) {
            publish(environment, "plannerLiteralDatabase", required(environment, EXPLICIT_KEYS[0]),
                    required(environment, EXPLICIT_KEYS[1]), required(environment, EXPLICIT_KEYS[2]));
            return;
        }

        var host = required(environment, "PGHOST");
        var database = required(environment, "PGDATABASE");
        var username = required(environment, "PGUSER");
        var password = required(environment, "PGPASSWORD");
        if (host.length() > 253 || !DNS_HOST.matcher(host).matches()) {
            throw invalidConfiguration();
        }

        var databaseSegment = URLEncoder.encode(database, StandardCharsets.UTF_8)
                .replace("+", "%20").replace("*", "%2A").replace(".", "%2E");
        var url = "jdbc:postgresql://" + host + "/" + databaseSegment
                + "?sslmode=verify-full&sslfactory=org.postgresql.ssl.DefaultJavaSSLFactory";
        publish(environment, "neonPostgresFallback", url, username, password);
    }

    private static void publish(ConfigurableEnvironment environment, String name,
            String url, String username, String password) {
        Map<String, Object> literals = new LinkedHashMap<>();
        var values = new String[] {url, username, password};
        var properties = new String[] {"url", "username", "password"};
        for (int index = 0; index < EXPLICIT_KEYS.length; index++) {
            // Boot's standard char[] formatter binds literal text without placeholder expansion.
            literals.put(EXPLICIT_KEYS[index], values[index].toCharArray());
            var datasourceKey = "spring.datasource." + properties[index];
            var existing = raw(environment, datasourceKey);
            // Replace only this application's YAML aliases, not externally overridden datasource values.
            if (existing == null || existing.equals("${" + EXPLICIT_KEYS[index] + "}")) {
                literals.put(datasourceKey, values[index].toCharArray());
            }
        }
        environment.getPropertySources().addFirst(new MapPropertySource(name, literals));
    }

    private static String required(ConfigurableEnvironment environment, String key) {
        var value = raw(environment, key);
        if (value == null || value.isBlank()) {
            throw invalidConfiguration();
        }
        return value;
    }

    private static String raw(ConfigurableEnvironment environment, String key) {
        for (var source : environment.getPropertySources()) {
            if (ConfigurationPropertySources.isAttachedConfigurationPropertySource(source)) {
                continue;
            }
            var value = source.getProperty(key);
            if (value instanceof String text) {
                return text;
            }
            if (value instanceof char[] characters) {
                return new String(characters);
            }
            if (value != null) {
                throw invalidConfiguration();
            }
        }
        return null;
    }

    private static IllegalStateException invalidConfiguration() {
        // Do not attach provider values or an exception containing credentials.
        return new IllegalStateException("Production database configuration is incomplete or invalid.");
    }
}
