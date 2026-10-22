import { render, screen, within } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { AnnualCoverageSummary } from "../components/AnnualCoverageSummary";
import { buildAnnualCoverage } from "../lib/coverage";
import type { Area, Session, Topic } from "../lib/types";

const area = (id: string, name: string): Area => ({
  id,
  name,
  ownerId: "teacher",
  sharedEdit: false,
});
const topic = (id: string, areaId: string): Topic => ({
  id,
  areaId,
  name: id,
  unit: 1,
  legacyId: null,
});
const session = (topicId: string, fields: Partial<Session>): Session => ({
  id: `session-${topicId}`,
  topicId,
  assignment: null,
  exactDate: null,
  status: null,
  notes: null,
  version: 0,
  ...fields,
});
const mixedCoverage = () =>
  buildAnnualCoverage({
    areas: [area("math", "Matemática"), area("science", "Ciencia")],
    topics: [
      topic("done", "math"),
      topic("planned", "math"),
      topic("missing", "math"),
      topic("science-done", "science"),
    ],
    sessions: [
      session("done", { status: "Trabajado" }),
      session("planned", { assignment: "Diciembre" }),
      session("science-done", { status: "Trabajado" }),
    ],
  });

function expectRow(name: string, values: string[]) {
  const heading = screen.getByRole("rowheader", { name });
  const row = heading.closest("tr")!;
  expect(
    within(row)
      .getAllByRole("cell")
      .map((cell) => cell.textContent),
  ).toEqual(values);
}

describe("annual coverage summary", () => {
  it("renders the context, accessible table headers and annual totals by area", () => {
    render(
      <AnnualCoverageSummary
        coverage={mixedCoverage()}
        contextLabel="2026 · Primaria · 6.º grado"
      />,
    );
    expect(
      screen.getByRole("heading", { name: "Avance curricular anual" }),
    ).toBeInTheDocument();
    expect(screen.getByText("2026 · Primaria · 6.º grado")).toBeInTheDocument();
    const table = screen.getByRole("table", { name: "Resumen anual por área" });
    expect(
      within(table)
        .getAllByRole("columnheader")
        .map((cell) => cell.textContent),
    ).toEqual([
      "Área",
      "Total de temas",
      "Trabajados",
      "Planificados",
      "Sin planificar",
      "Avance",
    ]);
    for (const header of within(table).getAllByRole("columnheader")) {
      expect(header).toHaveAttribute("scope", "col");
    }
    for (const header of within(table).getAllByRole("rowheader")) {
      expect(header).toHaveAttribute("scope", "row");
    }
    expectRow("Matemática", ["3", "1", "1", "1", "33.3 %"]);
    expectRow("Ciencia", ["1", "1", "0", "0", "100 %"]);
    expectRow("Total anual", ["4", "2", "1", "1", "50 %"]);
  });

  it("distinguishes an area without topics from a zero-percent area", () => {
    const coverage = buildAnnualCoverage({
      areas: [area("empty", "Sin catálogo"), area("new", "Comunicación")],
      topics: [topic("new-topic", "new")],
      sessions: [],
    });
    render(<AnnualCoverageSummary coverage={coverage} contextLabel="2026" />);
    expectRow("Sin catálogo", ["0Sin temas", "0", "0", "0", "Sin porcentaje"]);
    expectRow("Comunicación", ["1", "0", "0", "1", "0 %"]);
  });

  it("explains when no areas exist without inventing a percentage", () => {
    const coverage = buildAnnualCoverage({
      areas: [],
      topics: [],
      sessions: [],
    });
    render(<AnnualCoverageSummary coverage={coverage} contextLabel="2027" />);
    expect(
      screen.getByText("No hay áreas en este contexto académico."),
    ).toBeInTheDocument();
    expectRow("Total anual", ["0Sin temas", "0", "0", "0", "Sin porcentaje"]);
    expect(screen.queryByText("0 %")).not.toBeInTheDocument();
  });

  it("updates counts and context immediately when props change", () => {
    const { rerender } = render(
      <AnnualCoverageSummary
        coverage={mixedCoverage()}
        contextLabel="Contexto anterior"
      />,
    );
    const next = buildAnnualCoverage({
      areas: [area("art", "Arte")],
      topics: [topic("art-topic", "art")],
      sessions: [],
    });
    rerender(
      <AnnualCoverageSummary coverage={next} contextLabel="Contexto nuevo" />,
    );
    expect(screen.queryByText("Contexto anterior")).not.toBeInTheDocument();
    expect(screen.getByText("Contexto nuevo")).toBeInTheDocument();
    expect(
      screen.queryByRole("rowheader", { name: "Matemática" }),
    ).not.toBeInTheDocument();
    expectRow("Arte", ["1", "0", "0", "1", "0 %"]);
    expectRow("Total anual", ["1", "0", "0", "1", "0 %"]);
  });
});
