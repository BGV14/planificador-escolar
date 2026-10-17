import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, it, expect, vi } from "vitest";
import { Imports } from "../components/Imports";
import { api } from "../lib/api";
import type { PlannerData } from "../lib/types";
vi.mock("../lib/api", async () => {
  const actual = await vi.importActual("../lib/api");
  return { ...actual, api: { mutate: vi.fn() } };
});
const data = {
  plan: { id: "ctx", level: "PRIMARY", stageValue: 6 },
  history: [],
  teachers: [],
} as unknown as PlannerData;
const preview = {
  revision: 7,
  result: {
    preview: {
      affectedTopics: 1,
      fields: {
        dates: {
          incoming: 1,
          added: 0,
          updated: 1,
          unchanged: 0,
          explicitEmpty: 0,
        },
      },
    },
    warnings: [],
  },
};
describe("import confirmation boundary", () => {
  beforeEach(() => vi.clearAllMocks());
  it("requires preview and explicit incoming-wins consent, invalidates consent on edits", async () => {
    vi.mocked(api.mutate).mockResolvedValue(preview);
    const user = userEvent.setup();
    render(<Imports data={data} onSaved={vi.fn()} />);
    const input = screen.getByRole("textbox", {
      name: "Contenido JSON del respaldo",
    });
    await user.type(input, "payload");
    await user.click(
      screen.getByRole("button", { name: "Generar vista previa" }),
    );
    const confirm = await screen.findByRole("button", {
      name: "Confirmar importación",
    });
    expect(confirm).toBeDisabled();
    await user.click(screen.getByRole("checkbox"));
    expect(confirm).toBeEnabled();
    await user.type(input, "changed");
    expect(
      screen.queryByRole("button", { name: "Confirmar importación" }),
    ).not.toBeInTheDocument();
    expect(api.mutate).toHaveBeenCalledTimes(1);
  });
  it("confirms exactly the previewed text and revision", async () => {
    vi.mocked(api.mutate)
      .mockResolvedValueOnce(preview)
      .mockResolvedValueOnce({});
    const saved = vi.fn().mockResolvedValue(undefined);
    const user = userEvent.setup();
    render(<Imports data={data} onSaved={saved} />);
    await user.type(
      screen.getByRole("textbox", { name: "Contenido JSON del respaldo" }),
      "payload",
    );
    await user.click(
      screen.getByRole("button", { name: "Generar vista previa" }),
    );
    await user.click(await screen.findByRole("checkbox"));
    await user.click(
      screen.getByRole("button", { name: "Confirmar importación" }),
    );
    await waitFor(() => expect(saved).toHaveBeenCalledOnce());
    expect(api.mutate).toHaveBeenLastCalledWith(
      "/imports/confirm?contextId=ctx",
      "POST",
      {
        json: "payload",
        revision: 7,
        acknowledgeIncomingWins: true,
      },
    );
  });
});

it("does not offer legacy import into an incompatible context", () => {
  vi.clearAllMocks();
  render(
    <Imports
      data={{
        ...data,
        plan: {
          ...data.plan,
          level: "INITIAL",
          grade: null,
          stageValue: 0,
          displayLabel: "2027 · Inicial · Menores de 1 año",
        },
      }}
      onSaved={vi.fn()}
    />,
  );
  expect(
    screen.getByText("Importación heredada no compatible"),
  ).toBeInTheDocument();
  expect(screen.queryByRole("textbox")).not.toBeInTheDocument();
  expect(api.mutate).not.toHaveBeenCalled();
});
