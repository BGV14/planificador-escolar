import { useEffect, useRef, useState, type FormEvent } from "react";
import { api, errorMessage, contextPath } from "../lib/api";
import { blockKinds, canEdit, months, weekdays } from "../lib/planning";
import type {
  PlannerData,
  User,
  Area,
  Topic,
  Session,
  Schedule,
  Block,
} from "../lib/types";
import { Dialog } from "./ui/dialog";
import { Button } from "./ui/button";
import { Field, TextField, Select, Textarea } from "./Fields";
import { ErrorNotice } from "./Feedback";
export type Editor = {
  kind: "topics" | "sessions" | "areas" | "schedules" | "blocks" | "users";
  item?: Area | Topic | Session | Schedule | Block;
  topicId?: string;
  date?: string;
};
const names = {
  topics: "tema",
  sessions: "sesión",
  areas: "área",
  schedules: "horario",
  blocks: "evento",
  users: "docente",
};
export function EntityEditor({
  editor,
  data,
  user,
  areaId,
  onClose,
  onSaved,
  onReload,
}: {
  editor: Editor;
  data: PlannerData;
  user: User;
  areaId: string;
  onClose: () => void;
  onSaved: () => Promise<void>;
  onReload: () => Promise<void>;
}) {
  const active = useRef(true);
  useEffect(() => {
    active.current = true;
    return () => {
      active.current = false;
    };
  }, []);
  const contextId = useRef(data.plan.id).current;
  const path = (value: string) =>
    editor.kind === "users" ? value : contextPath(value, contextId);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const [confirmDelete, setConfirmDelete] = useState(false);
  const { kind } = editor;
  const item = editor.item as
    (Area & Topic & Session & Schedule & Block) | undefined;
  const owned = data.areas.filter((a) => canEdit(user, a));
  const topic =
    item && kind === "sessions"
      ? data.topics.find((t) => t.id === item.topicId)
      : undefined;
  const edit =
    kind === "users" || kind === "areas"
      ? user.role === "ADMIN"
      : kind === "sessions"
        ? canEdit(
            user,
            data.areas.find(
              (a) =>
                a.id ===
                (topic?.areaId ||
                  data.topics.find((t) => t.id === editor.topicId)?.areaId),
            ),
          ) ||
          (!item && owned.length > 0)
        : kind === "blocks" && item && !item.areaId
          ? user.role === "ADMIN"
          : !item ||
            canEdit(
              user,
              data.areas.find((a) => a.id === item.areaId),
            );
  const defaultArea =
    kind === "blocks" && item
      ? (item.areaId ?? "")
      : item?.areaId || areaId || owned[0]?.id || "";
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError("");
    setBusy(true);
    const f = new FormData(event.currentTarget);
    const val = (name: string) => String(f.get(name) ?? "");
    let body: Record<string, unknown> = {};
    if (kind === "areas")
      body = {
        name: val("name"),
        ownerId: val("ownerId"),
        sharedEdit: f.has("sharedEdit"),
      };
    if (kind === "topics")
      body = {
        name: val("name"),
        areaId: val("areaId"),
        unit: Number(val("unit")),
        legacyId: item?.legacyId ?? null,
      };
    if (kind === "sessions") {
      const preserve = (
        name: "assignment" | "status" | "notes",
        old: string | null | undefined,
      ) => (val(name) === "" && old === null ? null : val(name));
      body = {
        topicId: item?.topicId || val("topicId"),
        version: item?.version,
        assignment: preserve("assignment", item?.assignment),
        status: preserve("status", item?.status),
        notes: preserve("notes", item?.notes),
        date:
          val("date") === "" && item?.exactDate === null ? null : val("date"),
      };
    }
    if (kind === "schedules")
      body = {
        areaId: val("areaId"),
        weekday: Number(val("weekday")),
        startTime: val("startTime"),
        endTime: val("endTime"),
      };
    if (kind === "blocks")
      body = {
        areaId: val("areaId") || null,
        label: val("label"),
        kind: val("kind"),
        startDate: val("startDate"),
        endDate: val("endDate"),
      };
    if (kind === "users")
      body = {
        username: val("username"),
        password: val("password"),
        role: val("role"),
      };
    try {
      await api.mutate(
        path(`/${kind}${item ? "/" + encodeURIComponent(item.id) : ""}`),
        item ? "PUT" : "POST",
        body,
      );
      // A successful mutation must not remain retryable if only the subsequent reload fails.
      if (!active.current) return;
      await onSaved().catch(() => {});
      if (active.current) onClose();
    } catch (e) {
      if (active.current) setError(errorMessage(e));
    } finally {
      if (active.current) setBusy(false);
    }
  }
  async function remove() {
    if (!item) return;
    setBusy(true);
    setError("");
    try {
      await api.mutate(
        path(
          `/${kind}/${encodeURIComponent(item.id)}${kind === "sessions" ? `?version=${item.version}` : ""}`,
        ),
        "DELETE",
      );
      if (!active.current) return;
      await onSaved().catch(() => {});
      if (active.current) onClose();
    } catch (e) {
      if (active.current) setError(errorMessage(e));
      setConfirmDelete(false);
    } finally {
      if (active.current) setBusy(false);
    }
  }
  const areaField = (allowSchool = false) => (
    <Field label="Área">
      {(id) => (
        <Select
          id={id}
          name="areaId"
          defaultValue={defaultArea}
          required={!allowSchool}
        >
          {allowSchool && user.role === "ADMIN" && (
            <option value="">Todo este plan</option>
          )}
          {owned.map((a) => (
            <option key={a.id} value={a.id}>
              {a.name}
            </option>
          ))}
        </Select>
      )}
    </Field>
  );
  return (
    <Dialog
      open
      onOpenChange={(o) => {
        if (!o && !busy) onClose();
      }}
      title={`${item ? "Detalle de" : "Nuevo"} ${names[kind]}`}
      description={
        edit
          ? "Revisa los datos antes de guardarlos. Las fechas nunca se mueven automáticamente."
          : "Vista de solo lectura. Solo el responsable, un editor compartido o la administración puede modificar."
      }
    >
      <form onSubmit={submit} className="mt-5 space-y-4">
        {error && (
          <ErrorNotice
            message={error}
            onReload={() => {
              void onReload();
              onClose();
            }}
          />
        )}
        <fieldset disabled={busy || !edit} className="space-y-4">
          {kind === "topics" && (
            <>
              <TextField
                label="Nombre del tema"
                name="name"
                value={item?.name}
              />
              {areaField()}
              <TextField
                label="Unidad"
                name="unit"
                value={item?.unit ?? 1}
                type="number"
                min={1}
                max={99}
              />
              {item?.legacyId && (
                <p className="text-xs text-slate-500">
                  Identificador de catálogo: {item.legacyId} (no modificable)
                </p>
              )}
            </>
          )}
          {kind === "sessions" && (
            <>
              <Field label="Tema">
                {(id) => (
                  <Select
                    id={id}
                    name="topicId"
                    defaultValue={item?.topicId || editor.topicId || ""}
                    required
                    disabled={!!item}
                  >
                    <option value="" disabled>
                      Selecciona un tema
                    </option>
                    {data.topics
                      .filter(
                        (t) =>
                          item?.topicId === t.id ||
                          (canEdit(
                            user,
                            data.areas.find((a) => a.id === t.areaId),
                          ) &&
                            !data.sessions.some((s) => s.topicId === t.id)),
                      )
                      .map((t) => (
                        <option value={t.id} key={t.id}>
                          {data.areas.find((a) => a.id === t.areaId)?.name} · U
                          {t.unit} · {t.name}
                        </option>
                      ))}
                  </Select>
                )}
              </Field>
              <div className="grid gap-4 sm:grid-cols-2">
                <Field label="Mes asignado">
                  {(id) => (
                    <Select
                      id={id}
                      name="assignment"
                      defaultValue={
                        item
                          ? (item.assignment ?? "")
                          : months[Number(editor.date?.slice(5, 7)) - 1] ||
                            months[2]
                      }
                    >
                      <option value="">Sin asignar</option>
                      {months.slice(2).map((m) => (
                        <option key={m}>{m}</option>
                      ))}
                    </Select>
                  )}
                </Field>
                <TextField
                  label="Fecha exacta"
                  name="date"
                  type="date"
                  value={item?.exactDate ?? editor.date ?? ""}
                  required={false}
                  min={`${data.plan.year}-01-01`}
                  max={`${data.plan.year}-12-31`}
                />
              </div>
              <Field label="Estado">
                {(id) => (
                  <Select
                    id={id}
                    name="status"
                    defaultValue={item?.status ?? ""}
                  >
                    <option value="">Pendiente</option>
                    <option value="Trabajado">Trabajado</option>
                  </Select>
                )}
              </Field>
              <Field label="Notas">
                {(id) => (
                  <Textarea
                    id={id}
                    name="notes"
                    defaultValue={item?.notes ?? ""}
                    maxLength={100000}
                  />
                )}
              </Field>
              <p className="text-xs text-slate-500">
                Un tema tiene una sesión anual. Mes y fecha son independientes;
                cualquier diferencia se señalará, no se corregirá
                automáticamente.
              </p>
            </>
          )}
          {kind === "areas" && (
            <>
              <TextField
                label="Nombre del área"
                name="name"
                value={item?.name}
                maxLength={200}
              />
              <Field label="Docente responsable">
                {(id) => (
                  <Select
                    id={id}
                    name="ownerId"
                    defaultValue={item?.ownerId || user.id}
                  >
                    {data.teachers.map((t) => (
                      <option key={t.id} value={t.id}>
                        {t.username}
                      </option>
                    ))}
                  </Select>
                )}
              </Field>
              <label className="flex items-start gap-3 text-sm">
                <input
                  type="checkbox"
                  name="sharedEdit"
                  defaultChecked={item?.sharedEdit}
                  className="mt-1"
                />
                <span>
                  Permitir edición compartida por todos los docentes de esta
                  escuela
                </span>
              </label>
            </>
          )}
          {kind === "schedules" && (
            <>
              {areaField()}
              <Field label="Día de la semana">
                {(id) => (
                  <Select
                    name="weekday"
                    id={id}
                    defaultValue={item?.weekday ?? 1}
                  >
                    {weekdays.map((d, i) => (
                      <option value={i + 1} key={d}>
                        {d}
                      </option>
                    ))}
                  </Select>
                )}
              </Field>
              <div className="grid grid-cols-2 gap-4">
                <TextField
                  label="Inicio"
                  name="startTime"
                  type="time"
                  value={item?.startTime ?? "08:00"}
                />
                <TextField
                  label="Fin"
                  name="endTime"
                  type="time"
                  value={item?.endTime ?? "09:30"}
                />
              </div>
            </>
          )}
          {kind === "blocks" && (
            <>
              <TextField
                label="Nombre del evento"
                name="label"
                value={item?.label}
                maxLength={200}
              />
              {areaField(true)}
              <Field label="Tipo">
                {(id) => (
                  <Select
                    id={id}
                    name="kind"
                    defaultValue={item?.kind ?? "EXAM"}
                  >
                    {Object.entries(blockKinds).map(([k, v]) => (
                      <option value={k} key={k}>
                        {v}
                      </option>
                    ))}
                  </Select>
                )}
              </Field>
              <div className="grid gap-4 sm:grid-cols-2">
                <TextField
                  label="Desde (inclusive)"
                  name="startDate"
                  type="date"
                  value={item?.startDate ?? editor.date}
                  min={`${data.plan.year}-01-01`}
                  max={`${data.plan.year}-12-31`}
                />
                <TextField
                  label="Hasta (inclusive)"
                  name="endDate"
                  type="date"
                  value={item?.endDate ?? editor.date}
                  min={`${data.plan.year}-01-01`}
                  max={`${data.plan.year}-12-31`}
                />
              </div>
              <p className="rounded-lg bg-amber-50 p-3 text-xs text-amber-900">
                Un bloqueo no elimina sesiones existentes. Los solapamientos se
                mostrarán como conflictos. No se asumen fechas de exámenes ni
                feriados.
              </p>
            </>
          )}
          {kind === "users" && (
            <>
              <TextField label="Usuario" name="username" maxLength={100} />
              <TextField
                label="Contraseña inicial"
                name="password"
                type="password"
                hint="Mínimo 12 caracteres y máximo 72 bytes UTF-8. No se guarda en este navegador."
                maxLength={72}
              />
              <Field label="Rol">
                {(id) => (
                  <Select id={id} name="role" defaultValue="TEACHER">
                    <option value="TEACHER">Docente</option>
                    <option value="ADMIN">Administrador</option>
                  </Select>
                )}
              </Field>
            </>
          )}
        </fieldset>
        {confirmDelete ? (
          <div
            role="alert"
            className="rounded-lg border border-red-200 bg-red-50 p-4 text-sm"
          >
            <p>
              ¿Eliminar este {names[kind]}? Esta acción no se puede deshacer.
            </p>
            <div className="mt-3 flex gap-2">
              <Button
                variant="destructive"
                disabled={busy}
                onClick={remove}
                type="button"
              >
                Sí, eliminar
              </Button>
              <Button
                variant="outline"
                type="button"
                onClick={() => setConfirmDelete(false)}
              >
                Cancelar
              </Button>
            </div>
          </div>
        ) : (
          <div className="flex flex-wrap items-center justify-between gap-2 border-t pt-4">
            {item && edit ? (
              <Button
                variant="ghost"
                type="button"
                className="text-red-700"
                onClick={() => setConfirmDelete(true)}
              >
                Eliminar
              </Button>
            ) : (
              <span />
            )}
            <div className="flex gap-2">
              <Button
                variant="outline"
                type="button"
                onClick={onClose}
                disabled={busy}
              >
                Cerrar
              </Button>
              {edit && (
                <Button type="submit" disabled={busy}>
                  {busy ? "Guardando…" : "Guardar cambios"}
                </Button>
              )}
            </div>
          </div>
        )}
      </form>
    </Dialog>
  );
}
