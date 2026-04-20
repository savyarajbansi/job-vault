import { beforeEach, describe, expect, it, vi } from "vitest";

const ACCESS_TOKEN_KEY = "jobvault.accessToken";

type JsonBody = Record<string, unknown> | null;

type Deferred<T> = {
  promise: Promise<T>;
  resolve: (value: T) => void;
  reject: (reason?: unknown) => void;
};

function createDeferred<T>(): Deferred<T> {
  let resolve!: (value: T) => void;
  let reject!: (reason?: unknown) => void;
  const promise = new Promise<T>((res, rej) => {
    resolve = res;
    reject = rej;
  });
  return { promise, resolve, reject };
}

function jsonResponse(status: number, body?: JsonBody): Response {
  const payload = body === null || status === 204 ? undefined : JSON.stringify(body);
  return new Response(payload, {
    status,
    headers: { "Content-Type": "application/json" }
  });
}

function textResponse(status: number, body: string, statusText: string): Response {
  return new Response(body, {
    status,
    statusText,
    headers: { "Content-Type": "text/plain" }
  });
}

function installBrowserShims(): void {
  const memory = new Map<string, string>();
  const localStorageMock: Storage = {
    get length() {
      return memory.size;
    },
    clear() {
      memory.clear();
    },
    getItem(key: string) {
      return memory.has(key) ? memory.get(key)! : null;
    },
    key(index: number) {
      return Array.from(memory.keys())[index] ?? null;
    },
    removeItem(key: string) {
      memory.delete(key);
    },
    setItem(key: string, value: string) {
      memory.set(key, value);
    }
  };

  Object.defineProperty(globalThis, "localStorage", {
    value: localStorageMock,
    writable: true,
    configurable: true
  });

  Object.defineProperty(globalThis, "document", {
    value: { cookie: "csrf_token=test-csrf" },
    writable: true,
    configurable: true
  });
}

function expectAuthRecoveryError(error: unknown): void {
  expect(error).toBeInstanceOf(Error);
  expect((error as Error).message).toContain("ERR_AUTH_003");
}

function callUrl(input: RequestInfo | URL): string {
  if (typeof input === "string") {
    return input;
  }
  if (input instanceof URL) {
    return input.toString();
  }
  return input.url;
}

function callHeader(init: RequestInit | undefined, name: string): string | null {
  const headers = init?.headers;
  if (!headers) {
    return null;
  }
  if (headers instanceof Headers) {
    return headers.get(name);
  }
  if (Array.isArray(headers)) {
    const matched = headers.find(([key]) => key.toLowerCase() === name.toLowerCase());
    return matched ? matched[1] : null;
  }
  const found = Object.entries(headers).find(
    ([key]) => key.toLowerCase() === name.toLowerCase()
  );
  return found ? String(found[1]) : null;
}

beforeEach(() => {
  vi.resetModules();
  vi.restoreAllMocks();
  installBrowserShims();
});

describe("auth persistence and refresh recovery", () => {
  it("stores access token in localStorage on login", async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      jsonResponse(200, {
        accessToken: "token-login",
        accessTokenExpiresAt: "2026-04-19T23:59:00Z",
        refreshTokenExpiresAt: "2026-04-20T23:59:00Z",
        user: { id: "u-1", roles: ["EMPLOYER"] }
      })
    );
    Object.defineProperty(globalThis, "fetch", {
      value: fetchMock,
      writable: true,
      configurable: true
    });

    const auth = await import("./auth");
    await auth.login("user@example.com", "password");

    expect(globalThis.localStorage.getItem(ACCESS_TOKEN_KEY)).toBe("token-login");
  });

  it("runs a single refresh for concurrent GET requests and retries each once", async () => {
    globalThis.localStorage.setItem(ACCESS_TOKEN_KEY, "token-old");

    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(jsonResponse(401, { code: "ERR_AUTH_002", message: "expired" }))
      .mockResolvedValueOnce(jsonResponse(401, { code: "ERR_AUTH_002", message: "expired" }))
      .mockResolvedValueOnce(
        jsonResponse(200, {
          accessToken: "token-new",
          accessTokenExpiresAt: "2026-04-19T23:59:00Z",
          refreshTokenExpiresAt: "2026-04-20T23:59:00Z",
          user: { id: "u-1", roles: ["EMPLOYER"] }
        })
      )
      .mockResolvedValueOnce(jsonResponse(200, { id: "u-1", roles: ["EMPLOYER"] }))
      .mockResolvedValueOnce(jsonResponse(200, { id: "u-1", roles: ["EMPLOYER"] }));

    Object.defineProperty(globalThis, "fetch", {
      value: fetchMock,
      writable: true,
      configurable: true
    });

    const auth = await import("./auth");

    const first = auth.whoami();
    const second = auth.whoami();

    await Promise.all([first, second]);

    const refreshCalls = fetchMock.mock.calls.filter(
      ([input]) => callUrl(input as RequestInfo | URL) === "/api/auth/refresh"
    );
    expect(refreshCalls).toHaveLength(1);

    const retriedCalls = fetchMock.mock.calls.filter(
      ([input, init]) =>
        callUrl(input as RequestInfo | URL) === "/api/me" &&
        callHeader(init as RequestInit, "Authorization") === "Bearer token-new"
    );
    expect(retriedCalls).toHaveLength(2);
  });

  it("restarts queue processor when a request is queued during drain", async () => {
    globalThis.localStorage.setItem(ACCESS_TOKEN_KEY, "token-old");

    let authModule: Awaited<typeof import("./auth")> | undefined;
    let spawnedDuringDrain: Promise<unknown> | undefined;
    let spawnTriggered = false;
    let refreshCount = 0;

    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = callUrl(input);
      const authorization = callHeader(init, "Authorization");

      if (url === "/api/me" && authorization === "Bearer token-old") {
        return jsonResponse(401, { code: "ERR_AUTH_002", message: "expired" });
      }

      if (url === "/api/auth/refresh" && refreshCount === 0) {
        refreshCount += 1;
        return jsonResponse(200, {
          accessToken: "token-new",
          accessTokenExpiresAt: "2026-04-19T23:59:00Z",
          refreshTokenExpiresAt: "2026-04-20T23:59:00Z",
          user: { id: "u-1", roles: ["EMPLOYER"] }
        });
      }

      if (url === "/api/me" && authorization === "Bearer token-new" && !spawnTriggered) {
        spawnTriggered = true;
        if (authModule) {
          spawnedDuringDrain = authModule.whoami();
        }
        return jsonResponse(200, { id: "u-1", roles: ["EMPLOYER"] });
      }

      if (url === "/api/me" && authorization === "Bearer token-new" && spawnTriggered) {
        return jsonResponse(401, { code: "ERR_AUTH_002", message: "expired" });
      }

      if (url === "/api/auth/refresh" && refreshCount === 1) {
        refreshCount += 1;
        return jsonResponse(200, {
          accessToken: "token-newer",
          accessTokenExpiresAt: "2026-04-20T00:30:00Z",
          refreshTokenExpiresAt: "2026-04-21T00:30:00Z",
          user: { id: "u-1", roles: ["EMPLOYER"] }
        });
      }

      if (url === "/api/me" && authorization === "Bearer token-newer") {
        return jsonResponse(200, { id: "u-1", roles: ["EMPLOYER"] });
      }

      throw new Error(`unexpected fetch call: ${url} (${authorization ?? "no auth"})`);
    });

    Object.defineProperty(globalThis, "fetch", {
      value: fetchMock,
      writable: true,
      configurable: true
    });

    authModule = await import("./auth");

    await expect(authModule.whoami()).resolves.toEqual({ id: "u-1", roles: ["EMPLOYER"] });
    expect(spawnedDuringDrain).toBeDefined();

    await vi.waitFor(() => {
      const refreshCalls = fetchMock.mock.calls.filter(
        ([request]) => callUrl(request as RequestInfo | URL) === "/api/auth/refresh"
      );
      expect(refreshCalls).toHaveLength(2);
    });

    await expect(spawnedDuringDrain).resolves.toEqual({ id: "u-1", roles: ["EMPLOYER"] });
  });

  it("rejects non-idempotent request with ERR_AUTH_003 after refresh success", async () => {
    globalThis.localStorage.setItem(ACCESS_TOKEN_KEY, "token-old");

    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(jsonResponse(401, { code: "ERR_AUTH_002", message: "expired" }))
      .mockResolvedValueOnce(
        jsonResponse(200, {
          accessToken: "token-new",
          accessTokenExpiresAt: "2026-04-19T23:59:00Z",
          refreshTokenExpiresAt: "2026-04-20T23:59:00Z",
          user: { id: "u-1", roles: ["EMPLOYER"] }
        })
      );

    Object.defineProperty(globalThis, "fetch", {
      value: fetchMock,
      writable: true,
      configurable: true
    });

    const auth = await import("./auth");

    await expect(
      auth.authorizedRequest("/api/employer/jobs", {
        method: "POST",
        body: JSON.stringify({ title: "Role" }),
        headers: { "Content-Type": "application/json" }
      })
    ).rejects.toThrow(/ERR_AUTH_003/);

    const postRetries = fetchMock.mock.calls.filter(
      ([input]) => callUrl(input as RequestInfo | URL) === "/api/employer/jobs"
    );
    expect(postRetries).toHaveLength(1);
  });

  it("clears auth and rejects queued requests when refresh fails", async () => {
    globalThis.localStorage.setItem(ACCESS_TOKEN_KEY, "token-old");

    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(jsonResponse(401, { code: "ERR_AUTH_002", message: "expired" }))
      .mockResolvedValueOnce(jsonResponse(401, { code: "ERR_AUTH_002", message: "expired" }))
      .mockResolvedValueOnce(jsonResponse(401, { code: "ERR_AUTH_003", message: "refresh failed" }));

    Object.defineProperty(globalThis, "fetch", {
      value: fetchMock,
      writable: true,
      configurable: true
    });

    const auth = await import("./auth");

    const [first, second] = await Promise.allSettled([auth.whoami(), auth.whoami()]);

    expect(first.status).toBe("rejected");
    expect(second.status).toBe("rejected");
    expectAuthRecoveryError((first as PromiseRejectedResult).reason);
    expectAuthRecoveryError((second as PromiseRejectedResult).reason);
    expect(globalThis.localStorage.getItem(ACCESS_TOKEN_KEY)).toBeNull();

    const callCountBefore = fetchMock.mock.calls.length;
    await expect(auth.whoami()).rejects.toThrow(/ERR_AUTH_003/);
    expect(fetchMock.mock.calls).toHaveLength(callCountBefore);
  });

  it("ignores refresh result when logout happens during refresh", async () => {
    globalThis.localStorage.setItem(ACCESS_TOKEN_KEY, "token-old");

    const refreshDeferred = createDeferred<Response>();
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(jsonResponse(401, { code: "ERR_AUTH_002", message: "expired" }))
      .mockImplementationOnce(() => refreshDeferred.promise)
      .mockResolvedValueOnce(jsonResponse(204, null));

    Object.defineProperty(globalThis, "fetch", {
      value: fetchMock,
      writable: true,
      configurable: true
    });

    const auth = await import("./auth");

    const pendingWhoAmIResult: Promise<unknown> = auth
      .whoami()
      .then((value) => value)
      .catch((error: unknown) => error);

    await vi.waitFor(() => {
      expect(fetchMock.mock.calls.length).toBeGreaterThanOrEqual(2);
    });

    await auth.logout();

    refreshDeferred.resolve(
      jsonResponse(200, {
        accessToken: "token-new",
        accessTokenExpiresAt: "2026-04-19T23:59:00Z",
        refreshTokenExpiresAt: "2026-04-20T23:59:00Z",
        user: { id: "u-1", roles: ["EMPLOYER"] }
      })
    );

    const pendingWhoAmIError = await pendingWhoAmIResult;
    expectAuthRecoveryError(pendingWhoAmIError);
    expect(globalThis.localStorage.getItem(ACCESS_TOKEN_KEY)).toBeNull();

    const meRetriesAfterRefresh = fetchMock.mock.calls.filter(
      ([input, init]) =>
        callUrl(input as RequestInfo | URL) === "/api/me" &&
        callHeader(init as RequestInit, "Authorization") === "Bearer token-new"
    );
    expect(meRetriesAfterRefresh).toHaveLength(0);
  });

  it("falls back to status text for non-JSON error responses", async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      textResponse(503, "temporary outage", "Service Unavailable")
    );
    Object.defineProperty(globalThis, "fetch", {
      value: fetchMock,
      writable: true,
      configurable: true
    });

    const auth = await import("./auth");

    let thrown: unknown;
    try {
      await auth.login("user@example.com", "password");
    } catch (error) {
      thrown = error;
    }
    expect(thrown).toBeInstanceOf(Error);
    expect((thrown as Error).message).toBe("Service Unavailable");
    expect((thrown as Error).message).not.toMatch(/unexpected token|json/i);
  });

  it("falls back to status text for malformed JSON payloads", async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response("{broken-json", {
        status: 500,
        statusText: "Internal Server Error",
        headers: { "Content-Type": "application/json" }
      })
    );
    Object.defineProperty(globalThis, "fetch", {
      value: fetchMock,
      writable: true,
      configurable: true
    });

    const auth = await import("./auth");

    let thrown: unknown;
    try {
      await auth.login("user@example.com", "password");
    } catch (error) {
      thrown = error;
    }
    expect(thrown).toBeInstanceOf(Error);
    expect((thrown as Error).message).toBe("Internal Server Error");
    expect((thrown as Error).message).not.toMatch(/unexpected token|json/i);
  });
});
