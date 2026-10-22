import type { Area, PlannerData, Topic } from "./types";

export type CoverageStatus = "completed" | "planned" | "unplanned";

export interface CoverageCounts {
  total: number;
  completed: number;
  planned: number;
  unplanned: number;
  completionPercent: number | null;
}

export interface TopicCoverage {
  topic: Topic;
  status: CoverageStatus;
}

export interface AreaCoverage extends CoverageCounts {
  area: Area;
  topics: TopicCoverage[];
}

export interface AnnualCoverage {
  areas: AreaCoverage[];
  totals: CoverageCounts;
}

function emptyCounts(): CoverageCounts {
  return {
    total: 0,
    completed: 0,
    planned: 0,
    unplanned: 0,
    completionPercent: null,
  };
}

function updatePercent(counts: CoverageCounts) {
  counts.completionPercent =
    counts.total === 0 ? null : (counts.completed * 100) / counts.total;
}

/** Aggregate the full catalog of the currently loaded academic context, not a month. */
export function buildAnnualCoverage(
  data: Pick<PlannerData, "areas" | "topics" | "sessions">,
): AnnualCoverage {
  const areas: AreaCoverage[] = data.areas.map((area) => ({
    ...emptyCounts(),
    area,
    topics: [],
  }));
  const byArea = new Map(areas.map((summary) => [summary.area.id, summary]));
  const byTopic = new Map<string, CoverageStatus>();

  // Completion wins over planning evidence even when sessions have no dates.
  for (const session of data.sessions) {
    if (session.status === "Trabajado") {
      byTopic.set(session.topicId, "completed");
    } else if (
      byTopic.get(session.topicId) !== "completed" &&
      (session.assignment?.trim() || session.exactDate?.trim())
    ) {
      byTopic.set(session.topicId, "planned");
    }
  }

  const totals = emptyCounts();
  for (const topic of data.topics) {
    const summary = byArea.get(topic.areaId);
    if (!summary) continue;
    const status = byTopic.get(topic.id) ?? "unplanned";
    summary.topics.push({ topic, status });
    summary.total += 1;
    summary[status] += 1;
    totals.total += 1;
    totals[status] += 1;
  }
  areas.forEach(updatePercent);
  updatePercent(totals);
  return { areas, totals };
}
