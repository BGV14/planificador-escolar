import { useId, useState } from "react";
import { ListChecks } from "lucide-react";
import type { AnnualCoverage, CoverageStatus } from "../lib/coverage";
import { Empty } from "./Feedback";
import { Card } from "./ui/card";
import { Select } from "./ui/input";

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

const statusBadges: Record<CoverageStatus, string> = {
  completed: "success",
  planned: "",
  unplanned: "warning",
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
    <section aria-labelledby={`${id}-title`}>
      <Card className="overflow-hidden">
        <div className="section-heading flex-wrap">
          <div>
            <h2 id={`${id}-title`}>
              <ListChecks size={17} />
              Detalle del avance anual
            </h2>
            <p className="mt-1 text-xs text-slate-500">{contextLabel}</p>
          </div>
          <div className="flex flex-wrap items-end gap-3">
            <label
              htmlFor={`${id}-area`}
              className="grid gap-1 text-xs text-slate-500"
            >
              Área
              <Select
                id={`${id}-area`}
                value={selectedArea}
                onChange={(event) => setAreaId(event.target.value)}
                className="w-auto min-w-40"
              >
                <option value="">Todas las áreas</option>
                {coverage.areas.map(({ area }) => (
                  <option key={area.id} value={area.id}>
                    {area.name}
                  </option>
                ))}
              </Select>
            </label>
            <label
              htmlFor={`${id}-status`}
              className="grid gap-1 text-xs text-slate-500"
            >
              Estado
              <Select
                id={`${id}-status`}
                value={status}
                onChange={(event) =>
                  setStatus(event.target.value as CoverageStatus | "")
                }
                className="w-auto min-w-40"
              >
                <option value="">Todos los estados</option>
                {Object.entries(statusLabels).map(([value, label]) => (
                  <option key={value} value={value}>
                    {label}
                  </option>
                ))}
              </Select>
            </label>
          </div>
        </div>
        <p
          role="status"
          className="border-b border-slate-100 px-5 py-2.5 text-xs text-slate-500"
        >
          {rows.length} de {coverage.totals.total} temas del catálogo anual
        </p>
        {coverage.totals.total === 0 ? (
          <Empty
            title="No hay temas en el catálogo anual."
            detail="Agrega temas al catálogo para ver su avance."
          />
        ) : rows.length === 0 ? (
          <Empty
            title="No hay temas que coincidan con los filtros."
            detail="Cambia el área o el estado para ver más temas."
          />
        ) : (
          <div className="table-scroll">
            <table className="coverage-table coverage-details-table">
              <caption className="sr-only">
                Detalle de temas del catálogo anual
              </caption>
              <thead>
                <tr>
                  {["Área", "Unidad", "Tema", "Estado"].map((label) => (
                    <th key={label} scope="col">
                      {label}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {rows.map(({ area, topic, status: topicStatus }) => (
                  <tr key={`${area.id}:${topic.id}`}>
                    <td className="text-slate-600">{area.name}</td>
                    <td className="tabular-nums text-slate-500">
                      {topic.unit}
                    </td>
                    <td className="coverage-topic font-medium text-slate-900">
                      {topic.name}
                    </td>
                    <td>
                      <span className={`badge ${statusBadges[topicStatus]}`}>
                        {statusLabels[topicStatus]}
                      </span>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>
    </section>
  );
}
