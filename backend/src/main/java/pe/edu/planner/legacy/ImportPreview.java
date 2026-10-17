package pe.edu.planner.legacy;

import java.util.Map;

public record ImportPreview(Map<String, FieldCounts> fields, int affectedTopics) {
    public ImportPreview {
        fields = Map.copyOf(fields);
    }
}
