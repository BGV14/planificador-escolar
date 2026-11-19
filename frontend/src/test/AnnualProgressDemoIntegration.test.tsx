import { render, screen } from "@testing-library/react";
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
  section: "UNICA",
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

it("keeps real annual coverage and removes synthetic navigation entirely", async () => {
  render(<App />);
  await screen.findByText("Fracciones");
  await userEvent.click(screen.getByRole("button", { name: "Avance anual" }));
  expect(
    await screen.findByRole("region", { name: "Avance curricular anual" }),
  ).toBeInTheDocument();
  expect(
    screen.queryByRole("button", { name: "Demostración con datos simulados" }),
  ).not.toBeInTheDocument();
  expect(
    screen.queryByRole("note", { name: "Prototipo académico" }),
  ).not.toBeInTheDocument();
  expect(
    vi
      .mocked(api.get)
      .mock.calls.some(([path]) => path.startsWith("/progress/annual")),
  ).toBe(false);
});

it("returns to the normal login without loading any synthetic endpoint", async () => {
  render(<App />);
  await screen.findByText("Fracciones");
  await userEvent.click(screen.getByRole("button", { name: "Cerrar sesión" }));
  await screen.findByRole("button", { name: "Ingresar a mi escuela" });
  expect(
    screen.queryByRole("note", { name: "Prototipo académico" }),
  ).not.toBeInTheDocument();
  expect(
    vi
      .mocked(api.get)
      .mock.calls.some(([path]) => path.startsWith("/progress/annual")),
  ).toBe(false);
});
