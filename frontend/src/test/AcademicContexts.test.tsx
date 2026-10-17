import { render, screen, within, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, expect, it, vi } from "vitest";
import { AcademicContexts } from "../components/AcademicContexts";
import { api } from "../lib/api";
vi.mock("../lib/api", async () => ({
  ...(await vi.importActual("../lib/api")),
  api: { mutate: vi.fn() },
}));
beforeEach(() => vi.clearAllMocks());
function view() {
  return render(
    <AcademicContexts
      contexts={[]}
      selected=""
      admin
      onSelect={vi.fn()}
      onCreated={vi.fn()}
    />,
  );
}
it("offers all legal ages and grades without inventing an existing plan", async () => {
  view();
  expect(screen.queryByLabelText("Año del plan")).not.toBeInTheDocument();
  await userEvent.click(
    screen.getByText("Crear plan para otro año, nivel o etapa"),
  );
  await userEvent.selectOptions(
    screen.getByLabelText("Nivel del nuevo plan"),
    "INITIAL",
  );
  const ages = screen.getByLabelText("Edad del nuevo plan");
  expect(within(ages).getAllByRole("option")).toHaveLength(6);
  expect(
    within(ages).getByRole("option", { name: "Menores de 1 año" }),
  ).toHaveValue("0");
  expect(
    screen.queryByLabelText("Grado del nuevo plan"),
  ).not.toBeInTheDocument();
  await userEvent.selectOptions(
    screen.getByLabelText("Nivel del nuevo plan"),
    "SECONDARY",
  );
  expect(
    within(screen.getByLabelText("Grado del nuevo plan")).getAllByRole(
      "option",
    ),
  ).toHaveLength(5);
  await userEvent.selectOptions(
    screen.getByLabelText("Nivel del nuevo plan"),
    "PRIMARY",
  );
  expect(
    within(screen.getByLabelText("Grado del nuevo plan")).getAllByRole(
      "option",
    ),
  ).toHaveLength(6);
});
it("creates a separately editable future-year plan only after server confirmation", async () => {
  const created = vi.fn();
  vi.mocked(api.mutate).mockResolvedValue({ id: "new" });
  render(
    <AcademicContexts
      contexts={[]}
      selected=""
      admin
      onSelect={vi.fn()}
      onCreated={created}
    />,
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
  await waitFor(() =>
    expect(api.mutate).toHaveBeenCalledWith("/contexts", "POST", {
      year: 2027,
      level: "PRIMARY",
      stageValue: 3,
    }),
  );
  expect(created).toHaveBeenCalledWith({ id: "new" });
});
it("keeps context creation administrative and constrains supported years", () => {
  const { rerender } = view();
  expect(screen.getByLabelText("Año del nuevo plan")).toHaveAttribute(
    "min",
    "1900",
  );
  expect(screen.getByLabelText("Año del nuevo plan")).toHaveAttribute(
    "max",
    "2200",
  );
  rerender(
    <AcademicContexts
      contexts={[]}
      selected=""
      admin={false}
      onSelect={vi.fn()}
      onCreated={vi.fn()}
    />,
  );
  expect(
    screen.queryByRole("button", { name: "Crear plan vacío" }),
  ).not.toBeInTheDocument();
});
