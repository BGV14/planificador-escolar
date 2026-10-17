import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { EntityEditor } from "../components/EntityEditor";
import { api } from "../lib/api";
import type { PlannerData, Session, User } from "../lib/types";

vi.mock("../lib/api", async () => {
  const actual = await vi.importActual("../lib/api");
  return { ...actual, api: { mutate: vi.fn() } };
});
const session: Session = {
  id: "session",
  topicId: "topic",
  assignment: null,
  status: null,
  exactDate: null,
  notes: null,
  version: 4,
};
const data: PlannerData = {
  areas: [
    { id: "area", name: "Matemática", ownerId: "teacher", sharedEdit: false },
  ],
  topics: [
    { id: "topic", areaId: "area", name: "Conjuntos", unit: 1, legacyId: null },
  ],
  sessions: [session],
  schedules: [],
  blocks: [],
  teachers: [],
  history: [],
  plan: {
    id: "ctx",
    schoolId: "school",
    level: "PRIMARY",
    stageKind: "GRADE",
    stageValue: 6,
    cycle: null,
    displayLabel: "2026 · Primaria · 6° grado",
    legacyDefault: true,
    year: 2026,
    grade: 6,
    revision: 0,
    warnings: [],
  },
};
const teacher = { id: "teacher", role: "TEACHER" } as User;
function editor(item?: Session, date?: string) {
  return (
    <EntityEditor
      editor={{ kind: "sessions", item, topicId: "topic", date }}
      data={data}
      user={teacher}
      areaId="area"
      onClose={vi.fn()}
      onSaved={vi.fn().mockResolvedValue(undefined)}
      onReload={vi.fn().mockResolvedValue(undefined)}
    />
  );
}
describe("nullable existing session fields", () => {
  beforeEach(() => vi.mocked(api.mutate).mockReset().mockResolvedValue({}));
  it("renders a null assignment as unassigned rather than March", () => {
    render(editor(session));
    expect(screen.getByRole("combobox", { name: "Mes asignado" })).toHaveValue(
      "",
    );
  });
  it("preserves null assignment and untouched fields when editing notes only", async () => {
    const user = userEvent.setup();
    render(editor(session));
    await user.type(
      screen.getByRole("textbox", { name: "Notas" }),
      "Notas editadas",
    );
    await user.click(screen.getByRole("button", { name: "Guardar cambios" }));
    await waitFor(() =>
      expect(api.mutate).toHaveBeenCalledWith(
        "/sessions/session?contextId=ctx",
        "PUT",
        {
          topicId: "topic",
          version: 4,
          assignment: null,
          status: null,
          date: null,
          notes: "Notas editadas",
        },
      ),
    );
  });
  it("still intentionally defaults new sessions to March without an exact date", () => {
    render(editor());
    expect(screen.getByRole("combobox", { name: "Mes asignado" })).toHaveValue(
      "Marzo",
    );
  });
});

it("constrains custom session dates to the chosen future year", () => {
  const future = {
    ...data,
    plan: {
      ...data.plan,
      year: 2027,
      displayLabel: "2027 · Inicial · Menores de 1 año",
      grade: null,
      level: "INITIAL" as const,
      stageKind: "AGE" as const,
      stageValue: 0,
    },
  };
  render(
    <EntityEditor
      editor={{ kind: "sessions", topicId: "topic" }}
      data={future}
      user={teacher}
      areaId="area"
      onClose={vi.fn()}
      onSaved={vi.fn()}
      onReload={vi.fn()}
    />,
  );
  expect(screen.getByLabelText("Fecha exacta")).toHaveAttribute(
    "min",
    "2027-01-01",
  );
  expect(screen.getByLabelText("Fecha exacta")).toHaveAttribute(
    "max",
    "2027-12-31",
  );
  expect(screen.getByRole("button", { name: "Guardar cambios" })).toBeEnabled();
});
