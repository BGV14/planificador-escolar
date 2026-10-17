import { Search, Plus, CalendarPlus, FilePenLine } from "lucide-react";
import { useState } from "react";
import type { PlannerData, Session, Topic, User } from "../lib/types";
import { canEdit } from "../lib/planning";
import { Button } from "./ui/button";
import { Input } from "./ui/input";
import { Card } from "./ui/card";
import { Empty } from "./Feedback";
export function Topics({
  data,
  user,
  areaId,
  onTopic,
  onSession,
  onNewSession,
  onNewTopic,
}: {
  data: PlannerData;
  user: User;
  areaId: string;
  onTopic: (t: Topic) => void;
  onSession: (s: Session) => void;
  onNewSession: (t: Topic) => void;
  onNewTopic: () => void;
}) {
  const [search, setSearch] = useState("");
  const topics = data.topics.filter(
    (t) =>
      (!areaId || t.areaId === areaId) &&
      t.name.toLocaleLowerCase("es").includes(search.toLocaleLowerCase("es")),
  );
  const sessions = new Map(data.sessions.map((s) => [s.topicId, s]));
  return (
    <Card>
      <div className="flex flex-wrap items-center justify-between gap-3 border-b p-5">
        <div>
          <h2 className="font-semibold">Banco de temas</h2>
          <p className="text-xs text-slate-500">
            {topics.length} temas · catálogo y temas personalizados
          </p>
        </div>
        <div className="flex gap-2">
          <div className="relative">
            <Search
              size={16}
              className="absolute left-3 top-3 text-slate-400"
            />
            <Input
              aria-label="Buscar tema"
              className="pl-9"
              placeholder="Buscar tema…"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
            />
          </div>
          <Button
            onClick={onNewTopic}
            disabled={
              !data.areas.some(
                (a) => (!areaId || a.id === areaId) && canEdit(user, a),
              )
            }
          >
            <Plus size={16} />
            Tema
          </Button>
        </div>
      </div>
      {!topics.length ? (
        <Empty
          title="Sin temas en esta vista"
          detail="Cambia de área o agrega un tema para comenzar."
        />
      ) : (
        <div className="table-scroll">
          <table>
            <thead>
              <tr>
                <th>Tema / unidad</th>
                <th>Área</th>
                <th>Mes asignado</th>
                <th>Fecha exacta</th>
                <th>Estado</th>
                <th>
                  <span className="sr-only">Acciones</span>
                </th>
              </tr>
            </thead>
            <tbody>
              {topics.map((t) => {
                const s = sessions.get(t.id);
                const a = data.areas.find((a) => a.id === t.areaId);
                const edit = canEdit(user, a);
                return (
                  <tr key={t.id}>
                    <td>
                      <p className="font-medium">{t.name}</p>
                      <small>
                        Unidad {t.unit} ·{" "}
                        {t.legacyId ? "Catálogo" : "Personalizado"}
                      </small>
                    </td>
                    <td>{a?.name}</td>
                    <td>{s?.assignment || "Sin asignar"}</td>
                    <td>{s?.exactDate || "Sin fecha"}</td>
                    <td>
                      <span
                        className={`badge ${s?.status === "Trabajado" ? "success" : ""}`}
                      >
                        {s?.status === "Trabajado" ? "Trabajado" : "Pendiente"}
                      </span>
                    </td>
                    <td>
                      <div className="flex justify-end gap-1">
                        <Button
                          variant="ghost"
                          size="icon"
                          aria-label={`Editar tema ${t.name}`}
                          onClick={() => onTopic(t)}
                          disabled={!edit}
                        >
                          <FilePenLine size={16} />
                        </Button>
                        <Button
                          variant="outline"
                          size="sm"
                          disabled={!s && !edit}
                          onClick={() => (s ? onSession(s) : onNewSession(t))}
                        >
                          <CalendarPlus size={15} />
                          {s ? "Ver sesión" : "Planificar"}
                        </Button>
                      </div>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}
    </Card>
  );
}
