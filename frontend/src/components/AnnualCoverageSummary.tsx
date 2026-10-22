import { useId } from "react";
import {
  BarChart3,
  CalendarClock,
  CheckCheck,
  CircleDashed,
} from "lucide-react";
import type { AnnualCoverage, CoverageCounts } from "../lib/coverage";
import { Card } from "./ui/card";

interface AnnualCoverageSummaryProps {
  coverage: AnnualCoverage;
  contextLabel: string;
}

const percentFormatter = new Intl.NumberFormat("es-PE", {
  maximumFractionDigits: 1,
});

function formatPercent(percent: number | null) {
  return percent === null
    ? "Sin porcentaje"
    : `${percentFormatter.format(percent)} %`;
}

function CoverageCells({ counts }: { counts: CoverageCounts }) {
  return (
    <>
      <td className="num">
        {counts.total}
        {counts.total === 0 && <small className="block">Sin temas</small>}
      </td>
      <td className="num">{counts.completed}</td>
      <td className="num">{counts.planned}</td>
      <td className="num">{counts.unplanned}</td>
      <td className="num coverage-progress">
        {formatPercent(counts.completionPercent)}
        {counts.completionPercent !== null && (
          <div
            aria-hidden="true"
            className="mt-1.5 h-1.5 w-full overflow-hidden rounded-full bg-slate-100"
          >
            <div
              className="h-full rounded-full bg-teal-600"
              style={{ width: `${Math.min(counts.completionPercent, 100)}%` }}
            />
          </div>
        )}
      </td>
    </>
  );
}

export function AnnualCoverageSummary({
  coverage,
  contextLabel,
}: AnnualCoverageSummaryProps) {
  const titleId = useId();
  const { totals } = coverage;
  return (
    <section aria-labelledby={titleId} className="space-y-5">
      <div>
        <div className="flex flex-wrap items-center justify-between gap-2">
          <h2 id={titleId} className="text-lg font-semibold text-slate-900">
            Avance curricular anual
          </h2>
          <p className="rounded-full bg-teal-50 px-3 py-1 text-xs font-medium text-teal-700">
            {contextLabel}
          </p>
        </div>
        <p className="mt-1 text-xs text-slate-500">
          El avance corresponde a los temas trabajados del catálogo anual
          completo.
        </p>
      </div>
      <div className="stats-grid">
        <Card className="stat-card">
          <span className="stat-icon bg-teal-50 text-teal-700">
            <CheckCheck size={21} />
          </span>
          <div>
            <p className="stat-label">Avance anual</p>
            <p
              className={`stat-value tabular-nums ${totals.completionPercent === null ? "coverage-stat-empty" : ""}`}
            >
              {formatPercent(totals.completionPercent)}
              <span>
                {totals.completed} de {totals.total} temas trabajados
              </span>
            </p>
          </div>
        </Card>
        <Card className="stat-card">
          <span className="stat-icon bg-indigo-50 text-indigo-600">
            <CalendarClock size={21} />
          </span>
          <div>
            <p className="stat-label">Planificados</p>
            <p className="stat-value tabular-nums">
              {totals.planned}
              <span>con sesión programada</span>
            </p>
          </div>
        </Card>
        <Card className="stat-card">
          <span className="stat-icon bg-amber-50 text-amber-600">
            <CircleDashed size={21} />
          </span>
          <div>
            <p className="stat-label">Sin planificar</p>
            <p className="stat-value tabular-nums">
              {totals.unplanned}
              <span>aún sin sesión</span>
            </p>
          </div>
        </Card>
      </div>
      <Card className="overflow-hidden">
        <div className="section-heading">
          <h3
            id={`${titleId}-table`}
            className="flex items-center gap-2 text-sm font-semibold"
          >
            <BarChart3 size={17} className="text-[#527b83]" />
            Resumen anual por área
          </h3>
        </div>
        {coverage.areas.length === 0 && (
          <p className="border-b border-slate-100 px-5 py-4 text-sm text-slate-500">
            No hay áreas en este contexto académico.
          </p>
        )}
        <div className="table-scroll">
          <table
            aria-labelledby={`${titleId}-table`}
            className="coverage-table coverage-summary-table"
          >
            <thead>
              <tr>
                <th scope="col">Área</th>
                {[
                  "Total de temas",
                  "Trabajados",
                  "Planificados",
                  "Sin planificar",
                  "Avance",
                ].map((label) => (
                  <th key={label} scope="col" className="num">
                    {label}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {coverage.areas.map((summary) => (
                <tr key={summary.area.id}>
                  <th scope="row">{summary.area.name}</th>
                  <CoverageCells counts={summary} />
                </tr>
              ))}
            </tbody>
            <tfoot>
              <tr>
                <th scope="row">Total anual</th>
                <CoverageCells counts={totals} />
              </tr>
            </tfoot>
          </table>
        </div>
      </Card>
    </section>
  );
}
