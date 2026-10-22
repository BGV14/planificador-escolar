package pe.edu.planner.progress;

public record AnnualTopicItemDto(
    String id,
    String name,
    String areaId,
    String areaName,
    int unit,
    TopicAnnualStatus status,
    String assignment,
    String exactDate,
    boolean catalogTopic
) {}

