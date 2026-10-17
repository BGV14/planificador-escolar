package pe.edu.planner.legacy;

/** Incoming is the sum of added, updated and unchanged; empty values are also counted. */
public record FieldCounts(int incoming, int added, int updated, int unchanged, int explicitEmpty) {
}
