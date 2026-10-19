package pe.edu.planner.progress;

public record AnnualProgressAreaSummaryDto(
    String areaId,
    String areaName,
    int total,
    int completed,
    int plannedPending,
    int unplanned
) {}

