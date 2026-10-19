import { useState, useEffect } from "react";
import {
  CheckCircle2,
  Clock,
  CalendarX,
  Layers,
  Search,
  Filter,
  Eye,
  Info,
  Calendar,
  BookOpen,
} from "lucide-react";
import { api, errorMessage } from "../lib/api";
import type {
  AnnualProgressData,
  AnnualTopicItem,
  TopicAnnualStatus,
} from "../lib/progressTypes";
import { Card } from "./ui/card";
import { Button } from "./ui/button";
import { Input, Select } from "./ui/input";
import { Dialog } from "./ui/dialog";
import { EmptyState } from "./EmptyState";
import { ErrorNotice, Loading } from "./Feedback";

export function AnnualProgress({
  contextId,
  onSelectSession,
}: {
  contextId: string;
  onSelectSession?: (topicId: string) => void;
}) {
  const [data, setData] = useState<AnnualProgressData | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  // Filtros activos
  const [areaFilter, setAreaFilter] = useState("");
  const [statusFilter, setStatusFilter] = useState<string>("");
  const [searchQuery, setSearchQuery] = useState("");

  // Vista de detalle del tema
  const [selectedTopic, setSelectedTopic] = useState<AnnualTopicItem | null>(null);

  // CRITERION 5: Cambio de contexto académico
  // Al cambiar de contextId, se deben restablecer automáticamente los filtros activos
  // y la vista de detalle. No debe mostrarse información del contexto anterior.
  useEffect(() => {
    // 1. Restablecer inmediatamente filtros, vista de detalle y datos del contexto anterior
    setAreaFilter("");
    setStatusFilter("");
    setSearchQuery("");
    setSelectedTopic(null);
    setData(null);
    setError("");

    if (!contextId) return;

    let active = true;
    setLoading(true);

    const fetchProgress = async () => {
      try {
        // Asegurar la inicialización del token CSRF mediante el cliente global
        await api.ensureCsrf().catch(() => {
          /* Continuar para permitir que el servidor responda si no requiere pre-fetch */
        });
        const res = await api.get<AnnualProgressData>(
          `/progress/annual?contextId=${encodeURIComponent(contextId)}`,
        );
        if (active) {
          setData(res);
        }
      } catch (err) {
        if (active) {
          setError(errorMessage(err));
        }
      } finally {
        if (active) {
          setLoading(false);
        }
      }
    };

    void fetchProgress();

    return () => {
      active = false;
    };
  }, [contextId]);

  const handleResetFilters = () => {
    setAreaFilter("");
    setStatusFilter("");
    setSearchQuery("");
  };

  const handleReload = () => {
    if (!contextId) return;
    setLoading(true);
    setError("");
    // Si venció el token CSRF o la sesión, refrescar el token antes de reintentar
    api
      .refreshCsrf()
      .catch(() => {})
      .then(() =>
        api.get<AnnualProgressData>(
          `/progress/annual?contextId=${encodeURIComponent(contextId)}`,
        ),
      )
      .then(setData)
      .catch((err) => setError(errorMessage(err)))
      .finally(() => setLoading(false));
  };

  if (loading) {
    return <Loading />;
  }

  if (error) {
    return (
      <div className="space-y-4">
        <div>
          <h2 className="text-xl font-bold text-slate-900">
            Avance anual de temas
          </h2>
          <p className="text-xs text-slate-500">
            Consulta de avance curricular y estado de temas
          </p>
        </div>
        <ErrorNotice message={error} onReload={handleReload} />
      </div>
    );
  }

  if (!data) {
    return null;
  }

  // Filtrado de la lista en memoria según los criterios seleccionados
  const filteredTopics = data.topics.filter((topic) => {
    if (areaFilter && topic.areaId !== areaFilter) return false;
    if (statusFilter && topic.status !== statusFilter) return false;
    if (
      searchQuery &&
      !topic.name
        .toLocaleLowerCase("es")
        .includes(searchQuery.toLocaleLowerCase("es"))
    )
      return false;
    return true;
  });

  const isFiltered = Boolean(areaFilter || statusFilter || searchQuery);

  const getStatusBadge = (status: TopicAnnualStatus) => {
    switch (status) {
      case "COMPLETADO":
        return (
          <span className="inline-flex items-center gap-1 rounded-full bg-teal-100 px-2.5 py-0.5 text-xs font-medium text-teal-800">
            <CheckCircle2 size={12} className="text-teal-600" />
            Completado (Trabajado)
          </span>
        );
      case "PLANIFICADO_PENDIENTE":
        return (
          <span className="inline-flex items-center gap-1 rounded-full bg-blue-100 px-2.5 py-0.5 text-xs font-medium text-blue-800">
            <Clock size={12} className="text-blue-600" />
            Planificado pendiente
          </span>
        );
      case "NO_PLANIFICADO":
        return (
          <span className="inline-flex items-center gap-1 rounded-full bg-amber-100 px-2.5 py-0.5 text-xs font-medium text-amber-800">
            <CalendarX size={12} className="text-amber-600" />
            No planificado
          </span>
        );
    }
  };

  return (
    <div className="space-y-6">
      {/* Título de la vista y contexto */}
      <div>
        <h2 className="text-xl font-bold text-slate-900">
          Avance anual de temas
        </h2>
        <p className="text-xs text-slate-500">
          Contexto académico: {data.displayLabel} · Año lectivo {data.year}
        </p>
      </div>

      {/* CRITERION 4: Coherencia en conteos
          Los filtros no deben redefinir el denominador anual de todos los temas.
          El conteo total general de la escuela/contexto debe permanecer constante. */}
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
        {/* Total Temas Anuales (Denominador Constante) */}
        <Card className="p-4">
          <div className="flex items-center gap-3">
            <span className="flex h-10 w-10 items-center justify-center rounded-xl bg-slate-100 text-slate-700">
              <Layers size={20} />
            </span>
            <div>
              <p className="text-xs font-medium text-slate-500">
                Total anual del plan
              </p>
              <p
                data-testid="kpi-total-topics"
                className="text-2xl font-bold text-slate-900"
              >
                {data.summary.totalTopics}
              </p>
              <p className="text-[11px] text-slate-400">
                Denominador anual constante
              </p>
            </div>
          </div>
        </Card>

        {/* Completados (Trabajados) */}
        <Card className="p-4">
          <div className="flex items-center gap-3">
            <span className="flex h-10 w-10 items-center justify-center rounded-xl bg-teal-50 text-teal-700">
              <CheckCircle2 size={20} />
            </span>
            <div>
              <p className="text-xs font-medium text-slate-500">
                Temas completados
              </p>
              <p className="text-2xl font-bold text-teal-700">
                {data.summary.completedTopics}
                <span className="ml-1 text-sm font-normal text-slate-400">
                  / {data.summary.totalTopics}
                </span>
              </p>
              <p className="text-[11px] text-teal-600 font-medium">
                {data.summary.completionPercentage}% de avance anual
              </p>
            </div>
          </div>
        </Card>

        {/* Planificados Pendientes */}
        <Card className="p-4">
          <div className="flex items-center gap-3">
            <span className="flex h-10 w-10 items-center justify-center rounded-xl bg-blue-50 text-blue-700">
              <Clock size={20} />
            </span>
            <div>
              <p className="text-xs font-medium text-slate-500">
                Planificados pendientes
              </p>
              <p className="text-2xl font-bold text-blue-700">
                {data.summary.plannedPendingTopics}
                <span className="ml-1 text-sm font-normal text-slate-400">
                  / {data.summary.totalTopics}
                </span>
              </p>
              <p className="text-[11px] text-slate-400">
                Con mes asignado sin trabajar
              </p>
            </div>
          </div>
        </Card>

        {/* No Planificados */}
        <Card className="p-4">
          <div className="flex items-center gap-3">
            <span className="flex h-10 w-10 items-center justify-center rounded-xl bg-amber-50 text-amber-700">
              <CalendarX size={20} />
            </span>
            <div>
              <p className="text-xs font-medium text-slate-500">
                No planificados
              </p>
              <p className="text-2xl font-bold text-amber-700">
                {data.summary.unplannedTopics}
                <span className="ml-1 text-sm font-normal text-slate-400">
                  / {data.summary.totalTopics}
                </span>
              </p>
              <p className="text-[11px] text-slate-400">
                Por calendarizar en el año
              </p>
            </div>
          </div>
        </Card>
      </div>

      {/* Barra de Filtros Accesibles */}
      <Card className="p-4">
        <div className="flex flex-wrap items-center justify-between gap-4">
          <div className="flex flex-wrap items-center gap-3">
            {/* Filtro por Área */}
            <div className="flex items-center gap-2">
              <label
                htmlFor="annual-filter-area"
                className="text-xs font-medium text-slate-600 flex items-center gap-1"
              >
                <Filter size={14} className="text-slate-400" />
                Área:
              </label>
              <Select
                id="annual-filter-area"
                aria-label="Filtrar por área"
                value={areaFilter}
                onChange={(e) => setAreaFilter(e.target.value)}
                className="w-44 text-xs"
              >
                <option value="">Todas las áreas</option>
                {data.areaSummaries.map((area) => (
                  <option key={area.areaId} value={area.areaId}>
                    {area.areaName} ({area.total})
                  </option>
                ))}
              </Select>
            </div>

            {/* Filtro por Estado */}
            <div className="flex items-center gap-2">
              <label
                htmlFor="annual-filter-status"
                className="text-xs font-medium text-slate-600"
              >
                Estado:
              </label>
              <Select
                id="annual-filter-status"
                aria-label="Filtrar por estado"
                value={statusFilter}
                onChange={(e) => setStatusFilter(e.target.value)}
                className="w-52 text-xs"
              >
                <option value="">Todos los estados</option>
                <option value="COMPLETADO">Completado (Trabajado)</option>
                <option value="PLANIFICADO_PENDIENTE">
                  Planificado pendiente
                </option>
                <option value="NO_PLANIFICADO">No planificado</option>
              </Select>
            </div>

            {/* Búsqueda por texto */}
            <div className="relative">
              <Search
                size={14}
                className="absolute left-2.5 top-2.5 text-slate-400"
              />
              <Input
                aria-label="Buscar tema por nombre"
                className="pl-8 text-xs w-48"
                placeholder="Buscar tema…"
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
              />
            </div>
          </div>

          {/* Estado de conteo filtrado y reset */}
          <div className="flex items-center gap-2 text-xs text-slate-500">
            <span>
              Mostrando {filteredTopics.length} de {data.summary.totalTopics}{" "}
              temas
            </span>
            {isFiltered && (
              <Button
                variant="ghost"
                size="sm"
                onClick={handleResetFilters}
                className="h-8 text-xs text-indigo-600 hover:text-indigo-800"
              >
                Limpiar filtros
              </Button>
            )}
          </div>
        </div>
      </Card>

      {/* Lista de temas o Estado Vacío */}
      <Card>
        {filteredTopics.length === 0 ? (
          <EmptyState
            title="Sin temas encontrados"
            description="No hay temas que coincidan con los filtros seleccionados."
            onReset={handleResetFilters}
            resetLabel="Restablecer filtros"
          />
        ) : (
          <div className="table-scroll">
            <table>
              <thead>
                <tr>
                  <th>Tema / Unidad</th>
                  <th>Área curricular</th>
                  <th>Mes asignado</th>
                  <th>Fecha trabajada</th>
                  <th>Estado anual</th>
                  <th className="text-right">Detalle</th>
                </tr>
              </thead>
              <tbody>
                {filteredTopics.map((topic) => (
                  <tr key={topic.id}>
                    <td>
                      <p className="font-medium text-slate-800">{topic.name}</p>
                      <small className="text-slate-400">
                        Unidad {topic.unit} · Catálogo anual
                      </small>
                    </td>
                    <td>
                      <span className="text-slate-700">{topic.areaName}</span>
                    </td>
                    <td>
                      {topic.assignment ? (
                        <span className="font-medium text-slate-700">
                          {topic.assignment}
                        </span>
                      ) : (
                        <span className="text-slate-400 italic">
                          Sin asignar
                        </span>
                      )}
                    </td>
                    <td>
                      {topic.exactDate ? (
                        <span className="text-slate-700">
                          {topic.exactDate}
                        </span>
                      ) : (
                        <span className="text-slate-400 italic">
                          Sin registrar
                        </span>
                      )}
                    </td>
                    <td>{getStatusBadge(topic.status)}</td>
                    <td className="text-right">
                      <Button
                        variant="ghost"
                        size="sm"
                        aria-label={`Ver detalle de ${topic.name}`}
                        onClick={() => setSelectedTopic(topic)}
                        className="gap-1.5 text-xs text-slate-600 hover:text-slate-900"
                      >
                        <Eye size={14} />
                        Ver detalle
                      </Button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>

      {/* Modal / Dialog de Detalle del Tema */}
      {selectedTopic && (
        <Dialog
          open={Boolean(selectedTopic)}
          onOpenChange={(open) => !open && setSelectedTopic(null)}
          title="Detalle del tema"
          description="Información detallada del estado anual y asignación curricular."
        >
          <div className="mt-4 space-y-4">
            <div className="rounded-xl bg-slate-50 p-4 border border-slate-100">
              <h3 className="text-base font-semibold text-slate-900">
                {selectedTopic.name}
              </h3>
              <p className="mt-1 text-xs text-slate-500">
                Área: {selectedTopic.areaName} · Unidad {selectedTopic.unit}
              </p>
            </div>

            <div className="grid grid-cols-2 gap-4">
              <div className="rounded-lg border p-3">
                <span className="text-xs font-medium text-slate-400 flex items-center gap-1">
                  <Info size={13} /> Estado anual
                </span>
                <div className="mt-2">{getStatusBadge(selectedTopic.status)}</div>
              </div>

              <div className="rounded-lg border p-3">
                <span className="text-xs font-medium text-slate-400 flex items-center gap-1">
                  <Calendar size={13} /> Mes de planificación
                </span>
                <p className="mt-2 text-sm font-semibold text-slate-800">
                  {selectedTopic.assignment || "Sin asignar"}
                </p>
              </div>

              <div className="rounded-lg border p-3">
                <span className="text-xs font-medium text-slate-400 flex items-center gap-1">
                  <Calendar size={13} /> Fecha exacta trabajada
                </span>
                <p className="mt-2 text-sm font-semibold text-slate-800">
                  {selectedTopic.exactDate || "Sin fecha registrada"}
                </p>
              </div>

              <div className="rounded-lg border p-3">
                <span className="text-xs font-medium text-slate-400 flex items-center gap-1">
                  <BookOpen size={13} /> Procedencia
                </span>
                <p className="mt-2 text-sm font-semibold text-slate-800">
                  {selectedTopic.catalogTopic
                    ? "Catálogo anual oficial"
                    : "Tema personalizado"}
                </p>
              </div>
            </div>

            <div className="mt-6 flex justify-end gap-2 border-t pt-4">
              {onSelectSession && selectedTopic.status !== "NO_PLANIFICADO" && (
                <Button
                  size="sm"
                  onClick={() => {
                    const id = selectedTopic.id;
                    setSelectedTopic(null);
                    onSelectSession(id);
                  }}
                >
                  Ir a la planificación
                </Button>
              )}
              <Button
                variant="outline"
                size="sm"
                onClick={() => setSelectedTopic(null)}
              >
                Cerrar
              </Button>
            </div>
          </div>
        </Dialog>
      )}
    </div>
  );
}
