import { afterEach, describe, expect, it, vi } from "vitest";
import { ApiClient, contextPath } from "../lib/api";

function nativeLikeFetch() {
  return vi.fn(function (
    this: unknown,
    _input: RequestInfo | URL,
    _init?: RequestInit,
  ) {
    if (this !== globalThis)
      throw new TypeError(
        "Failed to execute fetch on Window: Illegal invocation",
      );
    return Promise.resolve(
      new Response(
        JSON.stringify({ token: "synthetic", headerName: "X-CSRF" }),
      ),
    );
  });
}

describe("fetch platform receiver", () => {
  afterEach(() => vi.unstubAllGlobals());

  it("invokes the default browser fetch with its global receiver", async () => {
    const fetcher = nativeLikeFetch();
    vi.stubGlobal("fetch", fetcher);
    await expect(new ApiClient().get("/csrf")).resolves.toEqual({
      token: "synthetic",
      headerName: "X-CSRF",
    });
    expect(fetcher.mock.contexts).toEqual([globalThis]);
    expect(fetcher).toHaveBeenCalledWith("/api/csrf", {
      credentials: "same-origin",
      cache: "no-store",
    });
  });

  it("also preserves dependency injection for native-like strict fetchers", async () => {
    const fetcher = nativeLikeFetch();
    await expect(new ApiClient(fetcher).get("/csrf")).resolves.toMatchObject({
      token: "synthetic",
    });
    expect(fetcher.mock.contexts).toEqual([globalThis]);
    expect(fetcher).toHaveBeenCalledOnce();
  });
});

it("appends explicit context identifiers without dropping session versions", () => {
  expect(contextPath("/sessions/s?version=3", "ctx a")).toBe(
    "/sessions/s?version=3&contextId=ctx%20a",
  );
  expect(() => contextPath("/topics", "")).toThrow();
});
it("does not initiate a stale-context download after its response arrives", async () => {
  const fetcher = vi.fn().mockResolvedValue(new Response("file"));
  const create = vi.fn();
  vi.stubGlobal("URL", { createObjectURL: create });
  await new ApiClient(fetcher).download(
    "/exports/monthly?contextId=old",
    "old.pdf",
    () => false,
  );
  expect(create).not.toHaveBeenCalled();
  vi.unstubAllGlobals();
});
