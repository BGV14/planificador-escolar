import type {
  Area,
  Block,
  FieldCounts,
  PreviewCounts,
  Session,
  Topic,
  User,
} from "./types";
export const months = [
  "Enero",
  "Febrero",
  "Marzo",
  "Abril",
  "Mayo",
  "Junio",
  "Julio",
  "Agosto",
  "Setiembre",
  "Octubre",
  "Noviembre",
  "Diciembre",
];
export const weekdays = [
  "Lunes",
  "Martes",
  "Miércoles",
  "Jueves",
  "Viernes",
  "Sábado",
  "Domingo",
];
export const blockKinds = {
  EXAM: "Exámenes",
  VACATION: "Vacaciones",
  HOLIDAY: "Feriado",
  OTHER: "Otro evento",
};
export const isoDate = (year: number, month: number, day: number) =>
  `${String(year).padStart(4, "0")}-${String(month).padStart(2, "0")}-${String(day).padStart(2, "0")}`;
export function calendarDays(year: number, month: number) {
  const first = new Date(year, month - 1, 1, 12);
  const offset = (first.getDay() + 6) % 7;
  const last = new Date(year, month, 0, 12).getDate();
  const count = Math.ceil((offset + last) / 7) * 7;
  return Array.from({ length: count }, (_, i) => {
    const d = new Date(year, month - 1, 1 - offset + i, 12);
    return isoDate(d.getFullYear(), d.getMonth() + 1, d.getDate());
  });
}
export function sessionsOnDate(
  sessions: Session[],
  topics: Topic[],
  date: string,
  areaId: string,
) {
  const allowed = new Set(
    topics.filter((t) => !areaId || t.areaId === areaId).map((t) => t.id),
  );
  return sessions.filter((s) => s.exactDate === date && allowed.has(s.topicId));
}
export function monthConflicts(sessions: Session[]) {
  return sessions.filter(
    (s) =>
      s.assignment &&
      s.exactDate &&
      months[Number(s.exactDate.slice(5, 7)) - 1] !== s.assignment,
  );
}
export function canEdit(user: User, area?: Area) {
  return (
    !!area &&
    (user.role === "ADMIN" || area.ownerId === user.id || area.sharedEdit)
  );
}
export function blocksOnDate(blocks: Block[], date: string, areaId: string) {
  return blocks.filter(
    (b) =>
      (!areaId || !b.areaId || b.areaId === areaId) &&
      b.startDate <= date &&
      b.endDate >= date,
  );
}
export function previewTotals(preview: PreviewCounts): FieldCounts {
  return Object.values(preview.fields).reduce(
    (a, b) => ({
      incoming: a.incoming + b.incoming,
      added: a.added + b.added,
      updated: a.updated + b.updated,
      unchanged: a.unchanged + b.unchanged,
      explicitEmpty: a.explicitEmpty + b.explicitEmpty,
    }),
    { incoming: 0, added: 0, updated: 0, unchanged: 0, explicitEmpty: 0 },
  );
}
