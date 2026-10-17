import {
  Plus,
  FilePenLine,
  Clock3,
  Users,
  CalendarRange,
  Layers3,
} from "lucide-react";
import type { PlannerData, User } from "../lib/types";
import { canEdit, blockKinds, weekdays } from "../lib/planning";
import type { Editor } from "./EntityEditor";
import { Button } from "./ui/button";
import { Card } from "./ui/card";
import { Empty } from "./Feedback";
export function Settings({
  data,
  user,
  onEdit,
}: {
  data: PlannerData;
  user: User;
  onEdit: (e: Editor) => void;
}) {
  const admin = user.role === "ADMIN";
  const editable = data.areas.some((a) => canEdit(user, a));
  return (
    <div className="grid gap-6 xl:grid-cols-2">
      <Card>
        <div className="section-heading">
          <h2>
            <Layers3 size={18} />
            Áreas y responsables
          </h2>
          {admin && (
            <Button size="sm" onClick={() => onEdit({ kind: "areas" })}>
              <Plus size={14} />
              Área
            </Button>
          )}
        </div>
        {data.areas.length ? (
          <ul className="divide-y">
            {data.areas.map((a) => (
              <li
                key={a.id}
                className="flex items-center justify-between gap-3 p-5"
              >
                <div>
                  <h3 className="text-sm font-semibold">{a.name}</h3>
                  <p className="mt-1 text-xs text-slate-500">
                    {data.teachers.find((t) => t.id === a.ownerId)?.username ||
                      (a.ownerId === user.id
                        ? user.username
                        : "Otro docente")}{" "}
                    ·{" "}
                    {a.sharedEdit
                      ? "Edición compartida"
                      : "Responsable y administración"}
                  </p>
                </div>
                {admin && (
                  <Button
                    variant="ghost"
                    size="icon"
                    aria-label={`Editar área ${a.name}`}
                    onClick={() => onEdit({ kind: "areas", item: a })}
                  >
                    <FilePenLine size={17} />
                  </Button>
                )}
              </li>
            ))}
          </ul>
        ) : (
          <Empty
            title="Sin áreas"
            detail="La administración puede crear la primera área."
          />
        )}
      </Card>
      <Card>
        <div className="section-heading">
          <h2>
            <Clock3 size={18} />
            Horario semanal
          </h2>
          {editable && (
            <Button size="sm" onClick={() => onEdit({ kind: "schedules" })}>
              <Plus size={14} />
              Horario
            </Button>
          )}
        </div>
        {data.schedules.length ? (
          <ul className="divide-y">
            {[...data.schedules]
              .sort(
                (a, b) =>
                  a.weekday - b.weekday ||
                  a.startTime.localeCompare(b.startTime),
              )
              .map((s) => (
                <li
                  key={s.id}
                  className="flex items-center justify-between gap-3 p-5"
                >
                  <div>
                    <h3 className="text-sm font-semibold">
                      {weekdays[s.weekday - 1]} · {s.startTime}–{s.endTime}
                    </h3>
                    <p className="mt-1 text-xs text-slate-500">
                      {data.areas.find((a) => a.id === s.areaId)?.name}
                    </p>
                  </div>
                  {canEdit(
                    user,
                    data.areas.find((a) => a.id === s.areaId),
                  ) && (
                    <Button
                      variant="ghost"
                      size="icon"
                      aria-label="Editar horario"
                      onClick={() => onEdit({ kind: "schedules", item: s })}
                    >
                      <FilePenLine size={17} />
                    </Button>
                  )}
                </li>
              ))}
          </ul>
        ) : (
          <Empty
            title="Horario aún no definido"
            detail="Los horarios se muestran como referencia; no asignan ni mueven fechas."
          />
        )}
      </Card>
      <Card>
        <div className="section-heading">
          <h2>
            <CalendarRange size={18} />
            Calendario escolar
          </h2>
          {(editable || admin) && (
            <Button size="sm" onClick={() => onEdit({ kind: "blocks" })}>
              <Plus size={14} />
              Evento
            </Button>
          )}
        </div>
        {data.blocks.length ? (
          <ul className="divide-y">
            {[...data.blocks]
              .sort((a, b) => a.startDate.localeCompare(b.startDate))
              .map((b) => (
                <li
                  key={b.id}
                  className="flex items-center justify-between gap-3 p-5"
                >
                  <div>
                    <h3 className="text-sm font-semibold">
                      {b.label}{" "}
                      <span className="badge warning">
                        {blockKinds[b.kind]}
                      </span>
                    </h3>
                    <p className="mt-1 text-xs text-slate-500">
                      {b.startDate} → {b.endDate} ·{" "}
                      {b.areaId
                        ? data.areas.find((a) => a.id === b.areaId)?.name
                        : "Todo este plan"}
                    </p>
                  </div>
                  {(admin ||
                    canEdit(
                      user,
                      data.areas.find((a) => a.id === b.areaId),
                    )) && (
                    <Button
                      variant="ghost"
                      size="icon"
                      aria-label={`Editar evento ${b.label}`}
                      onClick={() => onEdit({ kind: "blocks", item: b })}
                    >
                      <FilePenLine size={17} />
                    </Button>
                  )}
                </li>
              ))}
          </ul>
        ) : (
          <Empty
            title="Sin bloqueos registrados"
            detail="Agrega exámenes, vacaciones o feriados confirmados. No se insertan fechas supuestas."
          />
        )}
      </Card>
      {admin && (
        <Card>
          <div className="section-heading">
            <h2>
              <Users size={18} />
              Equipo docente
            </h2>
            <Button size="sm" onClick={() => onEdit({ kind: "users" })}>
              <Plus size={14} />
              Docente
            </Button>
          </div>
          <ul className="divide-y">
            {data.teachers.map((t) => (
              <li key={t.id} className="flex items-center gap-3 p-5">
                <span className="avatar">
                  {t.username.slice(0, 2).toUpperCase()}
                </span>
                <div>
                  <p className="text-sm font-semibold">{t.username}</p>
                  <p className="text-xs text-slate-500">
                    {t.role === "ADMIN" ? "Administración" : "Docente"}
                  </p>
                </div>
              </li>
            ))}
          </ul>
        </Card>
      )}
    </div>
  );
}
