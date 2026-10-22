import { act, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, expect, it, vi } from "vitest";
import App from "../App";
import { api } from "../lib/api";

vi.mock("../lib/api", async () => ({
  ...(await vi.importActual("../lib/api")),
  api: { get: vi.fn(), mutate: vi.fn(), download: vi.fn(), logout: vi.fn() },
}));

const contexts = [2026, 2027].map((year, index) => ({
  id: index === 0 ? "a" : "b",
  schoolId: "school",
  year,
  level: "PRIMARY",
  stageKind: "GRADE",
  stageValue: 3,
  grade: 3,
  cycle: null,
  revision: 0,
  legacyDefault: index === 0,
  displayLabel: `${year} · Primaria · 3° grado confirmado`,
}));
const areas = [
  { id: "math", name: "Matemática", ownerId: "u", sharedEdit: false },
  { id: "language", name: "Comunicación", ownerId: "u", sharedEdit: false },
];
const topics = [
  { id: "one", areaId: "math", name: "Fracciones", unit: 1, legacyId: null },
  { id: "two", areaId: "math", name: "Geometría", unit: 2, legacyId: null },
  { id: "three", areaId: "language", name: "Lectura", unit: 1, legacyId: null },
  {
    id: "four",
    areaId: "language",
    name: "Escritura",
    unit: 2,
    legacyId: null,
  },
];
let pending: ((value: unknown) => void) | undefined;
let delayA = false;
let empty = false;

beforeEach(() => {
  vi.clearAllMocks();
  pending = undefined;
  delayA = false;
  empty = false;
  vi.mocked(api.logout).mockResolvedValue(undefined);
  vi.mocked(api.get).mockImplementation(async (path: string) => {
    if (path === "/me")
      return {
        id: "u",
        username: "Docente sintético",
        schoolId: "school",
        role: "ADMIN",
        school: {
          id: "school",
          name: "Escuela de prueba",
          grade: 3,
          school_year: 2026,
        },
      };
    if (path === "/contexts") return contexts;
    if (path === "/users") return [];
    const parsed = new URL(path, "http://local");
    const id = parsed.searchParams.get("contextId");
    if (!id) throw new Error("Missing explicit context");
    if (parsed.pathname === "/areas") return empty ? [] : areas;
    if (parsed.pathname === "/topics") {
      if (id === "a" && delayA)
        return new Promise((resolve) => {
          pending = resolve;
        });
      return empty
        ? []
        : topics.map((topic) => ({
            ...topic,
            name: id === "b" ? `${topic.name} nuevo` : topic.name,
          }));
    }
    if (parsed.pathname === "/sessions")
      return empty || id === "b"
        ? []
        : [
            {
              id: "s1",
              topicId: "one",
              status: "Trabajado",
              assignment: "",
              exactDate: null,
            },
            {
              id: "s2",
              topicId: "two",
              status: "Pendiente",
              assignment: "Diciembre",
              exactDate: "2026-12-01",
            },
            {
              id: "s3",
              topicId: "three",
              status: "Pendiente",
              assignment: "Marzo",
              exactDate: null,
            },
          ];
    if (parsed.pathname === "/plan")
      return { ...contexts.find((context) => context.id === id), warnings: [] };
    return [];
  });
});

async function openAnnual() {
  render(<App />);
  await screen.findByText("Fracciones");
  await userEvent.click(screen.getByRole("button", { name: "Avance anual" }));
  return screen.findByRole("region", { name: "Detalle del avance anual" });
}

function annualTotals() {
  return within(
    screen.getByRole("table", { name: "Resumen anual por área" }),
  ).getByRole("row", { name: /Total anual/ });
}

it("uses the complete annual catalog and preserves the monthly area and month on return", async () => {
  render(<App />);
  await screen.findByText("Fracciones");
  await userEvent.selectOptions(
    screen.getByLabelText("Filtrar por área"),
    "math",
  );
  await userEvent.selectOptions(screen.getByLabelText("Mes"), "3");
  await userEvent.click(screen.getByRole("button", { name: "Avance anual" }));
  expect(screen.getByRole("button", { name: "Avance anual" })).toHaveAttribute(
    "aria-current",
    "page",
  );
  expect(
    screen.queryByRole("combobox", { name: "Mes" }),
  ).not.toBeInTheDocument();
  expect(screen.getByText("Exportar plan")).not.toBeVisible();
  const summary = screen.getByRole("region", {
    name: "Avance curricular anual",
  });
  expect(
    within(summary).getByText(contexts[0].displayLabel),
  ).toBeInTheDocument();
  expect(
    within(annualTotals())
      .getAllByRole("cell")
      .map((cell) => cell.textContent),
  ).toEqual(["4", "1", "2", "1", "25 %"]);
  expect(screen.getByText("Escritura")).toBeInTheDocument();
  expect(
    screen.getByText("4 de 4 temas del catálogo anual"),
  ).toBeInTheDocument();
  await userEvent.click(
    screen.getByRole("button", { name: "Mi planificación" }),
  );
  expect(screen.getByLabelText("Filtrar por área")).toHaveValue("math");
  expect(screen.getByLabelText("Mes")).toHaveValue("3");
  expect(screen.queryByText("Escritura")).not.toBeInTheDocument();
});

it("keeps totals independent of detail filters and preserves filters on same-context reload", async () => {
  const details = await openAnnual();
  const totals = annualTotals().textContent;
  await userEvent.selectOptions(within(details).getByLabelText("Área"), "math");
  await userEvent.selectOptions(
    within(details).getByLabelText("Estado"),
    "planned",
  );
  expect(within(details).getByText("Geometría")).toBeInTheDocument();
  expect(within(details).queryByText("Fracciones")).not.toBeInTheDocument();
  expect(within(details).getByRole("status")).toHaveTextContent("1 de 4");
  expect(annualTotals()).toHaveTextContent(totals!);
  delayA = true;
  await userEvent.click(
    screen.getByRole("button", { name: "Recargar planificación" }),
  );
  await waitFor(() => expect(pending).toBeDefined());
  await act(async () => pending?.(topics));
  await waitFor(() =>
    expect(
      screen.getByRole("button", { name: "Recargar planificación" }),
    ).toBeEnabled(),
  );
  expect(within(details).getByLabelText("Área")).toHaveValue("math");
  expect(within(details).getByLabelText("Estado")).toHaveValue("planned");
  expect(annualTotals()).toHaveTextContent(totals!);
});

it("resets detail filters on context switch with reused IDs and rejects a late old catalog", async () => {
  let details = await openAnnual();
  await userEvent.selectOptions(within(details).getByLabelText("Área"), "math");
  await userEvent.selectOptions(
    within(details).getByLabelText("Estado"),
    "completed",
  );
  delayA = true;
  await userEvent.click(
    screen.getByRole("button", { name: "Recargar planificación" }),
  );
  await waitFor(() => expect(pending).toBeDefined());
  await userEvent.selectOptions(screen.getByLabelText("Año del plan"), "2027");
  await screen.findByText("Escritura nuevo");
  details = screen.getByRole("region", { name: "Detalle del avance anual" });
  expect(within(details).getByLabelText("Área")).toHaveValue("");
  expect(within(details).getByLabelText("Estado")).toHaveValue("");
  expect(
    within(details).getByText(contexts[1].displayLabel),
  ).toBeInTheDocument();
  expect(
    within(annualTotals())
      .getAllByRole("cell")
      .map((cell) => cell.textContent),
  ).toEqual(["4", "0", "0", "4", "0 %"]);
  await act(async () =>
    pending?.(topics.map((topic) => ({ ...topic, name: "CATÁLOGO OBSOLETO" }))),
  );
  expect(screen.queryByText("CATÁLOGO OBSOLETO")).not.toBeInTheDocument();
  expect(screen.queryByText("Fracciones")).not.toBeInTheDocument();
  expect(
    within(details).getByText("4 de 4 temas del catálogo anual"),
  ).toBeInTheDocument();
});

it("does not restore annual data after logout when a reload finishes late", async () => {
  await openAnnual();
  delayA = true;
  await userEvent.click(
    screen.getByRole("button", { name: "Recargar planificación" }),
  );
  await waitFor(() => expect(pending).toBeDefined());
  await userEvent.click(screen.getByRole("button", { name: "Cerrar sesión" }));
  await waitFor(() =>
    expect(
      screen.queryByRole("navigation", { name: "Principal" }),
    ).not.toBeInTheDocument(),
  );
  await act(async () => pending?.(topics));
  expect(
    screen.queryByRole("region", { name: "Avance curricular anual" }),
  ).not.toBeInTheDocument();
  expect(screen.queryByText("Fracciones")).not.toBeInTheDocument();
  expect(api.logout).toHaveBeenCalledOnce();
});

it("shows an empty annual catalog without inventing a percentage", async () => {
  empty = true;
  render(<App />);
  await screen.findByText("Este plan empieza vacío");
  await userEvent.click(screen.getByRole("button", { name: "Avance anual" }));
  expect(
    screen.getByText("No hay temas en el catálogo anual."),
  ).toBeInTheDocument();
  expect(
    screen.getByText("No hay áreas en este contexto académico."),
  ).toBeInTheDocument();
  expect(
    within(annualTotals()).getByText("Sin porcentaje"),
  ).toBeInTheDocument();
  expect(screen.queryByText(/^0 %$/)).not.toBeInTheDocument();
});
