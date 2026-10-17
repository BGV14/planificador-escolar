import { act, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, expect, it, vi } from "vitest";
import App from "../App";
import { api } from "../lib/api";
vi.mock("../lib/api", async () => ({
  ...(await vi.importActual("../lib/api")),
  api: { get: vi.fn(), mutate: vi.fn(), download: vi.fn(), logout: vi.fn() },
}));
const contexts = [
  {
    id: "a",
    schoolId: "school",
    year: 2026,
    level: "PRIMARY",
    stageKind: "GRADE",
    stageValue: 3,
    grade: 3,
    cycle: null,
    revision: 0,
    legacyDefault: true,
    displayLabel: "2026 · Primaria · 3° grado",
  },
  {
    id: "b",
    schoolId: "school",
    year: 2027,
    level: "PRIMARY",
    stageKind: "GRADE",
    stageValue: 3,
    grade: 3,
    cycle: null,
    revision: 0,
    legacyDefault: false,
    displayLabel: "2027 · Primaria · 3° grado",
  },
];
let pending: ((value: unknown) => void) | undefined;
let delayA = false;
beforeEach(() => {
  vi.clearAllMocks();
  pending = undefined;
  delayA = false;
  vi.mocked(api.get).mockImplementation(async (path: string) => {
    if (path === "/me")
      return {
        id: "u",
        username: "Admin",
        schoolId: "school",
        role: "ADMIN",
        school: { id: "school", name: "School", grade: 6, school_year: 2026 },
      };
    if (path === "/contexts") return contexts;
    if (path === "/users") return [];
    const parsed = new URL(path, "http://local");
    const id = parsed.searchParams.get("contextId");
    if (!id) throw new Error("Missing context");
    if (parsed.pathname === "/areas")
      return [
        {
          id: "area-" + id,
          name: "Area " + id,
          ownerId: "u",
          sharedEdit: false,
        },
      ];
    if (parsed.pathname === "/plan")
      return { ...contexts.find((c) => c.id === id), warnings: [] };
    if (parsed.pathname === "/topics") {
      if (id === "a" && delayA)
        return new Promise((resolve) => {
          pending = resolve;
        });
      return [
        {
          id: "topic-" + id,
          name: "Topic " + id,
          areaId: "area-" + id,
          unit: 1,
          legacyId: null,
        },
      ];
    }
    return [];
  });
});
it("loads only explicit contexts and never displays a late previous-year response", async () => {
  render(<App />);
  await screen.findByText("Topic a");
  expect(
    vi
      .mocked(api.get)
      .mock.calls.filter(([p]) =>
        [
          "/topics",
          "/areas",
          "/sessions",
          "/plan",
          "/imports",
          "/blocks",
          "/schedules",
        ].includes(p.split("?")[0]),
      )
      .every(([p]) => p.includes("contextId=a")),
  ).toBe(true);
  delayA = true;
  await userEvent.click(
    screen.getByRole("button", { name: "Recargar planificación" }),
  );
  await waitFor(() => expect(pending).toBeDefined());
  await userEvent.selectOptions(screen.getByLabelText("Año del plan"), "2027");
  await screen.findByText("Topic b");
  await act(async () =>
    pending?.([
      {
        id: "late",
        name: "OLD YEAR MUST NOT DISPLAY",
        areaId: "area-a",
        unit: 1,
        legacyId: null,
      },
    ]),
  );
  expect(
    screen.queryByText("OLD YEAR MUST NOT DISPLAY"),
  ).not.toBeInTheDocument();
  expect(screen.queryByText("Topic a")).not.toBeInTheDocument();
});
it("closes editors, filters and previews when changing to another context", async () => {
  render(<App />);
  await screen.findByText("Topic a");
  await userEvent.selectOptions(
    screen.getByRole("combobox", { name: "Filtrar por área" }),
    "area-a",
  );
  await userEvent.click(screen.getByRole("button", { name: "Nueva sesión" }));
  expect(screen.getByRole("dialog")).toBeInTheDocument();
  await userEvent.selectOptions(screen.getByLabelText("Año del plan"), "2027");
  await screen.findByText("Topic b");
  expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  expect(
    screen.getByRole("combobox", { name: "Filtrar por área" }),
  ).toHaveValue("");
});
it("captures a mutation context and ignores its late completion after switching years", async () => {
  let complete: (value: unknown) => void = () => {};
  vi.mocked(api.mutate).mockImplementation(
    () =>
      new Promise((resolve) => {
        complete = resolve;
      }),
  );
  render(<App />);
  await screen.findByText("Topic a");
  await userEvent.click(screen.getByRole("button", { name: "Nueva sesión" }));
  await userEvent.selectOptions(
    screen.getByRole("combobox", { name: "Tema" }),
    "topic-a",
  );
  await userEvent.click(
    screen.getByRole("button", { name: "Guardar cambios" }),
  );
  await waitFor(() =>
    expect(api.mutate).toHaveBeenCalledWith(
      "/sessions?contextId=a",
      "POST",
      expect.objectContaining({ topicId: "topic-a" }),
    ),
  );
  await userEvent.selectOptions(screen.getByLabelText("Año del plan"), "2027");
  await screen.findByText("Topic b");
  const calls = vi.mocked(api.get).mock.calls.length;
  await act(async () => complete({ id: "saved-in-a" }));
  expect(api.get).toHaveBeenCalledTimes(calls);
  expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  expect(screen.getByText("Topic b")).toBeInTheDocument();
});
it("suppresses late download callbacks and errors after changing context", async () => {
  let reject: (reason: unknown) => void = () => {};
  vi.mocked(api.download).mockImplementation(
    () =>
      new Promise((_resolve, fail) => {
        reject = fail;
      }),
  );
  render(<App />);
  await screen.findByText("Topic a");
  await userEvent.click(screen.getByText("Exportar plan"));
  await userEvent.click(screen.getByRole("button", { name: "Descargar PDF" }));
  const [path, , isCurrent] = vi.mocked(api.download).mock.calls[0];
  expect(path).toContain("contextId=a");
  expect(isCurrent?.()).toBe(true);
  await userEvent.selectOptions(screen.getByLabelText("Año del plan"), "2027");
  await screen.findByText("Topic b");
  expect(isCurrent?.()).toBe(false);
  await act(async () => reject(new Error("old download")));
  expect(screen.queryByText(/No se pudo conectar/)).not.toBeInTheDocument();
});

it("can select its first confirmed plan without fabricating an empty-school default", async () => {
  const original = vi.mocked(api.get).getMockImplementation()!;
  vi.mocked(api.get).mockImplementation((path) =>
    path === "/contexts" ? Promise.resolve([]) : original(path),
  );
  vi.mocked(api.mutate).mockResolvedValue(contexts[1]);
  render(<App />);
  await screen.findByText(
    "Todavía no hay planes. La administración puede crear el primero.",
  );
  await userEvent.click(
    screen.getByText("Crear plan para otro año, nivel o etapa"),
  );
  await userEvent.clear(screen.getByLabelText("Año del nuevo plan"));
  await userEvent.type(screen.getByLabelText("Año del nuevo plan"), "2027");
  await userEvent.selectOptions(
    screen.getByLabelText("Grado del nuevo plan"),
    "3",
  );
  await userEvent.click(
    screen.getByRole("button", { name: "Crear plan vacío" }),
  );
  await screen.findByText("Topic b");
});
it("opening the already-selected tuple never clears its loaded data", async () => {
  render(<App />);
  await screen.findByText("Topic a");
  await userEvent.click(
    screen.getByText("Crear plan para otro año, nivel o etapa"),
  );
  await userEvent.clear(screen.getByLabelText("Año del nuevo plan"));
  await userEvent.type(screen.getByLabelText("Año del nuevo plan"), "2026");
  await userEvent.selectOptions(
    screen.getByLabelText("Grado del nuevo plan"),
    "3",
  );
  await userEvent.click(
    screen.getByRole("button", { name: "Crear plan vacío" }),
  );
  expect(screen.getByText("Topic a")).toBeInTheDocument();
  expect(api.mutate).not.toHaveBeenCalled();
});
