import { render, screen } from "@testing-library/react";
import { describe, it, expect, vi } from "vitest";
import { Calendar } from "../components/Calendar";
import type { PlannerData } from "../lib/types";
describe("calendar rendering without hidden overflow", () => {
  it("shows all dense-day topics even beneath a calendar block", () => {
    const topics = Array.from({ length: 12 }, (_, i) => ({
      id: `t${i}`,
      areaId: "a",
      name: `Topic ${i}`,
      unit: 1,
      legacyId: null,
    }));
    const data = {
      topics,
      areas: [{ id: "a", name: "Area", ownerId: "u", sharedEdit: false }],
      sessions: topics.map((t, i) => ({
        id: `s${i}`,
        topicId: t.id,
        exactDate: "2026-03-02",
        assignment: "Marzo",
        status: "",
        notes: "",
        version: 0,
      })),
      blocks: [
        {
          id: "b",
          areaId: null,
          label: "Examen",
          kind: "EXAM",
          startDate: "2026-03-02",
          endDate: "2026-03-02",
        },
      ],
      schedules: [],
      history: [],
      teachers: [],
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
    } as PlannerData;
    render(
      <Calendar
        data={data}
        areaId="a"
        month={3}
        editable
        onSession={vi.fn()}
        onNew={vi.fn()}
      />,
    );
    expect(screen.getAllByRole("button", { name: /Topic \d/ })).toHaveLength(
      12,
    );
    expect(screen.getByText("Sábado")).toBeInTheDocument();
    expect(screen.getByText("Domingo")).toBeInTheDocument();
    expect(screen.getAllByText("Conflicto con bloqueo")).toHaveLength(12);
  });
});
