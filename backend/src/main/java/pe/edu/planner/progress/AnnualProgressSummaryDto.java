package pe.edu.planner.progress;

public record AnnualProgressSummaryDto(
    int totalTopics,
    int completedTopics,
    int plannedPendingTopics,
    int unplannedTopics,
    double completionPercentage
) {}

