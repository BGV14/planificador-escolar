package pe.edu.planner.legacy;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Pure, all-or-nothing compatibility boundary. It exposes no network or persistence API. */
public final class LegacyPlanImporter {
    private static final List<String> FIELDS = List.of("assignments", "status", "dates", "notes");
    private static final Set<String> WRAPPER_FIELDS = Set.of("version", "year", "grade", "state");
    private static final List<String> MONTHS = List.of("Marzo", "Abril", "Mayo", "Junio", "Julio",
            "Agosto", "Setiembre", "Octubre", "Noviembre", "Diciembre");
    private static final Pattern TOPIC_ID = Pattern.compile("[^|]+\\|[1-9][0-9]*\\|(?:0|[1-9][0-9]*)");
    private static final Pattern DATE = Pattern.compile("[0-9]{4}-[0-9]{2}-[0-9]{2}");
    private static final int MAX_PAYLOAD_CHARS = 2_000_000;

    private final Set<String> knownTopicIds;
    private final int schoolYear;
    private final int grade;
    private final ObjectMapper mapper;

    public LegacyPlanImporter(Set<String> knownTopicIds, int schoolYear, int grade) {
        this.knownTopicIds = Set.copyOf(knownTopicIds);
        if (this.knownTopicIds.stream().anyMatch(id -> !TOPIC_ID.matcher(id).matches())) {
            throw new IllegalArgumentException("Catalog IDs must use course|unit|zeroBasedIndex");
        }
        if (schoolYear < 1 || schoolYear > 9999 || grade < 1 || grade > 12) {
            throw new IllegalArgumentException("Invalid school year or grade policy");
        }
        this.schoolYear = schoolYear;
        this.grade = grade;
        var factory = JsonFactory.builder()
                .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                .streamReadConstraints(StreamReadConstraints.builder()
                        .maxNestingDepth(16).maxStringLength(100_000).maxNumberLength(16).build())
                .build();
        this.mapper = new ObjectMapper(factory).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    }

    public ImportResult preview(String json, LegacyPlanState existing) {
        Objects.requireNonNull(existing, "Existing state is required");
        if (json == null || json.length() > MAX_PAYLOAD_CHARS) {
            throw rejected("$", "INVALID_JSON");
        }
        JsonNode root;
        try {
            root = mapper.readTree(json);
        } catch (JsonProcessingException error) {
            // Do not retain parser source snippets or private backup values in diagnostics.
            throw rejected("$", "INVALID_JSON");
        }
        if (root == null || !root.isObject()) throw rejected("$", "INVALID_SHAPE");
        var errors = new ArrayList<ImportViolation>();
        var incomingNode = root;
        if (root.has("state")) {
            checkFields(root, WRAPPER_FIELDS, "$", errors);
            checkMetadata(root, "version", Set.of(1, 2), "UNSUPPORTED_VERSION", errors);
            checkMetadata(root, "year", Set.of(schoolYear), "WRONG_YEAR", errors);
            checkMetadata(root, "grade", Set.of(grade), "WRONG_GRADE", errors);
            incomingNode = root.get("state");
        }
        var incoming = readState(incomingNode, errors);
        var baseline = readState(mapper.valueToTree(existing), errors);
        if (!errors.isEmpty()) throw new ImportValidationException(errors);

        var merged = new LinkedHashMap<String, Map<String, String>>();
        var counts = new LinkedHashMap<String, FieldCounts>();
        var affected = new TreeSet<String>();
        for (var field : FIELDS) {
            var oldValues = baseline.get(field);
            var newValues = incoming.get(field);
            var result = new HashMap<>(oldValues);
            result.putAll(newValues);
            merged.put(field, result);
            affected.addAll(newValues.keySet());
            int added = 0, updated = 0, unchanged = 0, empty = 0;
            for (var entry : newValues.entrySet()) {
                if (!oldValues.containsKey(entry.getKey())) added++;
                else if (oldValues.get(entry.getKey()).equals(entry.getValue())) unchanged++;
                else updated++;
                if (entry.getValue().isEmpty()) empty++;
            }
            counts.put(field, new FieldCounts(newValues.size(), added, updated, unchanged, empty));
        }
        var state = new LegacyPlanState(merged.get("assignments"), merged.get("status"),
                merged.get("dates"), merged.get("notes"));
        return new ImportResult(state, new ImportPreview(counts, affected.size()), warnings(state));
    }

    private Map<String, Map<String, String>> readState(JsonNode state, List<ImportViolation> errors) {
        var result = new LinkedHashMap<String, Map<String, String>>();
        if (state == null || !state.isObject()) {
            errors.add(new ImportViolation("state", "INVALID_SHAPE"));
            return result;
        }
        checkFields(state, Set.copyOf(FIELDS), "state", errors);
        for (var field : FIELDS) {
            var values = new LinkedHashMap<String, String>();
            result.put(field, values);
            if (!state.has(field)) continue;
            var node = state.get(field);
            if (!node.isObject()) {
                errors.add(new ImportViolation(field, "INVALID_MAP"));
                continue;
            }
            node.properties().forEach(entry -> {
                var path = field + "." + entry.getKey();
                if (!knownTopicIds.contains(entry.getKey())) {
                    errors.add(new ImportViolation(path, "UNKNOWN_TOPIC"));
                }
                if (!entry.getValue().isTextual()) {
                    errors.add(new ImportViolation(path, "INVALID_VALUE"));
                    return;
                }
                var value = entry.getValue().textValue();
                if (field.equals("assignments") && value.equals("Septiembre")) value = "Setiembre";
                var code = validateValue(field, value);
                if (code != null) errors.add(new ImportViolation(path, code));
                values.put(entry.getKey(), value);
            });
        }
        return result;
    }

    private String validateValue(String field, String value) {
        if (value.isEmpty()) return null;
        return switch (field) {
            case "assignments" -> MONTHS.contains(value) ? null : "INVALID_MONTH";
            case "status" -> value.equals("Trabajado") ? null : "INVALID_STATUS";
            case "dates" -> validateDate(value);
            default -> null;
        };
    }

    private String validateDate(String value) {
        if (!DATE.matcher(value).matches()) return "INVALID_DATE";
        try {
            return LocalDate.parse(value).getYear() == schoolYear ? null : "WRONG_YEAR";
        } catch (DateTimeParseException error) {
            return "INVALID_DATE";
        }
    }

    private void checkMetadata(JsonNode root, String field, Set<Integer> allowed, String code,
                               List<ImportViolation> errors) {
        if (!root.has(field)) return;
        var value = root.get(field);
        if (!value.isIntegralNumber() || !value.canConvertToInt() || !allowed.contains(value.intValue())) {
            errors.add(new ImportViolation(field, code));
        }
    }

    private void checkFields(JsonNode node, Set<String> allowed, String path, List<ImportViolation> errors) {
        node.fieldNames().forEachRemaining(field -> {
            if (!allowed.contains(field)) errors.add(new ImportViolation(path + "." + field, "UNKNOWN_FIELD"));
        });
    }

    private List<ImportWarning> warnings(LegacyPlanState state) {
        var warnings = new ArrayList<ImportWarning>();
        var ids = new TreeSet<>(state.assignments().keySet());
        ids.addAll(state.status().keySet());
        ids.addAll(state.dates().keySet());
        ids.addAll(state.notes().keySet());
        for (var id : ids) {
            var month = state.assignments().getOrDefault(id, "");
            var date = state.dates().getOrDefault(id, "");
            if (month.isEmpty() && (!date.isEmpty() || !state.status().getOrDefault(id, "").isEmpty()
                    || !state.notes().getOrDefault(id, "").isEmpty())) {
                warnings.add(new ImportWarning("UNASSIGNED_TOPIC_DATA", id,
                        "Data is preserved for a topic with no assigned month."));
            } else if (!month.isEmpty() && !date.isEmpty()
                    && LocalDate.parse(date).getMonthValue() != MONTHS.indexOf(month) + 3) {
                warnings.add(new ImportWarning("MONTH_DATE_CONFLICT", id,
                        "Exact date is preserved despite a conflicting assigned month."));
            }
        }
        return warnings;
    }

    private ImportValidationException rejected(String path, String code) {
        return new ImportValidationException(List.of(new ImportViolation(path, code)));
    }
}
