import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { AnnualProgress } from "../components/AnnualProgress";
import { api } from "../lib/api";
import type { AnnualProgressData } from "../lib/progressTypes";

vi.mock("../lib/api", async () => ({
  ...(await vi.importActual("../lib/api")),
  api: { get: vi.fn(), mutate: vi.fn() },
}));

const mockContextAData: AnnualProgressData = {
  contextId: "ctx-a",
  displayLabel: "2026 · Primaria · 6° grado",
  year: 2026,
  level: "PRIMARY",
  stageValue: 6,
  summary: {
    totalTopics: 5,
    completedTopics: 2,
    plannedPendingTopics: 2,
    unplannedTopics: 1,
    completionPercentage: 40.0,
  },
  areaSummaries: [
    {
      areaId: "area-math",
      areaName: "Matemática",
      total: 3,
      completed: 1,
      plannedPending: 1,
      unplanned: 1,
    },
    {
      areaId: "area-com",
      areaName: "Comunicación",
      total: 2,
      completed: 1,
      plannedPending: 1,
      unplanned: 0,
    },
  ],
  topics: [
    {
      id: "top-1",
      name: "Conjuntos y Subconjuntos",
      areaId: "area-math",
      areaName: "Matemática",
      unit: 1,
      status: "COMPLETADO",
      assignment: "Marzo",
      exactDate: "2026-03-12",
      catalogTopic: true,
    },
    {
      id: "top-2",
      name: "Operaciones Aritméticas",
      areaId: "area-math",
      areaName: "Matemática",
      unit: 2,
      status: "PLANIFICADO_PENDIENTE",
      assignment: "Abril",
      exactDate: null,
      catalogTopic: true,
    },
    {
      id: "top-3",
      name: "Geometría del Espacio",
      areaId: "area-math",
      areaName: "Matemática",
      unit: 3,
      status: "NO_PLANIFICADO",
      assignment: null,
      exactDate: null,
      catalogTopic: true,
    },
    {
      id: "top-4",
      name: "Comprensión de Textos",
      areaId: "area-com",
      areaName: "Comunicación",
      unit: 1,
      status: "COMPLETADO",
      assignment: "Marzo",
      exactDate: "2026-03-20",
      catalogTopic: true,
    },
    {
      id: "top-5",
      name: "Gramática y Ortografía",
      areaId: "area-com",
      areaName: "Comunicación",
      unit: 2,
      status: "PLANIFICADO_PENDIENTE",
      assignment: "Mayo",
      exactDate: null,
      catalogTopic: true,
    },
  ],
};

const mockContextBData: AnnualProgressData = {
  contextId: "ctx-b",
  displayLabel: "2027 · Secundaria · 1° grado",
  year: 2027,
  level: "SECONDARY",
  stageValue: 1,
  summary: {
    totalTopics: 1,
    completedTopics: 0,
    plannedPendingTopics: 0,
    unplannedTopics: 1,
    completionPercentage: 0.0,
  },
  areaSummaries: [
    {
      areaId: "area-sci",
      areaName: "Ciencias",
      total: 1,
      completed: 0,
      plannedPending: 0,
      unplanned: 1,
    },
  ],
  topics: [
    {
      id: "top-b-1",
      name: "Método Científico",
      areaId: "area-sci",
      areaName: "Ciencias",
      unit: 1,
      status: "NO_PLANIFICADO",
      assignment: null,
      exactDate: null,
      catalogTopic: true,
    },
  ],
};

describe("AnnualProgress Component", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(api.get).mockImplementation(async (path: string) => {
      if (path.includes("contextId=ctx-a")) return mockContextAData;
      if (path.includes("contextId=ctx-b")) return mockContextBData;
      throw new Error("Context not found: " + path);
    });
  });

  it("renders catalog topics and displays the mutually exclusive states", async () => {
    render(<AnnualProgress contextId="ctx-a" />);

    await screen.findByText("Avance anual de temas");
    expect(screen.getByText("Conjuntos y Subconjuntos")).toBeInTheDocument();
    expect(screen.getByText("Operaciones Aritméticas")).toBeInTheDocument();
    expect(screen.getByText("Geometría del Espacio")).toBeInTheDocument();

    // Verify badges for mutually exclusive states
    expect(screen.getAllByText("Completado (Trabajado)").length).toBeGreaterThan(0);
    expect(screen.getAllByText("Planificado pendiente").length).toBeGreaterThan(0);
    expect(screen.getAllByText("No planificado").length).toBeGreaterThan(0);
  });

  it("filters by area and status without modifying the total annual denominator", async () => {
    render(<AnnualProgress contextId="ctx-a" />);
    await screen.findByText("Conjuntos y Subconjuntos");

    // CRITERION 4: Global denominator is 5
    expect(screen.getByTestId("kpi-total-topics")).toHaveTextContent("5");

    // Filter by area: Comunicación (has 2 topics)
    const areaSelect = screen.getByRole("combobox", { name: /filtrar por área/i });
    await userEvent.selectOptions(areaSelect, "area-com");

    expect(screen.getByText("Comprensión de Textos")).toBeInTheDocument();
    expect(screen.getByText("Gramática y Ortografía")).toBeInTheDocument();
    expect(screen.queryByText("Conjuntos y Subconjuntos")).not.toBeInTheDocument();

    // The total annual denominator MUST stay constant (5)
    expect(screen.getByTestId("kpi-total-topics")).toHaveTextContent("5");
    expect(screen.getByText(/mostrando 2 de 5 temas/i)).toBeInTheDocument();

    // Filter by status: PLANIFICADO_PENDIENTE (only Gramática y Ortografía in Comunicación)
    const statusSelect = screen.getByRole("combobox", { name: /filtrar por estado/i });
    await userEvent.selectOptions(statusSelect, "PLANIFICADO_PENDIENTE");

    expect(screen.getByText("Gramática y Ortografía")).toBeInTheDocument();
    expect(screen.queryByText("Comprensión de Textos")).not.toBeInTheDocument();
    expect(screen.getByTestId("kpi-total-topics")).toHaveTextContent("5");
    expect(screen.getByText(/mostrando 1 de 5 temas/i)).toBeInTheDocument();
  });

  it("displays empty state when filters match no topics and allows resetting filters", async () => {
    render(<AnnualProgress contextId="ctx-a" />);
    await screen.findByText("Conjuntos y Subconjuntos");

    // Filter by Comunicación + NO_PLANIFICADO (which has 0 topics)
    const areaSelect = screen.getByRole("combobox", { name: /filtrar por área/i });
    await userEvent.selectOptions(areaSelect, "area-com");

    const statusSelect = screen.getByRole("combobox", { name: /filtrar por estado/i });
    await userEvent.selectOptions(statusSelect, "NO_PLANIFICADO");

    // Empty state should be visible
    expect(
      screen.getByText(/no hay temas que coincidan con los filtros seleccionados/i),
    ).toBeInTheDocument();
    // Annual total denominator remains constant
    expect(screen.getByTestId("kpi-total-topics")).toHaveTextContent("5");

    // Click "Restablecer filtros"
    const resetButton = screen.getByRole("button", { name: /restablecer filtros/i });
    await userEvent.click(resetButton);

    // Filters are cleared and all 5 topics reappear
    expect(screen.getByText("Conjuntos y Subconjuntos")).toBeInTheDocument();
    expect(areaSelect).toHaveValue("");
    expect(statusSelect).toHaveValue("");
  });

  it("opens topic detail view and resets filters and detail view when switching contextId", async () => {
    const { rerender } = render(<AnnualProgress contextId="ctx-a" />);
    await screen.findByText("Conjuntos y Subconjuntos");

    // Apply a filter
    const areaSelect = screen.getByRole("combobox", { name: /filtrar por área/i });
    await userEvent.selectOptions(areaSelect, "area-math");
    expect(areaSelect).toHaveValue("area-math");

    // Open detail view for topic 1
    const detailButton = screen.getByRole("button", { name: /ver detalle de conjuntos y subconjuntos/i });
    await userEvent.click(detailButton);

    // Detail view is open
    expect(screen.getByRole("dialog", { name: /detalle del tema/i })).toBeInTheDocument();
    expect(within(screen.getByRole("dialog")).getByText("Marzo")).toBeInTheDocument();

    // CRITERION 5: Switching contextId must reset filters and detail view, and never show old data
    rerender(<AnnualProgress contextId="ctx-b" />);

    // Detail dialog must be closed
    await waitFor(() => {
      expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    });

    // Previous context data must NOT be shown
    expect(screen.queryByText("Conjuntos y Subconjuntos")).not.toBeInTheDocument();

    // New context data is shown
    await screen.findByText("Método Científico");
    expect(screen.getByTestId("kpi-total-topics")).toHaveTextContent("1");

    // Filters must be reset
    const newAreaSelect = screen.getByRole("combobox", { name: /filtrar por área/i });
    expect(newAreaSelect).toHaveValue("");
  });
});
