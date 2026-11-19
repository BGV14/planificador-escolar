import { act, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, expect, it, vi } from "vitest";
import App from "../App";
import { api, ApiError } from "../lib/api";
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
    section: "UNICA",
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
    section: "UNICA",
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
it("explains missing teacher assignments without loading or administrator controls", async () => {
  const implementation = vi.mocked(api.get).getMockImplementation()!;
  vi.mocked(api.get).mockImplementation(async (path) => {
    if (path === "/contexts") return [];
    const result = await implementation(path);
    if (path === "/me") return { ...(result as object), role: "TEACHER" };
    return result;
  });
  render(<App />);
  await screen.findByText(/No tienes aulas asignadas/);
  expect(
    screen.queryByRole("button", { name: "Demostración con datos simulados" }),
  ).not.toBeInTheDocument();
  expect(
    screen.queryByText("Toda la escuela · todos los planes · v4"),
  ).not.toBeInTheDocument();
  expect(
    screen.queryByText("Asignaciones del aula seleccionada"),
  ).not.toBeInTheDocument();
});
it("clears previously loaded data when an assignment is revoked", async () => {
  const user = userEvent.setup();
  render(<App />);
  await user.click(
    await screen.findByRole("button", { name: "Banco de temas" }),
  );
  await screen.findByText("Topic a");
  vi.mocked(api.get).mockRejectedValue(
    new ApiError(403, { message: "Not permitted" }),
  );
  await user.click(
    screen.getByRole("button", { name: "Recargar planificación" }),
  );
  await screen.findByText(/El aula no está disponible/);
  expect(screen.queryByText("Topic a")).not.toBeInTheDocument();
});
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
it("removes prototype notices from login and workspace", async () => {
  vi.mocked(api.get).mockRejectedValueOnce(new ApiError(401, {}));
  const login = render(<App />);
  await screen.findByRole("button", { name: "Ingresar a mi escuela" });
  expect(
    screen.queryByRole("note", { name: "Prototipo académico" }),
  ).not.toBeInTheDocument();
  login.unmount();
  render(<App />);
  await screen.findByText("Topic a");
  expect(
    screen.queryByRole("note", { name: "Prototipo académico" }),
  ).not.toBeInTheDocument();
  expect(
    screen.queryByRole("button", { name: "Demostración con datos simulados" }),
  ).not.toBeInTheDocument();
});
it("shows owner-only scope tabs and restores the selected school plan", async () => {
  const base = vi.mocked(api.get).getMockImplementation()!;
  vi.mocked(api.get).mockImplementation(async (path) => {
    if (path === "/platform/schools") return [];
    const result = await base(path);
    return path === "/me"
      ? { ...(result as object), superadmin: true }
      : result;
  });
  render(<App />);
  await screen.findByText("Topic a");
  await userEvent.selectOptions(screen.getByLabelText("Año del plan"), "2027");
  await screen.findByText("Topic b");
  await userEvent.click(
    screen.getByRole("tab", { name: "Superadministrador" }),
  );
  await screen.findByRole("heading", { name: "Administración de colegios" });
  expect(screen.queryByText("Topic b")).not.toBeInTheDocument();
  await userEvent.click(
    screen.getByRole("tab", { name: "Administrador de mi colegio" }),
  );
  await screen.findByText("Topic b");
  expect(screen.getByLabelText("Año del plan")).toHaveValue("2027");
});
it("does not offer global tabs to an ordinary school administrator", async () => {
  render(<App />);
  await screen.findByText("Topic a");
  expect(
    screen.queryByRole("tab", { name: "Superadministrador" }),
  ).not.toBeInTheDocument();
  expect(
    vi
      .mocked(api.get)
      .mock.calls.some(([path]) => path.startsWith("/platform/")),
  ).toBe(false);
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

it("does not fetch a legacy default or section A while the destination is pending", async () => {
  const original = vi.mocked(api.get).getMockImplementation()!;
  vi.mocked(api.get).mockImplementation((path) =>
    path === "/contexts"
      ? Promise.resolve([
          { ...contexts[0], section: "B" },
          { ...contexts[1], section: "A" },
        ])
      : original(path),
  );
  render(<App />);
  await screen.findByText("Topic a");
  vi.mocked(api.get).mockClear();
  await userEvent.selectOptions(screen.getByLabelText("Año del plan"), "2027");
  expect(
    screen.getByText("Selecciona o crea un plan para comenzar."),
  ).toBeVisible();
  expect(screen.queryByText("Topic a")).not.toBeInTheDocument();
  expect(screen.queryByText("Topic b")).not.toBeInTheDocument();
  expect(api.get).not.toHaveBeenCalled();
  await userEvent.selectOptions(
    screen.getByLabelText("Grado y sección del plan"),
    "b",
  );
  await screen.findByText("Topic b");
  expect(
    vi
      .mocked(api.get)
      .mock.calls.every(
        ([path]) => path === "/users" || path.includes("contextId=b"),
      ),
  ).toBe(true);
});
it("does not replace a newly confirmed context with a late initial context list", async () => {
  const original = vi.mocked(api.get).getMockImplementation()!;
  let finish!: (value: unknown) => void;
  vi.mocked(api.get).mockImplementation((path) =>
    path === "/contexts"
      ? new Promise((resolve) => {
          finish = resolve;
        })
      : original(path),
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
  await act(async () => finish([contexts[0]]));
  expect(screen.getByText("Topic b")).toBeVisible();
  expect(screen.queryByText("Topic a")).not.toBeInTheDocument();
});

it("keeps global administration usable when school planning fails", async () => {
  const base = vi.mocked(api.get).getMockImplementation()!;
  vi.mocked(api.get).mockImplementation(async (path) => {
    if (path === "/platform/schools") return [];
    if (path.startsWith("/topics?")) throw new ApiError(500, {});
    const result = await base(path);
    return path === "/me"
      ? { ...(result as object), superadmin: true }
      : result;
  });
  render(<App />);
  await userEvent.click(
    await screen.findByRole("tab", { name: "Superadministrador" }),
  );
  await screen.findByRole("heading", { name: "Administración de colegios" });
  await screen.findByText("No hay colegios registrados.");
});
it("drops global controls on revoked capability and preserves local planning", async () => {
  const base = vi.mocked(api.get).getMockImplementation()!;
  vi.mocked(api.get).mockImplementation(async (path) => {
    if (path === "/platform/schools") return [];
    const result = await base(path);
    return path === "/me"
      ? { ...(result as object), superadmin: true }
      : result;
  });
  render(<App />);
  await screen.findByText("Topic a");
  await userEvent.click(
    screen.getByRole("tab", { name: "Superadministrador" }),
  );
  await screen.findByText("No hay colegios registrados.");
  vi.mocked(api.get).mockRejectedValueOnce(new ApiError(403, {}));
  await userEvent.click(
    screen.getByRole("button", { name: "Actualizar colegios" }),
  );
  await screen.findByText("Topic a");
  expect(
    screen.queryByRole("tab", { name: "Superadministrador" }),
  ).not.toBeInTheDocument();
  expect(
    screen.queryByRole("heading", { name: "Administración de colegios" }),
  ).not.toBeInTheDocument();
});
it("does not restore a late global list after logout", async () => {
  const base = vi.mocked(api.get).getMockImplementation()!;
  let resolve!: (value: unknown) => void;
  vi.mocked(api.logout).mockResolvedValue(undefined);
  vi.mocked(api.get).mockImplementation(async (path) => {
    if (path === "/platform/schools")
      return new Promise((done) => {
        resolve = done;
      });
    const result = await base(path);
    return path === "/me"
      ? { ...(result as object), superadmin: true }
      : result;
  });
  render(<App />);
  await screen.findByText("Topic a");
  await userEvent.click(
    screen.getByRole("tab", { name: "Superadministrador" }),
  );
  await waitFor(() => expect(resolve).toBeDefined());
  await userEvent.click(screen.getByRole("button", { name: "Cerrar sesión" }));
  await screen.findByRole("button", { name: "Ingresar a mi escuela" });
  await act(async () =>
    resolve([
      {
        id: "old",
        name: "Late private school",
        year: 2026,
        grade: 6,
        administrators: [],
      },
    ]),
  );
  expect(screen.queryByText("Late private school")).not.toBeInTheDocument();
});
