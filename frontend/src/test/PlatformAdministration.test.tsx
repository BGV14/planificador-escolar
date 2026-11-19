import { act, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, expect, it, vi } from "vitest";
import { PlatformAdministration } from "../components/PlatformAdministration";
import { api, ApiError } from "../lib/api";
vi.mock("../lib/api", async () => ({
  ...(await vi.importActual("../lib/api")),
  api: { get: vi.fn(), mutate: vi.fn() },
}));
const schools = [
  {
    id: "school/one",
    name: "Colegio Uno",
    year: 2030,
    grade: 1,
    administrators: [
      {
        id: "admin",
        schoolId: "school/one",
        username: "director",
        role: "ADMIN",
      },
    ],
  },
];
beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(api.get).mockResolvedValue(schools);
  vi.mocked(api.mutate).mockResolvedValue({});
});
it("lists school metadata and creates an empty Initial context without storing the password", async () => {
  const storage = vi.spyOn(Storage.prototype, "setItem");
  render(<PlatformAdministration onAccessLost={vi.fn()} />);
  await screen.findByText("Administradores: director");
  await userEvent.type(
    screen.getByLabelText("Nombre del colegio"),
    "Nueva escuela",
  );
  await userEvent.selectOptions(screen.getByLabelText("Nivel"), "INITIAL");
  expect(screen.getByLabelText("Edad")).toHaveValue(0);
  await userEvent.clear(screen.getByLabelText("Año"));
  await userEvent.type(screen.getByLabelText("Año"), "2035");
  await userEvent.type(
    screen.getByLabelText("Usuario del administrador"),
    "newadmin",
  );
  const password = screen.getByLabelText("Contraseña del administrador");
  expect(password).toHaveAttribute("type", "password");
  await userEvent.type(password, "synthetic-password");
  await userEvent.click(
    screen.getByRole("button", { name: "Crear colegio vacío" }),
  );
  await screen.findByText("Colegio creado sin contenido precargado.");
  expect(api.mutate).toHaveBeenCalledWith("/platform/schools", "POST", {
    name: "Nueva escuela",
    year: 2035,
    level: "INITIAL",
    stageValue: 0,
    section: "UNICA",
    adminUsername: "newadmin",
    adminPassword: "synthetic-password",
  });
  expect(password).toHaveValue("");
  expect(screen.getByLabelText("Usuario del administrador")).toHaveValue("");
  expect(storage).not.toHaveBeenCalled();
  storage.mockRestore();
});
it("creates an additional school administrator without sending a role or global grant", async () => {
  render(<PlatformAdministration onAccessLost={vi.fn()} />);
  await screen.findByText("Administradores: director");
  await userEvent.selectOptions(
    screen.getByLabelText("Colegio de destino"),
    "school/one",
  );
  await userEvent.type(
    screen.getByLabelText("Usuario del nuevo administrador"),
    "secondadmin",
  );
  await userEvent.type(
    screen.getByLabelText("Contraseña del nuevo administrador"),
    "synthetic-password",
  );
  await userEvent.click(
    screen.getByRole("button", { name: "Crear administrador" }),
  );
  await screen.findByText("Administrador creado.");
  expect(api.mutate).toHaveBeenCalledWith(
    "/platform/schools/school%2Fone/administrators",
    "POST",
    { username: "secondadmin", password: "synthetic-password" },
  );
  expect(
    screen.getByLabelText("Contraseña del nuevo administrador"),
  ).toHaveValue("");
});
it("clears global metadata and reports revoked capability without retaining rows", async () => {
  const lost = vi.fn();
  render(<PlatformAdministration onAccessLost={lost} />);
  await screen.findByText("Administradores: director");
  vi.mocked(api.get).mockRejectedValueOnce(new ApiError(403, {}));
  await userEvent.click(
    screen.getByRole("button", { name: "Actualizar colegios" }),
  );
  await waitFor(() => expect(lost).toHaveBeenCalledWith(false));
  expect(
    screen.queryByText("Administradores: director"),
  ).not.toBeInTheDocument();
});
it("ignores a previous identity's late success", async () => {
  let resolve!: (value: unknown) => void;
  vi.mocked(api.get).mockImplementationOnce(
    () =>
      new Promise((done) => {
        resolve = done;
      }),
  );
  const lost = vi.fn();
  const previous = render(<PlatformAdministration onAccessLost={lost} />);
  previous.unmount();
  render(<PlatformAdministration onAccessLost={lost} />);
  await screen.findByText("Administradores: director");
  await act(async () =>
    resolve([
      { ...schools[0], name: "Old private school", administrators: [] },
    ]),
  );
  expect(screen.queryByText("Old private school")).not.toBeInTheDocument();
  expect(lost).not.toHaveBeenCalled();
});
it("does not refresh or restore global data when a pending mutation completes after unmount", async () => {
  let resolve!: (value: unknown) => void;
  vi.mocked(api.mutate).mockImplementationOnce(
    () =>
      new Promise((done) => {
        resolve = done;
      }),
  );
  const previous = render(<PlatformAdministration onAccessLost={vi.fn()} />);
  await screen.findByText("Administradores: director");
  await userEvent.selectOptions(
    screen.getByLabelText("Colegio de destino"),
    "school/one",
  );
  await userEvent.type(
    screen.getByLabelText("Usuario del nuevo administrador"),
    "secondadmin",
  );
  await userEvent.type(
    screen.getByLabelText("Contraseña del nuevo administrador"),
    "synthetic-password",
  );
  await userEvent.click(
    screen.getByRole("button", { name: "Crear administrador" }),
  );
  expect(
    screen.getByRole("button", { name: "Crear administrador" }),
  ).toBeDisabled();
  previous.unmount();
  await act(async () => resolve({}));
  expect(api.get).toHaveBeenCalledTimes(1);
});

it("does not revoke the current identity when a previous view rejects late", async () => {
  let reject!: (reason: unknown) => void;
  vi.mocked(api.get).mockImplementationOnce(
    () =>
      new Promise((_done, fail) => {
        reject = fail;
      }),
  );
  const lost = vi.fn();
  const previous = render(<PlatformAdministration onAccessLost={lost} />);
  previous.unmount();
  render(<PlatformAdministration onAccessLost={lost} />);
  await screen.findByText("Administradores: director");
  await act(async () => reject(new ApiError(403, {})));
  expect(lost).not.toHaveBeenCalled();
  expect(screen.getByText("Administradores: director")).toBeInTheDocument();
});
