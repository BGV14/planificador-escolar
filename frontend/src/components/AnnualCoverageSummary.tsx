import { AnnualProgressSummary } from "./AnnualProgressSummary";
import { useId } from "react";
import { CalendarClock, CheckCheck, CircleDashed } from "lucide-react";
import type { AnnualCoverage } from "../lib/coverage";
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
        {coverage.areas.length === 0 && (
          <p className="px-5 py-4 text-sm text-slate-500">
            No hay áreas en este contexto académico.
          </p>
        )}
        <AnnualProgressSummary
          coverage={coverage}
          contextLabel={contextLabel}
          tableOnly
        />
      </Card>
    </section>
  );
}
