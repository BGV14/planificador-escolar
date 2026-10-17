package pe.edu.planner.legacy;

import java.util.List;

/** No partially imported state is returned when any violation exists. */
public final class ImportValidationException extends IllegalArgumentException {
    private static final long serialVersionUID = 1L;
    private final List<ImportViolation> violations;

    public ImportValidationException(List<ImportViolation> violations) {
        super("Legacy import rejected: " + violations);
        this.violations = List.copyOf(violations);
    }

    public List<ImportViolation> violations() {
        return violations;
    }
}
