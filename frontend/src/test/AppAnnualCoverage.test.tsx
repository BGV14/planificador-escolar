import { act, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, expect, it, vi } from "vitest";
import App from "../App";
import { api } from "../lib/api";
import type { AcademicContext, Topic } from "../lib/types";

vi.mock("../lib/api", async () => ({
  ...(await vi.importActual("../lib/api")),
  api: { get: vi.fn(), mutate: vi.fn(), download: vi.fn(), logout: vi.fn() },
}));

const contexts: AcademicContext[] = [2026, 2027].map((year, index) => ({
  id: index === 0 ? "a" : "b",
  schoolId: "school",
  year,
  level: "PRIMARY",
  section: "UNICA",
  stageKind: "GRADE",
  stageValue: 3,
  grade: 3,
  cycle: null,
  revision: 0,
  legacyDefault: index === 0,
  displayLabel: `${year} · Primaria · 3° grado`,
}));
let empty = false;
let delayA = false;
let resolveTopics: ((topics: Topic[]) => void) | undefined;
let revision = 0;

function topicsFor(id: string): Topic[] {
  return [
    {
      id: "t1",
      areaId: "area1",
      name: `Tema enero ${id}`,
      unit: 1,
      legacyId: null,
    },
    {
      id: "t2",
      areaId: "area2",
      name: `Tema diciembre ${id}`,
      unit: 2,
      legacyId: null,
    },
    {
      id: "t3",
      areaId: "area2",
      name: `Tema sin sesión ${id}`,
      unit: 3,
      legacyId: null,
    },
  ];
}

beforeEach(() => {
  vi.clearAllMocks();
  empty = false;
  delayA = false;
  resolveTopics = undefined;
  revision = 0;
  vi.mocked(api.logout).mockResolvedValue(undefined);
  vi.mocked(api.get).mockImplementation(async (path: string) => {
    if (path === "/me")
      return {
        id: "u",
        username: "Admin",
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
    const id = parsed.searchParams.get("contextId")!;
    if (parsed.pathname === "/plan")
      return {
        ...contexts.find((context) => context.id === id),
        displayLabel: `Plan confirmado ${id}`,
        revision,
        warnings: [],
      };
    if (parsed.pathname === "/areas")
      return empty
        ? []
        : [
            {
              id: "area1",
              name: "Matemática",
              ownerId: "u",
              sharedEdit: false,
            },
            {
              id: "area2",
              name: "Comunicación",
              ownerId: "u",
              sharedEdit: false,
            },
          ];
    if (parsed.pathname === "/topics") {
      if (id === "a" && delayA)
        return new Promise<Topic[]>((resolve) => {
          resolveTopics = resolve;
        });
      return empty ? [] : topicsFor(id);
    }
    if (parsed.pathname === "/sessions")
      return empty
        ? []
        : [
            {
              id: "s1",
              topicId: "t1",
              assignment: "Enero",
              exactDate: "2026-01-05",
              status: "Trabajado",
              notes: null,
              version: 0,
            },
            {
              id: "s2",
              topicId: "t2",
              assignment: "Diciembre",
              exactDate: "2026-12-07",
              status: null,
              notes: null,
              version: 0,
            },
          ];
    return [];
  });
});

async function openAnnual() {
  render(<App />);
  await screen.findByText("Tema sin sesión a");
  await userEvent.click(screen.getByRole("button", { name: "Avance anual" }));
  return screen.findByRole("region", { name: "Avance curricular anual" });
}
function expectAnnualTotal() {
  const row = screen.getByRole("row", { name: /Total anual/ });
  expect(
    within(row)
      .getAllByRole("cell")
      .map((cell) => cell.textContent),
  ).toEqual(["3", "1", "1", "1", "33.3 %"]);
}

it("integra el catálogo completo y usa la etiqueta de la respuesta confirmada", async () => {
  render(<App />);
  await screen.findByText("Tema sin sesión a");
  await userEvent.selectOptions(
    screen.getByRole("combobox", { name: "Mes" }),
    "1",
  );
  await userEvent.click(screen.getByRole("button", { name: "Avance anual" }));
  const summary = await screen.findByRole("region", {
    name: "Avance curricular anual",
  });
  expect(within(summary).getByText("Plan confirmado a")).toBeInTheDocument();
  expect(screen.getByText("Tema diciembre a")).toBeInTheDocument();
  expect(screen.getByText("Tema sin sesión a")).toBeInTheDocument();
  expectAnnualTotal();
  expect(
    screen.queryByRole("combobox", { name: "Mes" }),
  ).not.toBeInTheDocument();
  expect(
    screen.queryByRole("combobox", { name: "Filtrar por área" }),
  ).not.toBeInTheDocument();
  expect(screen.queryByText("Exportar plan")).not.toBeInTheDocument();
});

it("ignora el filtro mensual de área y lo conserva al volver a planificación", async () => {
  render(<App />);
  await screen.findByText("Tema sin sesión a");
  await userEvent.selectOptions(
    screen.getByRole("combobox", { name: "Filtrar por área" }),
    "area1",
  );
  await userEvent.selectOptions(
    screen.getByRole("combobox", { name: "Mes" }),
    "1",
  );
  await userEvent.click(screen.getByRole("button", { name: "Avance anual" }));
  expectAnnualTotal();
  await userEvent.click(
    screen.getByRole("button", { name: "Mi planificación" }),
  );
  expect(
    screen.getByRole("combobox", { name: "Filtrar por área" }),
  ).toHaveValue("area1");
  expect(screen.getByRole("combobox", { name: "Mes" })).toHaveValue("1");
  expect(screen.getByText("Exportar plan")).toBeInTheDocument();
  expect(screen.queryByText("Tema diciembre a")).not.toBeInTheDocument();
});

it("los filtros de detalle no alteran los totales y sobreviven a una revisión del mismo plan", async () => {
  await openAnnual();
  await userEvent.selectOptions(
    screen.getByRole("combobox", { name: "Área" }),
    "area2",
  );
  await userEvent.selectOptions(
    screen.getByRole("combobox", { name: "Estado" }),
    "planned",
  );
  expect(screen.getByRole("status")).toHaveTextContent("1 de 3");
  expectAnnualTotal();
  revision = 1;
  await userEvent.click(
    screen.getByRole("button", { name: "Recargar planificación" }),
  );
  await screen.findByText(/Revisión 1/);
  expect(screen.getByRole("combobox", { name: "Estado" })).toHaveValue(
    "planned",
  );
  expect(screen.getByRole("combobox", { name: "Área" })).toHaveValue("area2");
  expectAnnualTotal();
});

it("reinicia filtros al cambiar de contexto aunque reutilice los identificadores", async () => {
  await openAnnual();
  await userEvent.selectOptions(
    screen.getByRole("combobox", { name: "Área" }),
    "area1",
  );
  await userEvent.selectOptions(
    screen.getByRole("combobox", { name: "Estado" }),
    "completed",
  );
  await userEvent.selectOptions(screen.getByLabelText("Año del plan"), "2027");
  await screen.findByText("Tema diciembre b");
  expect(screen.getByRole("combobox", { name: "Área" })).toHaveValue("");
  expect(screen.getByRole("combobox", { name: "Estado" })).toHaveValue("");
  expect(screen.queryByText("Tema enero a")).not.toBeInTheDocument();
  expectAnnualTotal();
});

it("una respuesta tardía del contexto anterior no sustituye el panel anual actual", async () => {
  await openAnnual();
  delayA = true;
  await userEvent.click(
    screen.getByRole("button", { name: "Recargar planificación" }),
  );
  await waitFor(() => expect(resolveTopics).toBeDefined());
  await userEvent.selectOptions(screen.getByLabelText("Año del plan"), "2027");
  await screen.findByText("Tema diciembre b");
  await act(async () => resolveTopics?.(topicsFor("obsoleto")));
  expect(screen.queryByText("Tema enero obsoleto")).not.toBeInTheDocument();
  expect(screen.getByText("Tema diciembre b")).toBeInTheDocument();
  expectAnnualTotal();
});

it("cerrar sesión impide que una recarga tardía restaure el panel", async () => {
  await openAnnual();
  delayA = true;
  await userEvent.click(
    screen.getByRole("button", { name: "Recargar planificación" }),
  );
  await waitFor(() => expect(resolveTopics).toBeDefined());
  await userEvent.click(screen.getByRole("button", { name: "Cerrar sesión" }));
  await waitFor(() =>
    expect(
      screen.queryByRole("region", { name: "Avance curricular anual" }),
    ).not.toBeInTheDocument(),
  );
  await act(async () => resolveTopics?.(topicsFor("obsoleto")));
  expect(api.logout).toHaveBeenCalledOnce();
  expect(
    screen.queryByRole("button", { name: "Avance anual" }),
  ).not.toBeInTheDocument();
  expect(screen.queryByText("Tema enero obsoleto")).not.toBeInTheDocument();
});

it("presenta un catálogo vacío sin porcentajes inventados", async () => {
  empty = true;
  render(<App />);
  await screen.findByText("Este plan empieza vacío");
  await userEvent.click(screen.getByRole("button", { name: "Avance anual" }));
  expect(
    screen.getByText("No hay temas en el catálogo anual."),
  ).toBeInTheDocument();
  const row = screen.getByRole("row", { name: /Total anual/ });
  expect(within(row).getByText("Sin porcentaje")).toBeInTheDocument();
  expect(screen.getByRole("status")).toHaveTextContent("0 de 0");
});
