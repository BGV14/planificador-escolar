import { useEffect, useRef, useState } from "react";
import { api, contextPath, errorMessage } from "../lib/api";
import type { Area, Teacher } from "../lib/types";
import { Button } from "./ui/button";
import { Select } from "./ui/input";
import { Card } from "./ui/card";
import { ErrorNotice } from "./Feedback";

/** Mounted only for administrators; the server independently enforces every mutation. */
export function TeacherAssignments({
  contextId,
  teachers,
  areas,
  onChanged,
}: {
  contextId: string;
  teachers: Teacher[];
  areas: Area[];
  onChanged: () => void | Promise<void>;
}) {
  const [tutors, setTutors] = useState<Teacher[]>([]);
  const [teacherId, setTeacherId] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const [ready, setReady] = useState(false);
  const epoch = useRef(0);
  useEffect(() => {
    const current = ++epoch.current;
    setTutors([]);
    setTeacherId("");
    setError("");
    setReady(false);
    setBusy(false);
    api
      .get<Teacher[]>(contextPath("/classroom-tutors", contextId))
      .then((items) => {
        if (epoch.current === current) {
          setTutors(items);
          setReady(true);
        }
      })
      .catch((e) => {
        if (epoch.current === current) setError(errorMessage(e));
      });
    return () => {
      ++epoch.current;
    };
  }, [contextId]);

  async function change(path: string, method: string, body?: unknown) {
    const current = epoch.current;
    setBusy(true);
    setError("");
    try {
      await api.mutate(contextPath(path, contextId), method, body);
      if (epoch.current !== current) return;
      const items = await api.get<Teacher[]>(
        contextPath("/classroom-tutors", contextId),
      );
      if (epoch.current !== current) return;
      setTutors(items);
      setTeacherId("");
      await onChanged();
    } catch (e) {
      if (epoch.current === current) setError(errorMessage(e));
    } finally {
      if (epoch.current === current) setBusy(false);
    }
  }

  return (
    <Card className="p-5 xl:col-span-2">
      <h2 className="text-sm font-semibold">
        Asignaciones del aula seleccionada
      </h2>
      <p className="mt-2 text-sm text-slate-500">
        La tutoría permite consultar toda esta aula, pero no editar cursos de
        otros responsables. La edición compartida mantiene su alcance actual.
      </p>
      {error && <ErrorNotice message={error} />}
      <ul className="my-3 space-y-2">
        {tutors.map((t) => (
          <li key={t.id} className="flex items-center gap-3">
            <span>{t.username}</span>
            <Button
              size="sm"
              variant="outline"
              disabled={busy}
              aria-label={`Revocar tutoría de ${t.username}`}
              onClick={() =>
                void change(
                  `/classroom-tutors/${encodeURIComponent(t.id)}`,
                  "DELETE",
                )
              }
            >
              Revocar tutoría
            </Button>
          </li>
        ))}
      </ul>
      {ready && tutors.length === 0 && (
        <p className="my-2 text-sm">Sin tutoría asignada.</p>
      )}
      <div className="flex flex-wrap items-end gap-3">
        <label>
          Docente tutor
          <Select
            aria-label="Docente tutor"
            value={teacherId}
            disabled={busy || !ready}
            onChange={(e) => setTeacherId(e.target.value)}
          >
            <option value="">Seleccionar docente</option>
            {teachers
              .filter(
                (t) =>
                  t.role === "TEACHER" && !tutors.some((a) => a.id === t.id),
              )
              .map((t) => (
                <option key={t.id} value={t.id}>
                  {t.username}
                </option>
              ))}
          </Select>
        </label>
        <Button
          disabled={busy || !ready || !teacherId}
          onClick={() =>
            void change("/classroom-tutors", "POST", { userId: teacherId })
          }
        >
          Asignar tutoría
        </Button>
      </div>
      <h3 className="mt-5 text-sm font-semibold">Responsables de curso</h3>
      <div className="mt-3 space-y-3">
        {areas.map((area) => (
          <label key={area.id} className="block">
            {area.name}
            <Select
              aria-label={`Responsable de ${area.name}`}
              value={area.ownerId}
              disabled={busy || !ready}
              onChange={(e) =>
                void change(`/areas/${encodeURIComponent(area.id)}`, "PUT", {
                  name: area.name,
                  ownerId: e.target.value,
                  sharedEdit: area.sharedEdit,
                })
              }
            >
              {!teachers.some(
                (t) => t.id === area.ownerId && t.role === "TEACHER",
              ) && (
                <option value={area.ownerId}>
                  {teachers.find((t) => t.id === area.ownerId)?.username ??
                    "Responsable actual"}
                </option>
              )}
              {teachers
                .filter((t) => t.role === "TEACHER")
                .map((t) => (
                  <option key={t.id} value={t.id}>
                    {t.username}
                  </option>
                ))}
            </Select>
          </label>
        ))}
      </div>
    </Card>
  );
}
