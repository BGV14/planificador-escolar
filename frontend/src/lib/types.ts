export interface School {
  id: string;
  name: string;
  school_year: number;
  grade: number;
}
export interface User {
  id: string;
  schoolId: string;
  username: string;
  role: "ADMIN" | "TEACHER";
  school: School;
}
export interface Teacher {
  id: string;
  username: string;
  role: "ADMIN" | "TEACHER";
}
export interface Area {
  id: string;
  name: string;
  ownerId: string;
  sharedEdit: boolean;
}
export interface Topic {
  id: string;
  areaId: string;
  name: string;
  unit: number;
  legacyId: string | null;
}
export interface Session {
  id: string;
  topicId: string;
  assignment: string | null;
  status: string | null;
  exactDate: string | null;
  notes: string | null;
  version: number;
}
export interface Schedule {
  id: string;
  areaId: string;
  weekday: number;
  startTime: string;
  endTime: string;
}
export interface Block {
  id: string;
  areaId: string | null;
  label: string;
  kind: "EXAM" | "VACATION" | "HOLIDAY" | "OTHER";
  startDate: string;
  endDate: string;
}
export interface Warning {
  code?: string;
  topicId?: string;
  message?: string;
  id?: string;
  exactDate?: string;
  blockId?: string;
  label?: string;
}
export type AcademicLevel = "INITIAL" | "PRIMARY" | "SECONDARY";
export interface AcademicContext {
  id: string;
  schoolId: string;
  year: number;
  level: AcademicLevel;
  stageKind: "AGE" | "GRADE";
  stageValue: number;
  cycle: "I" | "II" | null;
  displayLabel: string;
  revision: number;
  legacyDefault: boolean;
  grade: number | null;
}
export interface Plan extends AcademicContext {
  revision: number;
  warnings: Warning[];
}
export interface FieldCounts {
  incoming: number;
  added: number;
  updated: number;
  unchanged: number;
  explicitEmpty: number;
}
export interface PreviewCounts {
  fields: Record<string, FieldCounts>;
  affectedTopics: number;
}
export interface ImportPreview {
  revision: number;
  notice: string;
  result: { preview: PreviewCounts; warnings: Warning[] };
}
export interface ImportHistory {
  id: string;
  userId: string;
  importedAt: string;
  affectedTopics: number;
  revision: number;
}
export interface PlannerData {
  areas: Area[];
  topics: Topic[];
  sessions: Session[];
  schedules: Schedule[];
  blocks: Block[];
  plan: Plan;
  history: ImportHistory[];
  teachers: Teacher[];
}
