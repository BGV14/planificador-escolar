import { useId, useState } from "react";
import type { AnnualCoverage, CoverageStatus } from "../lib/coverage";

interface AnnualCoverageDetailsProps {
  coverage: AnnualCoverage;
  contextLabel: string;
  contextKey: string;
}

const statusLabels: Record<CoverageStatus, string> = {
  completed: "Trabajado",
  planned: "Planificado",
  unplanned: "Sin planificar",
};

export function AnnualCoverageDetails(props: AnnualCoverageDetailsProps) {
  // A new academic context must never inherit filters, even with reused IDs.
  return <ContextDetails key={props.contextKey} {...props} />;
}

function ContextDetails({
  coverage,
  contextLabel,
}: AnnualCoverageDetailsProps) {
  const id = useId();
  const [areaId, setAreaId] = useState("");
  const [status, setStatus] = useState<CoverageStatus | "">("");
  const selectedArea = coverage.areas.some(({ area }) => area.id === areaId)
    ? areaId
    : "";
  // Normalize during render so a removed selection cannot return on refresh.
  if (areaId !== selectedArea) setAreaId(selectedArea);

  const rows = coverage.areas.flatMap(({ area, topics }) =>
    topics
      .filter(
        (entry) =>
          (!selectedArea || area.id === selectedArea) &&
          (!status || entry.status === status),
      )
      .map((entry) => ({ area, ...entry })),
  );

  return (
    <section aria-labelledby={`${id}-title`} className="space-y-3">
      <h2 id={`${id}-title`} className="text-lg font-semibold">
        Detalle del avance anual
      </h2>
      <p>{contextLabel}</p>
      <div className="flex flex-wrap gap-3">
        <label htmlFor={`${id}-area`}>
          Área
          <select
            id={`${id}-area`}
            value={selectedArea}
            onChange={(event) => setAreaId(event.target.value)}
            className="ml-2 rounded border p-2"
          >
            <option value="">Todas las áreas</option>
            {coverage.areas.map(({ area }) => (
              <option key={area.id} value={area.id}>
                {area.name}
              </option>
            ))}
          </select>
        </label>
        <label htmlFor={`${id}-status`}>
          Estado
          <select
            id={`${id}-status`}
            value={status}
            onChange={(event) =>
              setStatus(event.target.value as CoverageStatus | "")
            }
            className="ml-2 rounded border p-2"
          >
            <option value="">Todos los estados</option>
            {Object.entries(statusLabels).map(([value, label]) => (
              <option key={value} value={value}>
                {label}
              </option>
            ))}
          </select>
        </label>
      </div>
      <p role="status">
        {rows.length} de {coverage.totals.total} temas del catálogo anual
      </p>
      {coverage.totals.total === 0 ? (
        <p>No hay temas en el catálogo anual.</p>
      ) : rows.length === 0 ? (
        <p>No hay temas que coincidan con los filtros.</p>
      ) : (
        <div className="overflow-x-auto rounded-lg border">
          <table className="w-full text-left text-sm">
            <caption className="p-3 text-left font-medium">
              Detalle de temas del catálogo anual
            </caption>
            <thead>
              <tr className="border-b">
                {["Área", "Unidad", "Tema", "Estado"].map((label) => (
                  <th key={label} scope="col" className="p-3">
                    {label}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {rows.map(({ area, topic, status: topicStatus }) => (
                <tr key={`${area.id}:${topic.id}`} className="border-b">
                  <td className="p-3">{area.name}</td>
                  <td className="p-3">{topic.unit}</td>
                  <td className="p-3">{topic.name}</td>
                  <td className="p-3">{statusLabels[topicStatus]}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}
