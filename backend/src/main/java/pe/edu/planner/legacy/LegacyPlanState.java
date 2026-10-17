package pe.edu.planner.legacy;

import java.util.Map;

/** Four independent maps preserve the legacy field-level merge contract. */
public record LegacyPlanState(Map<String, String> assignments, Map<String, String> status,
                              Map<String, String> dates, Map<String, String> notes) {
    public LegacyPlanState {
        assignments = Map.copyOf(assignments);
        status = Map.copyOf(status);
        dates = Map.copyOf(dates);
        notes = Map.copyOf(notes);
    }

    public static LegacyPlanState empty() {
        return new LegacyPlanState(Map.of(), Map.of(), Map.of(), Map.of());
    }
}
