package pe.edu.planner.progress;

import java.util.List;

public record AnnualProgressDto(
    String contextId,
    String displayLabel,
    int year,
    String level,
    int stageValue,
    AnnualProgressSummaryDto summary,
    List<AnnualProgressAreaSummaryDto> areaSummaries,
    List<AnnualTopicItemDto> topics
) {}

