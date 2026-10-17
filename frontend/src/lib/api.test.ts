import { describe, it, expect, vi, beforeEach } from "vitest";
import { ApiClient, ApiError, errorMessage } from "./api";
const response = (body: unknown, status = 200) =>
  new Response(body === null ? null : JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
describe("authenticated API client", () => {
  beforeEach(() => vi.restoreAllMocks());
  it("refreshes CSRF after login and sends same-origin cookies", async () => {
    const fetcher = vi
      .fn()
      .mockResolvedValueOnce(
        response({ token: "before", headerName: "X-CSRF" }),
      )
      .mockResolvedValueOnce(response(null))
      .mockResolvedValueOnce(response({ token: "after", headerName: "X-CSRF" }))
      .mockResolvedValueOnce(response({ id: "u" }))
      .mockResolvedValueOnce(response({ id: "a" }));
    const api = new ApiClient(fetcher);
    await api.login("teacher", "private-password");
    await api.mutate("/areas", "POST", { name: "Area" });
    expect(fetcher.mock.calls[1][1].body).toContain("username=teacher");
    expect(fetcher.mock.calls[4][1].headers["X-CSRF"]).toBe("after");
    expect(
      fetcher.mock.calls.every((c) => c[1].credentials === "same-origin"),
    ).toBe(true);
  });
  it("surfaces stale previews without silently retrying a mutation", async () => {
    const fetcher = vi
      .fn()
      .mockResolvedValueOnce(response({ token: "t", headerName: "X-CSRF" }))
      .mockResolvedValueOnce(response({ message: "STALE_PREVIEW" }, 409));
    const api = new ApiClient(fetcher);
    await expect(
      api.mutate("/imports/confirm", "POST", {}),
    ).rejects.toMatchObject({ status: 409 });
    expect(fetcher).toHaveBeenCalledTimes(2);
    expect(
      errorMessage(new ApiError(409, { message: "STALE_SESSION" })),
    ).toContain("Recarga");
  });
  it("retains structured validation paths for actionable feedback", () =>
    expect(
      errorMessage(
        new ApiError(400, {
          code: "INVALID_IMPORT",
          violations: [{ path: "dates.x", code: "INVALID_DATE" }],
        }),
      ),
    ).toContain("dates.x"));
});
