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

const CSRF_COOKIE = "csrf_token";

export async function login(email: string, password: string): Promise<AuthTokensResponse> {
  return request<AuthTokensResponse>("/api/auth/login", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    credentials: "include",
    body: JSON.stringify({ email, password })
  });
}

export async function refresh(): Promise<AuthTokensResponse> {
  const csrf = readCookie(CSRF_COOKIE);
  return request<AuthTokensResponse>("/api/auth/refresh", {
    method: "POST",
    credentials: "include",
    headers: csrf ? { "X-CSRF-Token": csrf } : undefined
  });
}

export async function logout(accessToken: string): Promise<void> {
  const csrf = readCookie(CSRF_COOKIE);
  await request<void>("/api/auth/logout", {
    method: "POST",
    credentials: "include",
    headers: {
      Authorization: `Bearer ${accessToken}`,
      ...(csrf ? { "X-CSRF-Token": csrf } : {})
    }
  });
}

export async function whoami(accessToken: string): Promise<AuthUser> {
  return request<AuthUser>("/api/me", {
    method: "GET",
    headers: { Authorization: `Bearer ${accessToken}` }
  });
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

async function request<T>(url: string, options: RequestInit): Promise<T> {
  const response = await fetch(url, options);
  const text = await response.text();
  const payload = text ? JSON.parse(text) : null;
  if (!response.ok) {
    const message = payload?.message ?? response.statusText;
    const code = payload?.code ? `${payload.code}: ${message}` : message;
    throw new Error(code);
  }
  return payload as T;
}
