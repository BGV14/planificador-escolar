import { describe, it, expect } from "vitest";
import {
  calendarDays,
  isoDate,
  monthConflicts,
  sessionsOnDate,
  canEdit,
  previewTotals,
} from "./planning";
import type { Session, Topic } from "./types";
describe("exact-date planning", () => {
  it("uses Monday weeks and includes weekends and all dates", () => {
    const days = calendarDays(2026, 3);
    expect(days[0]).toBe("2026-02-23");
    expect(days).toContain("2026-03-01");
    expect(days).toContain("2026-03-31");
    expect(days.length % 7).toBe(0);
  });
  it("does not convert local calendar dates through UTC", () =>
    expect(isoDate(2026, 3, 1)).toBe("2026-03-01"));
  it("preserves every topic on a blocked or dense day", () => {
    const sessions = Array.from(
      { length: 12 },
      (_, i) =>
        ({
          id: `s${i}`,
          topicId: `t${i}`,
          exactDate: "2026-03-02",
          assignment: "Abril",
        }) as Session,
    );
    const topics = sessions.map(
      (s) => ({ id: s.topicId, areaId: "a" }) as Topic,
    );
    expect(sessionsOnDate(sessions, topics, "2026-03-02", "a")).toHaveLength(
      12,
    );
    expect(monthConflicts(sessions)).toHaveLength(12);
  });
  it("never grants another area owner editing implicitly", () => {
    const a = { id: "a", name: "Math", ownerId: "other", sharedEdit: false };
    expect(canEdit({ id: "me", role: "TEACHER" } as never, a)).toBe(false);
    expect(
      canEdit({ id: "me", role: "TEACHER" } as never, {
        ...a,
        sharedEdit: true,
      }),
    ).toBe(true);
  });
  it("sums incoming field counts rather than counting topics twice", () =>
    expect(
      previewTotals({
        fields: {
          dates: {
            incoming: 3,
            added: 1,
            updated: 1,
            unchanged: 1,
            explicitEmpty: 0,
          },
          notes: {
            incoming: 2,
            added: 0,
            updated: 2,
            unchanged: 0,
            explicitEmpty: 1,
          },
        },
        affectedTopics: 3,
      }),
    ).toEqual({
      incoming: 5,
      added: 1,
      updated: 3,
      unchanged: 1,
      explicitEmpty: 1,
    }));
});
