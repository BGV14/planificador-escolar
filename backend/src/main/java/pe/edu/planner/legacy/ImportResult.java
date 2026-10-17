package pe.edu.planner.legacy;

import java.util.List;

/** A preview is a new snapshot; producing it never persists or changes the caller's state. */
public record ImportResult(LegacyPlanState state, ImportPreview preview, List<ImportWarning> warnings) {
    public ImportResult {
        warnings = List.copyOf(warnings);
    }
}
