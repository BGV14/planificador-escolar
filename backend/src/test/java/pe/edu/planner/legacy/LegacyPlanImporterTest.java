package pe.edu.planner.legacy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LegacyPlanImporterTest {
    private static final String A = "Synthetic Math|1|0";
    private static final String B = "Synthetic Geometry|2|0";
    private final LegacyPlanImporter importer = new LegacyPlanImporter(Set.of(A, B), 2026, 6);

    @ParameterizedTest
    @ValueSource(strings = {"raw.json", "v1.json", "v2.json", "wrapper.json"})
    void acceptsLegacyShapesAndNormalizesSeptember(String fixture) throws IOException {
        var result = importer.preview(fixture(fixture), LegacyPlanState.empty());
        assertThat(result.state().assignments()).containsEntry(A, "Setiembre");
        assertThat(result.preview().fields().get("assignments").incoming()).isEqualTo(1);
        assertThat(result.preview().affectedTopics()).isEqualTo(1);
    }

    @Test
    void mergesMapsIndependentlyAndPreservesEmptyValues() {
        var existing = new LegacyPlanState(Map.of(A, "Marzo", B, "Abril"),
                Map.of(A, "Trabajado", B, "Trabajado"), Map.of(A, "2026-03-05", B, "2026-04-03"),
                Map.of(A, "original", B, "untouched"));
        var result = importer.preview("""
                {"assignments":{"Synthetic Math|1|0":""},"status":{"Synthetic Math|1|0":""},
                 "dates":{"Synthetic Math|1|0":""},"notes":{"Synthetic Math|1|0":""}}
                """, existing);
        assertThat(result.state().assignments()).containsEntry(A, "").containsEntry(B, "Abril");
        assertThat(result.state().status()).containsEntry(A, "").containsEntry(B, "Trabajado");
        assertThat(result.state().dates()).containsEntry(A, "").containsEntry(B, "2026-04-03");
        assertThat(result.state().notes()).containsEntry(A, "").containsEntry(B, "untouched");
        assertThat(existing.notes()).containsEntry(A, "original");
        assertThat(result.preview().fields().values()).allSatisfy(counts -> {
            assertThat(counts.updated()).isEqualTo(1);
            assertThat(counts.explicitEmpty()).isEqualTo(1);
        });
    }

    @Test
    void cumulativeImportsKeepOtherCoursesAndUnmentionedMaps() {
        var first = importer.preview("{\"assignments\":{\"" + A + "\":\"Marzo\"}}", LegacyPlanState.empty());
        var second = importer.preview("{\"notes\":{\"" + B + "\":\"  exact note  \"}}", first.state());
        assertThat(second.state().assignments()).containsExactlyEntriesOf(first.state().assignments());
        assertThat(second.state().notes()).containsEntry(B, "  exact note  ");
        assertThat(second.warnings()).extracting(ImportWarning::code).contains("UNASSIGNED_TOPIC_DATA");
        assertThat(second.preview().fields().get("notes").added()).isEqualTo(1);
    }

    @Test
    void warnsOnMonthConflictWithoutMovingExactDate() {
        var existing = new LegacyPlanState(Map.of(A, "Marzo"), Map.of(), Map.of(), Map.of());
        var result = importer.preview("{\"dates\":{\"" + A + "\":\"2026-09-15\"}}", existing);
        assertThat(result.state().dates()).containsEntry(A, "2026-09-15");
        assertThat(result.state().assignments()).containsEntry(A, "Marzo");
        assertThat(result.warnings()).extracting(ImportWarning::code).containsExactly("MONTH_DATE_CONFLICT");
    }

    @Test
    void countsUnchangedValuesAndReturnsImmutableSnapshots() {
        var mutable = new HashMap<>(Map.of(A, "Marzo"));
        var state = new LegacyPlanState(mutable, Map.of(), Map.of(), Map.of());
        mutable.put(A, "Abril");
        var result = importer.preview("{\"assignments\":{\"" + A + "\":\"Marzo\"}}", state);
        assertThat(result.state().assignments()).containsEntry(A, "Marzo");
        assertThat(result.preview().fields().get("assignments").unchanged()).isEqualTo(1);
        assertThatThrownBy(() -> result.state().assignments().put(B, "Mayo"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> result.warnings().add(new ImportWarning("x", A, "x")))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> result.preview().fields().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @ParameterizedTest
    @MethodSource("invalidPayloads")
    void rejectsInvalidPayload(String json, String code) {
        assertThatThrownBy(() -> importer.preview(json, LegacyPlanState.empty()))
                .isInstanceOfSatisfying(ImportValidationException.class, error ->
                        assertThat(error.violations()).extracting(ImportViolation::code).contains(code));
    }

    static Stream<Arguments> invalidPayloads() {
        return Stream.of(
                Arguments.of("null", "INVALID_SHAPE"),
                Arguments.of("", "INVALID_SHAPE"),
                Arguments.of("false", "INVALID_SHAPE"),
                Arguments.of("[]", "INVALID_SHAPE"),
                Arguments.of("{", "INVALID_JSON"),
                Arguments.of("{} {}", "INVALID_JSON"),
                Arguments.of("{\"notes\":{},\"notes\":{}}", "INVALID_JSON"),
                Arguments.of("{\"notes\":{\"Synthetic Math|1|0\":\"a\",\"Synthetic Math|1|0\":\"b\"}}", "INVALID_JSON"),
                Arguments.of("{\"state\":null}", "INVALID_SHAPE"),
                Arguments.of("{\"state\":[],\"version\":2}", "INVALID_SHAPE"),
                Arguments.of("{\"notes\":null}", "INVALID_MAP"),
                Arguments.of("{\"notes\":[]}", "INVALID_MAP"),
                Arguments.of("{\"assignments\":\"Marzo\"}", "INVALID_MAP"),
                Arguments.of("{\"state\":{},\"version\":3}", "UNSUPPORTED_VERSION"),
                Arguments.of("{\"state\":{},\"version\":\"2\"}", "UNSUPPORTED_VERSION"),
                Arguments.of("{\"state\":{},\"version\":2.0}", "UNSUPPORTED_VERSION"),
                Arguments.of("{\"state\":{},\"version\":2147483649}", "UNSUPPORTED_VERSION"),
                Arguments.of("{\"state\":{},\"year\":2025}", "WRONG_YEAR"),
                Arguments.of("{\"state\":{},\"grade\":5}", "WRONG_GRADE"),
                Arguments.of("{\"state\":{},\"year\":null}", "WRONG_YEAR"),
                Arguments.of("{\"other\":{}}", "UNKNOWN_FIELD"),
                Arguments.of("{\"state\":{},\"notes\":{}}", "UNKNOWN_FIELD"),
                Arguments.of("{\"notes\":{\"unknown|1|0\":\"text\"}}", "UNKNOWN_TOPIC"),
                Arguments.of("{\"notes\":{\"Synthetic Math|1|0\":null}}", "INVALID_VALUE"),
                Arguments.of("{\"notes\":{\"Synthetic Math|1|0\":7}}", "INVALID_VALUE"),
                Arguments.of("{\"status\":{\"Synthetic Math|1|0\":\"Pendiente\"}}", "INVALID_STATUS"),
                Arguments.of("{\"assignments\":{\"Synthetic Math|1|0\":\"January\"}}", "INVALID_MONTH"),
                Arguments.of("{\"dates\":{\"Synthetic Math|1|0\":\"2026-02-29\"}}", "INVALID_DATE"),
                Arguments.of("{\"dates\":{\"Synthetic Math|1|0\":\"2026-9-01\"}}", "INVALID_DATE"),
                Arguments.of("{\"dates\":{\"Synthetic Math|1|0\":\"2025-09-01\"}}", "WRONG_YEAR"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"assignments", "status", "dates", "notes"})
    void rejectsUnknownTopicsInEveryMapWithPath(String field) {
        assertThatThrownBy(() -> importer.preview("{\"" + field + "\":{\"unknown|1|0\":\"\"}}", LegacyPlanState.empty()))
                .isInstanceOfSatisfying(ImportValidationException.class, error ->
                        assertThat(error.violations()).contains(new ImportViolation(field + ".unknown|1|0", "UNKNOWN_TOPIC")));
    }

    @Test
    void validatesExistingStateAndCatalogRatherThanDroppingUnknownKeys() {
        var bad = new LegacyPlanState(Map.of(), Map.of(), Map.of(), Map.of("other|1|0", "note"));
        assertThatThrownBy(() -> importer.preview("{}", bad)).isInstanceOf(ImportValidationException.class);
        assertThatThrownBy(() -> new LegacyPlanImporter(Set.of("bad id"), 2026, 6))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void missingMapsPreserveExistingData() {
        var state = new LegacyPlanState(Map.of(), Map.of(), Map.of(), Map.of(A, "note"));
        assertThat(importer.preview("{}", state).state()).isEqualTo(state);
    }

    @Test
    void oneFieldClearDoesNotEraseIndependentStatusDateOrNote() {
        var state = new LegacyPlanState(Map.of(A, "Marzo"), Map.of(A, "Trabajado"),
                Map.of(A, "2026-03-04"), Map.of(A, "Keep me"));
        var result = importer.preview("{\"assignments\":{\"" + A + "\":\"\"}}", state);
        assertThat(result.state().status()).isEqualTo(state.status());
        assertThat(result.state().dates()).isEqualTo(state.dates());
        assertThat(result.state().notes()).isEqualTo(state.notes());
        assertThat(result.warnings()).extracting(ImportWarning::code).containsExactly("UNASSIGNED_TOPIC_DATA");
    }

    @Test
    void aggregatesUnknownIdsInsteadOfSilentlyDroppingThem() {
        assertThatThrownBy(() -> importer.preview("""
                {"notes":{"Unknown A|1|0":"a"},"dates":{"Unknown B|2|0":"2026-04-01"}}
                """, LegacyPlanState.empty()))
                .isInstanceOfSatisfying(ImportValidationException.class, error -> {
                    assertThat(error.violations()).hasSize(2);
                    assertThat(error.violations()).extracting(ImportViolation::path)
                            .containsExactlyInAnyOrder("notes.Unknown A|1|0", "dates.Unknown B|2|0");
                });
    }

    @Test
    void defendsCatalogAndRejectsOversizedPayloadRatherThanTruncatingNotes() {
        var catalog = new java.util.HashSet<>(Set.of(A));
        var boundary = new LegacyPlanImporter(catalog, 2026, 6);
        catalog.clear();
        assertThat(boundary.preview("{\"notes\":{\"" + A + "\":\"safe\"}}", LegacyPlanState.empty())
                .state().notes()).containsEntry(A, "safe");
        assertThatThrownBy(() -> importer.preview(" ".repeat(2_000_001), LegacyPlanState.empty()))
                .isInstanceOf(ImportValidationException.class);
        assertThatThrownBy(() -> importer.preview(null, LegacyPlanState.empty()))
                .isInstanceOf(ImportValidationException.class);
    }

    @Test
    void appliesConfiguredLeapYearPolicyWithoutChangingDate() {
        var boundary = new LegacyPlanImporter(Set.of(A), 2024, 6);
        var result = boundary.preview("{\"dates\":{\"" + A + "\":\"2024-02-29\"}}", LegacyPlanState.empty());
        assertThat(result.state().dates()).containsEntry(A, "2024-02-29");
    }

    private String fixture(String name) throws IOException {
        try (var input = getClass().getResourceAsStream("/legacy/" + name)) {
            if (input == null) throw new IOException("Missing synthetic fixture " + name);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
