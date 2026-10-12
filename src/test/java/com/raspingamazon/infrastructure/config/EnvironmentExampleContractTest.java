package com.raspingamazon.infrastructure.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * Regression contract between the example environment file and the existing
 * infrastructure configuration providers. No real credentials are loaded.
 */
class EnvironmentExampleContractTest {

    private static final Path EXAMPLE = Path.of(".env.example");

    private static final Path CONFIG_SOURCES = Path.of(
        "src", "main", "java", "com", "raspingamazon",
        "infrastructure", "config"
    );

    private static final Pattern ENV_KEY = Pattern.compile(
        "(?:APP|DB|AMAZON|PROCESSING|PUBLICATION|TELEGRAM|WHATSAPP|ALERT)_[A-Z0-9_]+"
    );

    private static final Pattern SOURCE_LITERAL = Pattern.compile(
        "\"((?:APP|DB|AMAZON|PROCESSING|PUBLICATION|TELEGRAM|WHATSAPP|ALERT)_[A-Z0-9_]+)\""
    );

    @Test
    void shouldDocumentEveryEnvironmentKeyDeclaredInConfigurationProviders()
        throws IOException {

        Set<String> declared = new HashSet<>();

        try (Stream<Path> sources = Files.list(CONFIG_SOURCES)) {
            for (Path source : sources.filter(
                path -> path.getFileName().toString().endsWith("Provider.java")
            ).toList()) {
                String javaSource = Files.readString(source, StandardCharsets.UTF_8);
                Matcher matcher = SOURCE_LITERAL.matcher(javaSource);

                while (matcher.find()) {
                    declared.add(matcher.group(1));
                }
            }
        }

        ParsedExample example = parseExample();

        assertFalse(declared.isEmpty());
        Set<String> undocumented = new HashSet<>(declared);
        undocumented.removeAll(example.documentedKeys());

        assertTrue(undocumented.isEmpty(),
            "Configuration provider keys missing from .env.example: "
                + undocumented);
    }

    @Test
    void shouldLoadNonChannelEnvironmentProfilesFromDocumentedValues()
        throws IOException {

        Map<String, String> values = parseExample().activeValues();

        ContinuousProcessingEnvironmentConfig continuous = assertDoesNotThrow(
            () -> ContinuousProcessingEnvironmentConfigProvider.load(
                values, "test-instance"
            )
        );

        assertEquals(Duration.ofHours(1), continuous.initialScheduleInterval());
        assertDoesNotThrow(() -> ContinuousProcessingRecoveryEnvironmentConfigProvider.load(values));
        assertDoesNotThrow(() -> PublicationAutomationPolicyEnvironmentConfigProvider.load(values));
        assertDoesNotThrow(() -> PublicationOutboxRetryConfigProvider.load(values));
        assertDoesNotThrow(() -> PublicationRateLimitConfigProvider.load(values));
        assertDoesNotThrow(() -> PublicationChannelActivationConfigProvider.load(values));
    }

    @Test
    void shouldKeepCredentialsAsPlaceholdersAndAlertThresholdsUnconfigured()
        throws IOException {

        ParsedExample example = parseExample();
        Map<String, String> values = example.activeValues();

        assertEquals("CHANGE_ME", values.get("DB_PASSWORD"));
        assertEquals("CHANGE_ME", values.get("AMAZON_ASSOCIATE_TAG"));
        assertEquals("CHANGE_ME", values.get("TELEGRAM_BOT_TOKEN"));
        assertEquals("CHANGE_ME", values.get("WHATSAPP_ACCESS_TOKEN"));
        assertEquals("CHANGE_ME", values.get("WHATSAPP_PHONE_NUMBER_ID"));

        assertEquals("false", values.get("TELEGRAM_ENABLED"));
        assertEquals("false", values.get("WHATSAPP_MANUAL_ENABLED"));
        assertEquals("false", values.get("WHATSAPP_ENABLED"));

        for (String alertKey : Set.of(
            "ALERT_REPEATED_EXTERNAL_FAILURE_THRESHOLD",
            "ALERT_REPEATED_EXTERNAL_FAILURE_WINDOW_SECONDS",
            "ALERT_SUSPICIOUS_COLLECTION_LOOKBACK_RUNS",
            "ALERT_SUSPICIOUS_COLLECTION_DROP_FRACTION",
            "ALERT_SUSPICIOUS_COLLECTION_MINIMUM_BASELINE_CANDIDATES"
        )) {
            assertTrue(example.documentedKeys().contains(alertKey));
            assertFalse(values.containsKey(alertKey),
                "Alert thresholds must not acquire undocumented runtime defaults");
        }

        assertFalse(values.containsKey("PROCESSING_SCHEDULER_INSTANCE_ID"));
        assertFalse(values.containsKey("PROCESSING_WORKER_ID"));
    }

    private static ParsedExample parseExample() throws IOException {
        Map<String, String> active = new HashMap<>();
        Set<String> documented = new HashSet<>();

        for (String sourceLine : Files.readAllLines(EXAMPLE, StandardCharsets.UTF_8)) {
            String line = sourceLine.strip();
            if (line.isEmpty()) {
                continue;
            }

            boolean commented = line.startsWith("#");
            if (commented) {
                line = line.substring(1).strip();
            }

            int separator = line.indexOf('=');
            if (separator < 0) {
                continue;
            }

            String key = line.substring(0, separator).strip();
            if (!ENV_KEY.matcher(key).matches()) {
                continue;
            }

            assertTrue(documented.add(key), "Duplicated key in .env.example: " + key);

            if (!commented) {
                String value = line.substring(separator + 1).strip();
                assertFalse(value.isEmpty(), "Blank active value: " + key);
                active.put(key, value);
            }
        }

        return new ParsedExample(Map.copyOf(active), Set.copyOf(documented));
    }

    private record ParsedExample(
        Map<String, String> activeValues,
        Set<String> documentedKeys
    ) {
    }
}
