import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, expect, it, vi } from "vitest";
import { TeacherAssignments } from "../components/TeacherAssignments";
import { api } from "../lib/api";

vi.mock("../lib/api", async () => ({
  ...(await vi.importActual("../lib/api")),
  api: { get: vi.fn(), mutate: vi.fn() },
}));
const teachers = [{ id: "teacher", username: "Ana", role: "TEACHER" as const }];
const areas = [
  {
    id: "math",
    name: "Matemática",
    ownerId: "legacy-admin",
    sharedEdit: false,
  },
];
beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(api.get).mockResolvedValue([]);
  vi.mocked(api.mutate).mockResolvedValue(undefined);
});
it("assigns and revokes classroom tutors with the selected context", async () => {
  const user = userEvent.setup();
  const reload = vi.fn().mockResolvedValue(undefined);
  vi.mocked(api.get).mockResolvedValueOnce(teachers).mockResolvedValueOnce([]);
  render(
    <TeacherAssignments
      contextId="class-a"
      teachers={teachers}
      areas={areas}
      onChanged={reload}
    />,
  );
  await screen.findByRole("button", { name: "Revocar tutoría de Ana" });
  await user.click(
    screen.getByRole("button", { name: "Revocar tutoría de Ana" }),
  );
  await waitFor(() =>
    expect(api.mutate).toHaveBeenCalledWith(
      "/classroom-tutors/teacher?contextId=class-a",
      "DELETE",
      undefined,
    ),
  );
  await user.selectOptions(screen.getByLabelText("Docente tutor"), "teacher");
  await user.click(screen.getByRole("button", { name: "Asignar tutoría" }));
  await waitFor(() =>
    expect(api.mutate).toHaveBeenCalledWith(
      "/classroom-tutors?contextId=class-a",
      "POST",
      { userId: "teacher" },
    ),
  );
  expect(reload).toHaveBeenCalled();
});
it("transfers course responsibility without rewriting legacy owners automatically", async () => {
  const user = userEvent.setup();
  render(
    <TeacherAssignments
      contextId="class-b"
      teachers={teachers}
      areas={areas}
      onChanged={vi.fn()}
    />,
  );
  expect(api.mutate).not.toHaveBeenCalled();
  await waitFor(() =>
    expect(screen.getByLabelText("Responsable de Matemática")).toBeEnabled(),
  );
  await user.selectOptions(
    screen.getByLabelText("Responsable de Matemática"),
    "teacher",
  );
  await waitFor(() =>
    expect(api.mutate).toHaveBeenCalledWith(
      "/areas/math?contextId=class-b",
      "PUT",
      { name: "Matemática", ownerId: "teacher", sharedEdit: false },
    ),
  );
});
