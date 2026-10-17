import { CalendarDays, Clock3, Plus, TriangleAlert } from "lucide-react";
import { blocksOnDate, calendarDays, weekdays } from "../lib/planning";
import type { PlannerData, Session } from "../lib/types";
import { Button } from "./ui/button";
import { Card } from "./ui/card";
export function Calendar({
  data,
  areaId,
  month,
  onSession,
  onNew,
  editable,
}: {
  data: PlannerData;
  areaId: string;
  month: number;
  onSession: (s: Session) => void;
  onNew: (date: string) => void;
  editable: boolean;
}) {
  const topicMap = new Map(
    data.topics
      .filter((t) => !areaId || t.areaId === areaId)
      .map((t) => [t.id, t]),
  );
  const sessions = data.sessions.filter((s) => topicMap.has(s.topicId));
  const areaMap = new Map(data.areas.map((a) => [a.id, a.name]));
  return (
    <Card className="overflow-hidden">
      <div className="flex flex-wrap items-center justify-between gap-2 border-b border-slate-200 px-5 py-4">
        <div className="flex items-center gap-2 font-semibold">
          <CalendarDays size={18} className="text-teal-700" />
          Calendario de sesiones
        </div>
        <p className="text-xs text-slate-500">
          La fecha exacta es la referencia. No reprogramamos automáticamente.
        </p>
      </div>
      <div className="calendar-scroll">
        <div className="calendar-grid">
          {weekdays.map((d) => (
            <div key={d} className="weekday">
              {d}
            </div>
          ))}
          {calendarDays(data.plan.year, month).map((date) => {
            const active = Number(date.slice(5, 7)) === month;
            const items = sessions.filter((s) => s.exactDate === date);
            const blocks = blocksOnDate(data.blocks, date, areaId);
            const weekday =
              ((new Date(date + "T12:00:00").getDay() + 6) % 7) + 1;
            const schedules = data.schedules.filter(
              (s) => (!areaId || s.areaId === areaId) && s.weekday === weekday,
            );
            return (
              <div
                key={date}
                className={`calendar-day ${active ? "" : "outside"} ${blocks.length ? "blocked" : ""}`}
              >
                <div className="mb-2 flex items-center justify-between">
                  <time dateTime={date} className="day-number">
                    {Number(date.slice(8))}
                  </time>
                  {active && editable && (
                    <Button
                      variant="ghost"
                      size="icon"
                      aria-label={`Agregar sesión el ${date}`}
                      onClick={() => onNew(date)}
                    >
                      <Plus size={14} />
                    </Button>
                  )}
                </div>
                {active && (
                  <>
                    {schedules.map((s) => (
                      <div
                        className="mb-2 flex gap-1 text-[10px] text-slate-500"
                        key={s.id}
                      >
                        <Clock3 size={11} className="shrink-0" />
                        <span>
                          {s.startTime}–{s.endTime}
                          {!areaId && ` · ${areaMap.get(s.areaId)}`}
                        </span>
                      </div>
                    ))}
                    {blocks.map((b) => (
                      <div className="block-label" key={b.id}>
                        <TriangleAlert size={12} className="shrink-0" />
                        {b.label}
                        {!areaId && b.areaId && ` · ${areaMap.get(b.areaId)}`}
                      </div>
                    ))}
                    {items.map((s) => {
                      const t = topicMap.get(s.topicId)!;
                      const conflict = blocks.some(
                        (b) => !b.areaId || b.areaId === t.areaId,
                      );
                      return (
                        <button
                          key={s.id}
                          className={`session-chip ${s.status === "Trabajado" ? "done" : ""} ${conflict ? "conflict" : ""}`}
                          onClick={() => onSession(s)}
                        >
                          <span className="mb-1 flex items-center justify-between gap-1 text-[10px] font-bold uppercase tracking-wide">
                            <span>
                              U{t.unit}
                              {!areaId && ` · ${areaMap.get(t.areaId)}`}
                            </span>
                            <span>{s.status === "Trabajado" ? "✓" : "○"}</span>
                          </span>
                          <span>{t.name}</span>
                          {conflict && (
                            <span className="mt-1 block text-[10px] font-semibold text-red-700">
                              Conflicto con bloqueo
                            </span>
                          )}
                        </button>
                      );
                    })}
                  </>
                )}
              </div>
            );
          })}
        </div>
      </div>
      <div className="flex flex-wrap gap-4 border-t px-5 py-3 text-xs text-slate-500">
        <span>
          <i className="legend bg-indigo-100" />
          Pendiente
        </span>
        <span>
          <i className="legend bg-teal-100" />
          Trabajado
        </span>
        <span>
          <i className="legend bg-amber-100" />
          Bloqueo
        </span>
        <span>Se muestran todos los temas, incluidos sábados y domingos.</span>
      </div>
    </Card>
  );
}
