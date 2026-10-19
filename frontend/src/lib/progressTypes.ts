export type TopicAnnualStatus =
  | "COMPLETADO"
  | "PLANIFICADO_PENDIENTE"
  | "NO_PLANIFICADO";

export interface AnnualTopicItem {
  id: string;
  name: string;
  areaId: string;
  areaName: string;
  unit: number;
  status: TopicAnnualStatus;
  assignment: string | null;
  exactDate: string | null;
  catalogTopic: boolean;
}

export interface AnnualProgressSummary {
  totalTopics: number;
  completedTopics: number;
  plannedPendingTopics: number;
  unplannedTopics: number;
  completionPercentage: number;
}

export interface AnnualProgressAreaSummary {
  areaId: string;
  areaName: string;
  total: number;
  completed: number;
  plannedPending: number;
  unplanned: number;
}

export interface AnnualProgressData {
  contextId: string;
  displayLabel: string;
  year: number;
  level: string;
  stageValue: number;
  summary: AnnualProgressSummary;
  areaSummaries: AnnualProgressAreaSummary[];
  topics: AnnualTopicItem[];
}
