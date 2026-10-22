import { describe, expect, it } from "vitest";
import { buildAnnualCoverage } from "./coverage";
import type { Area, PlannerData, Session, Topic } from "./types";

const area = (id = "math"): Area => ({
  id,
  name: id,
  ownerId: "teacher",
  sharedEdit: false,
});
const topic = (id: string, areaId = "math"): Topic => ({
  id,
  areaId,
  name: id,
  unit: 1,
  legacyId: null,
});
const session = (topicId: string, fields: Partial<Session> = {}): Session => ({
  id: `session-${topicId}`,
  topicId,
  assignment: null,
  status: null,
  exactDate: null,
  notes: null,
  version: 0,
  ...fields,
});
const input = (
  topics: Topic[] = [],
  sessions: Session[] = [],
  areas: Area[] = [area()],
): Pick<PlannerData, "areas" | "topics" | "sessions"> => ({
  areas,
  topics,
  sessions,
});

describe("annual curriculum coverage", () => {
  it("counts every catalog topic, including topics with no sessions", () => {
    const result = buildAnnualCoverage(
      input(
        [topic("done"), topic("scheduled"), topic("missing")],
        [
          session("done", { status: "Trabajado" }),
          session("scheduled", { assignment: "Marzo" }),
        ],
      ),
    );
    expect(result.totals).toEqual({
      total: 3,
      completed: 1,
      planned: 1,
      unplanned: 1,
      completionPercent: 100 / 3,
    });
    expect(result.areas[0].topics.map((entry) => entry.status)).toEqual([
      "completed",
      "planned",
      "unplanned",
    ]);
  });

  it("lets exact Trabajado win without a date and across multiple sessions", () => {
    const result = buildAnnualCoverage(
      input(
        [topic("one")],
        [
          session("one", { assignment: "Abril" }),
          session("one", { id: "second", status: "Trabajado" }),
          session("one", { id: "third", exactDate: "2026-12-01" }),
        ],
      ),
    );
    expect(result.totals).toEqual({
      total: 1,
      completed: 1,
      planned: 0,
      unplanned: 0,
      completionPercent: 100,
    });
  });

  it.each([
    { assignment: "  Marzo  " },
    { exactDate: "2026-01-01" },
    { exactDate: "2026-12-31" },
  ])(
    "includes annual planning evidence %j without a month filter",
    (fields) => {
      const result = buildAnnualCoverage(
        input([topic("one")], [session("one", fields)]),
      );
      expect(result.totals.planned).toBe(1);
      expect(result.totals.completed).toBe(0);
    },
  );

  it.each([null, "", "   ", "trabajado", " Trabajado ", "Pendiente"])(
    "does not infer completion from status %j or blank planning fields",
    (status) => {
      const result = buildAnnualCoverage(
        input(
          [topic("one")],
          [session("one", { status, assignment: " \t", exactDate: "  " })],
        ),
      );
      expect(result.totals.unplanned).toBe(1);
      expect(result.areas[0].topics[0].status).toBe("unplanned");
    },
  );

  it("keeps distinct area counts and ordered topic details consistent", () => {
    const result = buildAnnualCoverage(
      input(
        [
          topic("science-done", "science"),
          topic("math-empty"),
          topic("math-plan"),
        ],
        [
          session("science-done", { status: "Trabajado" }),
          session("math-plan", { exactDate: "2026-10-18" }),
        ],
        [area(), area("science")],
      ),
    );
    expect(result.areas.map((entry) => entry.area.id)).toEqual([
      "math",
      "science",
    ]);
    expect(result.areas[0].topics.map((entry) => entry.topic.id)).toEqual([
      "math-empty",
      "math-plan",
    ]);
    for (const summary of [...result.areas, result.totals]) {
      expect(summary.completed + summary.planned + summary.unplanned).toBe(
        summary.total,
      );
    }
    expect(result.areas[0].completionPercent).toBe(0);
    expect(result.areas[1].completionPercent).toBe(100);
  });

  it("ignores orphan topics and sessions instead of creating phantom areas", () => {
    const result = buildAnnualCoverage(
      input(
        [topic("known"), topic("orphan", "missing-area")],
        [
          session("orphan", { status: "Trabajado" }),
          session("missing-topic", { status: "Trabajado" }),
        ],
      ),
    );
    expect(result.areas).toHaveLength(1);
    expect(result.totals.total).toBe(1);
    expect(result.totals.unplanned).toBe(1);
  });

  it("returns null percentages for empty areas and an empty catalog", () => {
    const result = buildAnnualCoverage(input());
    expect(result.totals).toEqual({
      total: 0,
      completed: 0,
      planned: 0,
      unplanned: 0,
      completionPercent: null,
    });
    expect(result.areas[0].completionPercent).toBeNull();
    expect(result.areas[0].topics).toEqual([]);
    expect(buildAnnualCoverage(input([], [], [])).areas).toEqual([]);
  });

  it("is deterministic and does not mutate frozen input collections or records", () => {
    const data = input(
      [topic("one")],
      [session("one", { assignment: "Marzo" })],
    );
    for (const collection of [data.areas, data.topics, data.sessions]) {
      collection.forEach(Object.freeze);
      Object.freeze(collection);
    }
    Object.freeze(data);
    const before = JSON.stringify(data);
    expect(buildAnnualCoverage(data)).toEqual(buildAnnualCoverage(data));
    expect(JSON.stringify(data)).toBe(before);
  });
});
