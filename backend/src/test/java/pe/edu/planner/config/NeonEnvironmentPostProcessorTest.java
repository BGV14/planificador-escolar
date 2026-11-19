package pe.edu.planner.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.support.SpringFactoriesLoader;

@ExtendWith(OutputCaptureExtension.class)
class NeonEnvironmentPostProcessorTest {
    private final NeonEnvironmentPostProcessor processor = new NeonEnvironmentPostProcessor();

    @Test
    void derivesTlsUrlWithoutEmbeddingCredentials() {
        var environment = environment(neon(), "prod");
        process(environment);
        assertThat(environment.getProperty("PLANNER_DATABASE_URL"))
                .isEqualTo("jdbc:postgresql://ep-example.eu-central-1.aws.neon.tech/neondb"
                        + "?sslmode=verify-full&sslfactory=org.postgresql.ssl.DefaultJavaSSLFactory")
                .doesNotContain("private-user", "private-password");
        assertThat(environment.getProperty("PLANNER_DATABASE_USERNAME")).isEqualTo("private-user");
        assertThat(environment.getProperty("PLANNER_DATABASE_PASSWORD")).isEqualTo("private-password");
    }

    @Test
    void explicitCompleteTripleWinsUntouchedEvenWithInvalidNeon() {
        var values = neon();
        values.put("PGHOST", "malformed/@host");
        values.put("PLANNER_DATABASE_URL", "jdbc:postgresql://other/db?sslmode=verify-full");
        values.put("PLANNER_DATABASE_USERNAME", "explicit-user");
        values.put("PLANNER_DATABASE_PASSWORD", " explicit-password ");
        var environment = environment(values, "prod");
        process(environment);
        for (var key : new String[] {"URL", "USERNAME", "PASSWORD"}) {
            assertThat(environment.getProperty("PLANNER_DATABASE_" + key))
                    .isEqualTo(values.get("PLANNER_DATABASE_" + key));
        }
        assertThat(environment.getPropertySources().contains("neonPostgresFallback")).isFalse();
    }

    @Test
    void rejectsEveryPartialOrBlankExplicitTripleInsteadOfFallingBack() {
        for (var key : new String[] {"URL", "USERNAME", "PASSWORD"}) {
            for (var value : new String[] {"present", "", "  "}) {
                var values = neon();
                values.put("PLANNER_DATABASE_" + key, value);
                assertInvalid(values);
            }
            var values = neon();
            values.put("PLANNER_DATABASE_URL", "jdbc:postgresql://other/db");
            values.put("PLANNER_DATABASE_USERNAME", "explicit-user");
            values.put("PLANNER_DATABASE_PASSWORD", "explicit-password");
            values.put("PLANNER_DATABASE_" + key, " ");
            assertInvalid(values);
        }
    }

    @Test
    void rejectsEveryMissingOrBlankNeonVariable() {
        for (var key : new String[] {"PGHOST", "PGDATABASE", "PGUSER", "PGPASSWORD"}) {
            var missing = neon();
            missing.remove(key);
            assertInvalid(missing);
            for (var blank : new String[] {"", " \t"}) {
                var values = neon();
                values.put(key, blank);
                assertInvalid(values);
            }
        }
    }

    @Test
    void rejectsHostInjectionAndInvalidDns() {
        for (var host : new String[] {"host:5432", "user@host", "host/db", "host?sslmode=disable",
                "host#fragment", "host\\db", "host\n", " host", "-bad.example", "bad-.example",
                "bad..example", "bad_host", ".host", "host.", "[::1]", "a".repeat(64) + ".net",
                ("a".repeat(63) + ".").repeat(4) + "net"}) {
            var values = neon();
            values.put("PGHOST", host);
            assertInvalid(values);
        }
    }

    @Test
    void encodesDatabaseAsOneUtf8PathSegment() {
        var values = neon();
        values.put("PGDATABASE", "a b/+?&=#%é");
        var environment = environment(values, "prod");
        process(environment);
        assertThat(environment.getProperty("PLANNER_DATABASE_URL"))
                .contains("/a%20b%2F%2B%3F%26%3D%23%25%C3%A9?sslmode=verify-full");
    }

    @Test
    void ignoresNonProductionEvenWithInvalidPartialConfiguration() {
        var environment = environment(Map.of("PLANNER_DATABASE_PASSWORD", "private-password"), "dev");
        process(environment);
        assertThat(environment.getProperty("PLANNER_DATABASE_URL")).isNull();
        assertThat(environment.getPropertySources().contains("neonPostgresFallback")).isFalse();
    }

    @Test
    void registrationIsDiscoverableAndRunsAfterConfigDataWithDefaultProduction() {
        assertThat(SpringFactoriesLoader.loadFactoryNames(EnvironmentPostProcessor.class, getClass().getClassLoader()))
                .contains(NeonEnvironmentPostProcessor.class.getName());
        assertThat(processor.getOrder()).isGreaterThan(ConfigDataEnvironmentPostProcessor.ORDER);
        var environment = environment(neon());
        ConfigDataEnvironmentPostProcessor.applyTo(environment);
        assertThat(environment.getActiveProfiles()).isEmpty();
        assertThat(environment.getDefaultProfiles()).containsExactly("prod");
        process(environment);
        assertThat(environment.getProperty("spring.datasource.url"))
                .isEqualTo(environment.getProperty("PLANNER_DATABASE_URL"));
        assertThat(environment.getProperty("spring.datasource.username")).isEqualTo("private-user");
        assertThat(environment.getProperty("spring.datasource.password")).isEqualTo("private-password");
    }

    @Test
    void fullProductionBindingPreservesLiteralCredentialsWithoutInterpolation(CapturedOutput output) {
        for (boolean explicit : new boolean[] {false, true}) {
            for (var literal : new String[] {"prefix${FAKE_MISSING}suffix", "prefix${FAKE_RESOLVED}suffix",
                    "prefix\\${FAKE_RESOLVED}suffix", "prefix\\\\${FAKE_MISSING}suffix", "\\path\\end",
                    "${FAKE_MISSING:fallback}", "prefix${FAKE_RESOLVED:${FAKE_MISSING}}suffix"}) {
                var values = neon();
                values.put("FAKE_RESOLVED", "must-not-interpolate");
                values.put(explicit ? "PLANNER_DATABASE_USERNAME" : "PGUSER", literal);
                values.put(explicit ? "PLANNER_DATABASE_PASSWORD" : "PGPASSWORD", literal);
                if (explicit) {
                    values.put("PLANNER_DATABASE_URL", "jdbc:postgresql://other/db?sslmode=verify-full");
                }
                var environment = environment(values);
                ConfigDataEnvironmentPostProcessor.applyTo(environment);
                process(environment);
                var properties = Binder.get(environment).bind("spring.datasource", DataSourceProperties.class).get();
                assertThat(properties.getUsername()).isEqualTo(literal);
                assertThat(properties.getPassword()).isEqualTo(literal);
                assertThat(environment.getProperty("spring.datasource.password")).isEqualTo(literal);
                assertThat(environment.getProperty("PLANNER_DATABASE_PASSWORD")).isEqualTo(literal);
                assertThat(output.getAll()).doesNotContain(literal);
            }
        }
    }

    @Test
    void invalidConfigurationNeverIncludesLiteralSecretsInExceptionChain(CapturedOutput output) {
        for (boolean explicit : new boolean[] {false, true}) {
            var values = neon();
            var secret = "fake-prefix${FAKE_SECRET_MARKER}fake-suffix";
            values.put(explicit ? "PLANNER_DATABASE_PASSWORD" : "PGPASSWORD", secret);
            if (!explicit) {
                values.put("PGHOST", "invalid/host");
            }
            assertInvalid(values);
            assertThat(output.getAll()).doesNotContain(secret);
        }
    }

    @Test
    void externallyConfiguredDatasourcePropertiesKeepTheirOriginalPrecedence() {
        var values = neon();
        values.put("spring.datasource.username", "external-user");
        values.put("spring.datasource.password", "external-password");
        var environment = environment(values);
        ConfigDataEnvironmentPostProcessor.applyTo(environment);
        process(environment);
        var properties = Binder.get(environment).bind("spring.datasource", DataSourceProperties.class).get();
        assertThat(properties.getUsername()).isEqualTo("external-user");
        assertThat(properties.getPassword()).isEqualTo("external-password");
    }

    private void assertInvalid(Map<String, Object> values) {
        assertThatThrownBy(() -> process(environment(values, "prod")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Production database configuration is incomplete or invalid.")
                .hasNoCause();
    }

    private void process(StandardEnvironment environment) {
        processor.postProcessEnvironment(environment, new SpringApplication(Object.class));
    }

    private StandardEnvironment environment(Map<String, Object> values, String... profiles) {
        var environment = new StandardEnvironment();
        environment.setConversionService(new ApplicationConversionService());
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().addFirst(new MapPropertySource("testInputs", values));
        environment.setActiveProfiles(profiles);
        return environment;
    }

    private Map<String, Object> neon() {
        return new HashMap<>(Map.of("PGHOST", "ep-example.eu-central-1.aws.neon.tech",
                "PGDATABASE", "neondb", "PGUSER", "private-user", "PGPASSWORD", "private-password"));
    }
}
