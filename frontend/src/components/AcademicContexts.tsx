import { useEffect, useRef, useState, type FormEvent } from "react";
import { api, errorMessage } from "../lib/api";
import type { AcademicContext, AcademicLevel } from "../lib/types";
import { Button } from "./ui/button";
import { Select, Input } from "./ui/input";
import { ErrorNotice } from "./Feedback";
const levels: Record<AcademicLevel, string> = {
  INITIAL: "Inicial",
  PRIMARY: "Primaria",
  SECONDARY: "Secundaria",
};
export function stageLabel(level: AcademicLevel, value: number) {
  return level === "INITIAL"
    ? value === 0
      ? "Menores de 1 año"
      : `${value} ${value === 1 ? "año" : "años"}`
    : `${value}° grado`;
}
export function AcademicContexts({
  contexts,
  selected,
  admin,
  onSelect,
  onCreated,
}: {
  contexts: AcademicContext[];
  selected: string;
  admin: boolean;
  onSelect: (id: string) => void;
  onCreated: (context: AcademicContext) => void;
}) {
  const current = contexts.find((c) => c.id === selected);
  const [year, setYear] = useState(current?.year ?? new Date().getFullYear());
  const [level, setLevel] = useState<AcademicLevel>("PRIMARY");
  const [stage, setStage] = useState(1);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const active = useRef(true);
  const selectionEpoch = useRef(0);
  useEffect(() => {
    ++selectionEpoch.current;
    setError("");
  }, [selected]);
  useEffect(() => {
    active.current = true;
    return () => {
      active.current = false;
    };
  }, []);
  const years = [...new Set(contexts.map((c) => c.year))].sort((a, b) => a - b);
  const atYear = contexts.filter((c) => c.year === current?.year);
  const atLevel = atYear.filter((c) => c.level === current?.level);
  async function create(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const selection = selectionEpoch.current;
    setBusy(true);
    setError("");
    try {
      const existing = contexts.find(
        (c) => c.year === year && c.level === level && c.stageValue === stage,
      );
      if (existing) {
        onSelect(existing.id);
        return;
      }
      const result = await api.mutate<AcademicContext>("/contexts", "POST", {
        year,
        level,
        stageValue: stage,
      });
      if (active.current) onCreated(result);
    } catch (e) {
      if (active.current && selection === selectionEpoch.current)
        setError(errorMessage(e));
    } finally {
      if (active.current) setBusy(false);
    }
  }
  return (
    <section
      aria-label="Plan académico"
      className="mb-6 rounded-xl border border-slate-200 bg-white p-4"
    >
      <p className="mb-3 text-sm font-semibold">
        Plan académico · cada año y etapa tiene sus propios temas
      </p>
      {contexts.length > 0 ? (
        <div className="grid gap-3 sm:grid-cols-3">
          <label className="text-xs">
            Año del plan
            <Select
              aria-label="Año del plan"
              value={current?.year ?? ""}
              onChange={(e) => {
                const next =
                  contexts.find(
                    (c) =>
                      c.year === Number(e.target.value) &&
                      c.level === current?.level &&
                      c.stageValue === current?.stageValue,
                  ) ?? contexts.find((c) => c.year === Number(e.target.value));
                if (next) onSelect(next.id);
              }}
            >
              {years.map((y) => (
                <option key={y}>{y}</option>
              ))}
            </Select>
          </label>
          <label className="text-xs">
            Nivel del plan
            <Select
              aria-label="Nivel del plan"
              value={current?.level ?? ""}
              onChange={(e) => {
                const next =
                  atYear.find(
                    (c) =>
                      c.level === e.target.value &&
                      c.stageValue === current?.stageValue,
                  ) ?? atYear.find((c) => c.level === e.target.value);
                if (next) onSelect(next.id);
              }}
            >
              {Object.entries(levels)
                .filter(([l]) => atYear.some((c) => c.level === l))
                .map(([l, label]) => (
                  <option key={l} value={l}>
                    {label}
                  </option>
                ))}
            </Select>
          </label>
          <label className="text-xs">
            {current?.level === "INITIAL" ? "Edad del plan" : "Grado del plan"}
            <Select
              aria-label={
                current?.level === "INITIAL"
                  ? "Edad del plan"
                  : "Grado del plan"
              }
              value={selected}
              onChange={(e) => onSelect(e.target.value)}
            >
              {atLevel.map((c) => (
                <option key={c.id} value={c.id}>
                  {stageLabel(c.level, c.stageValue)}
                </option>
              ))}
            </Select>
          </label>
        </div>
      ) : (
        <p className="text-sm">
          Todavía no hay planes. La administración puede crear el primero.
        </p>
      )}
      {current && (
        <p className="mt-2 text-xs text-slate-500">
          {current.displayLabel}
          {current.cycle ? ` · Ciclo ${current.cycle}` : ""}
        </p>
      )}
      {admin && (
        <details className="mt-3">
          <summary className="cursor-pointer text-sm font-semibold text-teal-800">
            Crear plan para otro año, nivel o etapa
          </summary>
          <form onSubmit={create} className="mt-3 space-y-3">
            <p className="text-xs text-slate-500">
              El nuevo plan empieza vacío. No copia áreas, temas ni fechas de
              otro plan.
            </p>
            {error && <ErrorNotice message={error} />}
            <fieldset disabled={busy} className="grid gap-3 sm:grid-cols-3">
              <label className="text-xs">
                Año del nuevo plan
                <Input
                  aria-label="Año del nuevo plan"
                  type="number"
                  min={1900}
                  max={2200}
                  required
                  value={year}
                  onChange={(e) => setYear(Number(e.target.value))}
                />
              </label>
              <label className="text-xs">
                Nivel del nuevo plan
                <Select
                  aria-label="Nivel del nuevo plan"
                  value={level}
                  onChange={(e) => {
                    setLevel(e.target.value as AcademicLevel);
                    setStage(e.target.value === "INITIAL" ? 0 : 1);
                  }}
                >
                  {Object.entries(levels).map(([l, label]) => (
                    <option key={l} value={l}>
                      {label}
                    </option>
                  ))}
                </Select>
              </label>
              <label className="text-xs">
                {level === "INITIAL"
                  ? "Edad del nuevo plan"
                  : "Grado del nuevo plan"}
                <Select
                  aria-label={
                    level === "INITIAL"
                      ? "Edad del nuevo plan"
                      : "Grado del nuevo plan"
                  }
                  value={stage}
                  onChange={(e) => setStage(Number(e.target.value))}
                >
                  {Array.from(
                    { length: level === "SECONDARY" ? 5 : 6 },
                    (_, i) => (level === "INITIAL" ? i : i + 1),
                  ).map((n) => (
                    <option key={n} value={n}>
                      {stageLabel(level, n)}
                    </option>
                  ))}
                </Select>
              </label>
            </fieldset>
            <Button type="submit" disabled={busy}>
              {busy ? "Creando…" : "Crear plan vacío"}
            </Button>
          </form>
        </details>
      )}
    </section>
  );
}
