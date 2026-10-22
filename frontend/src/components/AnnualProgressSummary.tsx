import { useId } from "react";
import type { AnnualCoverage, CoverageCounts } from "../lib/coverage";

interface AnnualCoverageSummaryProps {
  coverage: AnnualCoverage;
  contextLabel: string;
}

const percentFormatter = new Intl.NumberFormat("es-PE", {
  maximumFractionDigits: 1,
});

function CoverageCells({ counts }: { counts: CoverageCounts }) {
  return (
    <>
      <td className="p-3">
        {counts.total}
        {counts.total === 0 && <span className="block text-sm">Sin temas</span>}
      </td>
      <td className="p-3">{counts.completed}</td>
      <td className="p-3">{counts.planned}</td>
      <td className="p-3">{counts.unplanned}</td>
      <td className="p-3">
        {counts.completionPercent === null
          ? "Sin porcentaje"
          : `${percentFormatter.format(counts.completionPercent)} %`}
      </td>
    </>
  );
}

export function AnnualProgressSummary({
  coverage,
  contextLabel,
}: AnnualCoverageSummaryProps) {
  const titleId = useId();
  return (
    <section aria-labelledby={titleId} className="space-y-3">
      <h2 id={titleId} className="text-lg font-semibold">
        Avance curricular anual
      </h2>
      <p>{contextLabel}</p>
      <p className="text-sm">
        El avance corresponde a los temas trabajados del catálogo anual
        completo.
      </p>
      {coverage.areas.length === 0 && (
        <p>No hay áreas en este contexto académico.</p>
      )}
      <div className="overflow-x-auto rounded-lg border">
        <table className="w-full text-left text-sm">
          <caption className="p-3 text-left font-medium">
            Resumen anual por área
          </caption>
          <thead>
            <tr className="border-b">
              {[
                "Área",
                "Total de temas",
                "Trabajados",
                "Planificados",
                "Sin planificar",
                "Avance",
              ].map((label) => (
                <th key={label} scope="col" className="p-3">
                  {label}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {coverage.areas.map((summary) => (
              <tr key={summary.area.id} className="border-b">
                <th scope="row" className="p-3 font-medium">
                  {summary.area.name}
                </th>
                <CoverageCells counts={summary} />
              </tr>
            ))}
          </tbody>
          <tfoot>
            <tr className="font-semibold">
              <th scope="row" className="p-3">
                Total anual
              </th>
              <CoverageCells counts={coverage.totals} />
            </tr>
          </tfoot>
        </table>
      </div>
    </section>
  );
}