export type AuthUser = {
  id: string;
  roles: string[];
};

export type AuthTokensResponse = {
  accessToken: string;
  accessTokenExpiresAt: string;
  refreshTokenExpiresAt: string;
  user: AuthUser;
};

const ACCESS_TOKEN_KEY = "jobvault.accessToken";
const CSRF_COOKIE = "csrf_token";
const AUTH_RECOVERY_CODE = "ERR_AUTH_003";
const AUTH_RECOVERY_MESSAGE = "Session expired. Please sign in again.";

type PendingRequest = {
  method: string;
  retry: () => Promise<unknown>;
  resolve: (value: unknown) => void;
  reject: (reason: unknown) => void;
};

let accessToken: string | null = loadAccessToken();
let refreshInFlight: Promise<AuthTokensResponse> | null = null;
let queueProcessor: Promise<void> | null = null;
let pendingRequests: PendingRequest[] = [];
let refreshFailed = false;
let loggedOut = false;
let authGeneration = 0;

export async function login(email: string, password: string): Promise<AuthTokensResponse> {
  const result = await request<AuthTokensResponse>("/api/auth/login", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    credentials: "include",
    body: JSON.stringify({ email, password })
  });
  applyAuthTokens(result);
  return result;
}

export async function refresh(): Promise<AuthTokensResponse> {
  if (loggedOut || refreshFailed) {
    throw authRecoveryError();
  }
  return ensureRefresh();
}

export async function logout(): Promise<void> {
  const tokenAtLogout = accessToken;
  clearAuthState(true);
  rejectPendingRequests(authRecoveryError());

  if (!tokenAtLogout) {
    return;
  }

  const csrf = readCookie(CSRF_COOKIE);
  try {
    await request<void>("/api/auth/logout", {
      method: "POST",
      credentials: "include",
      headers: {
        Authorization: `Bearer ${tokenAtLogout}`,
        ...(csrf ? { "X-CSRF-Token": csrf } : {})
      }
    });
  } catch {
    // Logout must remain terminal even if backend logout fails.
  }
}

export async function whoami(): Promise<AuthUser> {
  return authorizedRequest<AuthUser>("/api/me", { method: "GET" });
}

export async function authorizedRequest<T>(url: string, options: RequestInit = {}): Promise<T> {
  if (loggedOut || refreshFailed || !accessToken) {
    throw authRecoveryError();
  }
  return executeAuthorizedRequest<T>(url, options, false);
}

export function getAccessToken(): string | null {
  return accessToken;
}

function readCookie(name: string): string | null {
  if (typeof document === "undefined") {
    return null;
  }
  const cookies = document.cookie.split(";").map((value) => value.trim());
  for (const cookie of cookies) {
    if (cookie.startsWith(name + "=")) {
      return decodeURIComponent(cookie.substring(name.length + 1));
    }
  }
  return null;
}

function loadAccessToken(): string | null {
  if (typeof localStorage === "undefined") {
    return null;
  }
  try {
    return localStorage.getItem(ACCESS_TOKEN_KEY);
  } catch {
    return null;
  }
}

function saveAccessToken(token: string | null): void {
  if (typeof localStorage === "undefined") {
    return;
  }
  try {
    if (token) {
      localStorage.setItem(ACCESS_TOKEN_KEY, token);
    } else {
      localStorage.removeItem(ACCESS_TOKEN_KEY);
    }
  } catch {
    // Keep in-memory auth as a fallback.
  }
}

function applyAuthTokens(tokens: AuthTokensResponse): void {
  accessToken = tokens.accessToken;
  saveAccessToken(tokens.accessToken);
  refreshFailed = false;
  loggedOut = false;
  authGeneration += 1;
}

function clearAuthState(markLoggedOut: boolean): void {
  accessToken = null;
  saveAccessToken(null);
  loggedOut = markLoggedOut;
  if (markLoggedOut) {
    refreshFailed = false;
  }
  authGeneration += 1;
}

function markRefreshFailure(): void {
  accessToken = null;
  saveAccessToken(null);
  refreshFailed = true;
  loggedOut = true;
  authGeneration += 1;
}

function isIdempotent(method: string): boolean {
  return method === "GET" || method === "HEAD";
}

function normalizeMethod(options: RequestInit): string {
  return (options.method ?? "GET").toUpperCase();
}

function withAccessToken(options: RequestInit, token: string): RequestInit {
  const headers = new Headers(options.headers ?? undefined);
  headers.set("Authorization", `Bearer ${token}`);
  return { ...options, headers };
}

async function executeAuthorizedRequest<T>(
  url: string,
  options: RequestInit,
  afterRefreshRetry: boolean
): Promise<T> {
  const token = accessToken;
  if (!token || loggedOut || refreshFailed) {
    throw authRecoveryError();
  }

  const { response, payload } = await sendRequest(url, withAccessToken(options, token));
  if (response.ok) {
    return payload as T;
  }

  if (response.status !== 401 || afterRefreshRetry) {
    throwResponseError(response, payload);
  }

  return enqueueForRefresh<T>(url, options, normalizeMethod(options));
}

function enqueueForRefresh<T>(url: string, options: RequestInit, method: string): Promise<T> {
  return new Promise<T>((resolve, reject) => {
    pendingRequests.push({
      method,
      retry: () => executeAuthorizedRequest<T>(url, options, true),
      resolve: (value: unknown) => resolve(value as T),
      reject: (reason: unknown) => reject(reason)
    });

    ensureQueueProcessor();
  });
}

function ensureQueueProcessor(): void {
  if (queueProcessor) {
    return;
  }

  queueProcessor = processPendingQueue().finally(() => {
    queueProcessor = null;
    if (pendingRequests.length > 0) {
      ensureQueueProcessor();
    }
  });
}

async function processPendingQueue(): Promise<void> {
  try {
    await ensureRefresh();
    if (loggedOut || refreshFailed || !accessToken) {
      rejectPendingRequests(authRecoveryError());
      return;
    }

    const queued = pendingRequests;
    pendingRequests = [];
    for (const pending of queued) {
      if (!isIdempotent(pending.method)) {
        pending.reject(authRecoveryError());
        continue;
      }
      pending.retry().then(pending.resolve).catch(pending.reject);
    }
  } catch {
    rejectPendingRequests(authRecoveryError());
  }
}

function rejectPendingRequests(error: Error): void {
  const queued = pendingRequests;
  pendingRequests = [];
  for (const pending of queued) {
    pending.reject(error);
  }
}

async function ensureRefresh(): Promise<AuthTokensResponse> {
  if (loggedOut || refreshFailed) {
    throw authRecoveryError();
  }
  if (refreshInFlight) {
    return refreshInFlight;
  }

  const generationAtStart = authGeneration;
  refreshInFlight = requestRefreshTokens()
    .then((tokens) => {
      if (generationAtStart !== authGeneration || loggedOut) {
        throw authRecoveryError();
      }
      applyAuthTokens(tokens);
      return tokens;
    })
    .catch((error: unknown) => {
      if (generationAtStart !== authGeneration || loggedOut) {
        throw authRecoveryError();
      }

      markRefreshFailure();
      rejectPendingRequests(authRecoveryError());

      if (error instanceof Error && error.message.includes(AUTH_RECOVERY_CODE)) {
        throw error;
      }
      throw authRecoveryError();
    })
    .finally(() => {
      refreshInFlight = null;
    });

  return refreshInFlight;
}

async function requestRefreshTokens(): Promise<AuthTokensResponse> {
  const csrf = readCookie(CSRF_COOKIE);
  return request<AuthTokensResponse>("/api/auth/refresh", {
    method: "POST",
    credentials: "include",
    headers: csrf ? { "X-CSRF-Token": csrf } : undefined
  });
}

function authRecoveryError(): Error {
  return new Error(`${AUTH_RECOVERY_CODE}: ${AUTH_RECOVERY_MESSAGE}`);
}

async function request<T>(url: string, options: RequestInit): Promise<T> {
  const { response, payload } = await sendRequest(url, options);
  if (!response.ok) {
    throwResponseError(response, payload);
  }
  return payload as T;
}

async function sendRequest(
  url: string,
  options: RequestInit
): Promise<{ response: Response; payload: unknown }> {
  const response = await fetch(url, options);
  const text = await response.text();
  const payload = parsePayload(response, text);
  return { response, payload };
}

function parsePayload(response: Response, text: string): unknown {
  if (!text) {
    return null;
  }

  const contentType = response.headers.get("Content-Type")?.toLowerCase() ?? "";
  const isJson = contentType.includes("application/json") || contentType.includes("+json");
  if (!isJson) {
    return null;
  }

  try {
    return JSON.parse(text);
  } catch {
    return null;
  }
}

function throwResponseError(response: Response, payload: unknown): never {
  const body = payload as { code?: string; message?: string } | null;
  const message = body?.message ?? response.statusText;
  const code = body?.code ? `${body.code}: ${message}` : message;
  throw new Error(code);
}
