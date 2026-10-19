import { fireEvent, render, screen, within } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { AnnualCoverageDetails } from "../components/AnnualCoverageDetails";
import { buildAnnualCoverage, type AnnualCoverage } from "../lib/coverage";

function catalog(): AnnualCoverage {
  return buildAnnualCoverage({
    areas: ["Matemática", "Ciencia"].map((name, index) => ({
      id: String(index),
      name,
      ownerId: "teacher",
      sharedEdit: false,
    })),
    topics: ["Números", "Álgebra", "Geometría", "Plantas"].map(
      (name, index) => ({
        id: String(index),
        name,
        areaId: index === 3 ? "1" : "0",
        unit: index + 1,
        legacyId: null,
      }),
    ),
    sessions: [0, 1, 3].map((index) => ({
      id: String(index),
      topicId: String(index),
      assignment: "Diciembre",
      exactDate: null,
      status: index === 1 ? null : "Trabajado",
      notes: null,
      version: 0,
    })),
  });
}

function view(
  coverage = catalog(),
  contextKey = "2026",
  contextLabel = "2026 · Primaria",
) {
  return (
    <AnnualCoverageDetails
      coverage={coverage}
      contextKey={contextKey}
      contextLabel={contextLabel}
    />
  );
}
const choose = (label: string, value: string) =>
  fireEvent.change(screen.getByRole("combobox", { name: label }), {
    target: { value },
  });
const visibleTopics = () =>
  within(
    screen.getByRole("table", { name: "Detalle de temas del catálogo anual" }),
  )
    .getAllByRole("row")
    .slice(1)
    .map((row) => within(row).getAllByRole("cell")[2].textContent);

describe("annual coverage details", () => {
  it("presents an accessible region, context, labelled filters and semantic table", () => {
    render(view());
    expect(
      screen.getByRole("region", { name: "Detalle del avance anual" }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("heading", { name: "Detalle del avance anual" }),
    ).toBeInTheDocument();
    expect(screen.getByText("2026 · Primaria")).toBeInTheDocument();
    expect(screen.getByRole("combobox", { name: "Área" })).toHaveValue("");
    expect(screen.getByRole("combobox", { name: "Estado" })).toHaveValue("");
    const headers = screen.getAllByRole("columnheader");
    expect(headers.map((header) => header.textContent)).toEqual([
      "Área",
      "Unidad",
      "Tema",
      "Estado",
    ]);
    headers.forEach((header) => expect(header).toHaveAttribute("scope", "col"));
    expect(visibleTopics()).toEqual([
      "Números",
      "Álgebra",
      "Geometría",
      "Plantas",
    ]);
    expect(
      screen.getByText("4 de 4 temas del catálogo anual"),
    ).toBeInTheDocument();
  });

  it.each([
    ["completed", ["Números", "Plantas"]],
    ["planned", ["Álgebra"]],
    ["unplanned", ["Geometría"]],
  ])(
    "uses the supplied %s classification without changing the annual denominator",
    (status, expected) => {
      render(view());
      choose("Estado", status);
      expect(visibleTopics()).toEqual(expected);
      expect(
        screen.getByText(`${expected.length} de 4 temas del catálogo anual`),
      ).toBeInTheDocument();
      expect(screen.queryByText(/%/)).not.toBeInTheDocument();
    },
  );

  it("combines area and status filters as an intersection and can clear both", () => {
    render(view());
    choose("Área", "0");
    expect(visibleTopics()).toEqual(["Números", "Álgebra", "Geometría"]);
    choose("Estado", "completed");
    expect(visibleTopics()).toEqual(["Números"]);
    expect(
      screen.getByText("1 de 4 temas del catálogo anual"),
    ).toBeInTheDocument();
    choose("Área", "");
    expect(visibleTopics()).toEqual(["Números", "Plantas"]);
    choose("Estado", "");
    expect(visibleTopics()).toHaveLength(4);
  });

  it("distinguishes no matches from an empty catalog", () => {
    render(view());
    choose("Área", "1");
    choose("Estado", "unplanned");
    expect(
      screen.getByText("No hay temas que coincidan con los filtros."),
    ).toBeInTheDocument();
    expect(
      screen.getByText("0 de 4 temas del catálogo anual"),
    ).toBeInTheDocument();
    expect(
      screen.queryByText("No hay temas en el catálogo anual."),
    ).not.toBeInTheDocument();
  });

  it("explains an empty catalog without inventing rows or percentages", () => {
    render(view(buildAnnualCoverage({ areas: [], topics: [], sessions: [] })));
    expect(
      screen.getByText("No hay temas en el catálogo anual."),
    ).toBeInTheDocument();
    expect(
      screen.getByText("0 de 0 temas del catálogo anual"),
    ).toBeInTheDocument();
    expect(screen.queryByRole("table")).not.toBeInTheDocument();
    expect(
      screen.queryByText("No hay temas que coincidan con los filtros."),
    ).not.toBeInTheDocument();
  });

  it("resets both filters immediately on a context key change even with reused IDs and label", () => {
    const { rerender } = render(view());
    choose("Área", "1");
    choose("Estado", "planned");
    const next = catalog();
    next.areas[0].topics[0].topic.name = "Nuevo tema";
    rerender(view(next, "2027"));
    expect(screen.getByRole("combobox", { name: "Área" })).toHaveValue("");
    expect(screen.getByRole("combobox", { name: "Estado" })).toHaveValue("");
    expect(visibleTopics()).toEqual([
      "Nuevo tema",
      "Álgebra",
      "Geometría",
      "Plantas",
    ]);
    expect(screen.queryByText("Números")).not.toBeInTheDocument();
  });

  it("preserves valid filters on same-context data and label updates", () => {
    const { rerender } = render(view());
    choose("Área", "0");
    choose("Estado", "planned");
    const next = catalog();
    next.areas[0].topics[1].topic.name = "Álgebra actualizada";
    rerender(view(next, "2026", "Etiqueta actualizada"));
    expect(screen.getByRole("combobox", { name: "Área" })).toHaveValue("0");
    expect(screen.getByRole("combobox", { name: "Estado" })).toHaveValue(
      "planned",
    );
    expect(visibleTopics()).toEqual(["Álgebra actualizada"]);
    expect(screen.getByText("Etiqueta actualizada")).toBeInTheDocument();
  });

  it("normalizes only a disappeared area and does not resurrect it on a later update", () => {
    const { rerender } = render(view());
    choose("Área", "1");
    choose("Estado", "planned");
    const next = catalog();
    next.areas = next.areas.slice(0, 1);
    next.totals = { ...next.areas[0] };
    rerender(view(next));
    expect(screen.getByRole("combobox", { name: "Área" })).toHaveValue("");
    expect(screen.getByRole("combobox", { name: "Estado" })).toHaveValue(
      "planned",
    );
    expect(visibleTopics()).toEqual(["Álgebra"]);
    expect(
      screen.getByText("1 de 3 temas del catálogo anual"),
    ).toBeInTheDocument();
    rerender(view());
    expect(screen.getByRole("combobox", { name: "Área" })).toHaveValue("");
  });

  it("never mutates supplied coverage while filtering", () => {
    const coverage = catalog();
    const snapshot = JSON.stringify(coverage);
    const freeze = (value: object) => {
      Object.values(value).forEach((nested) => {
        if (nested !== null && typeof nested === "object") freeze(nested);
      });
      Object.freeze(value);
    };
    freeze(coverage);
    render(view(coverage));
    choose("Área", "0");
    choose("Estado", "unplanned");
    expect(visibleTopics()).toEqual(["Geometría"]);
    expect(JSON.stringify(coverage)).toBe(snapshot);
  });
});
