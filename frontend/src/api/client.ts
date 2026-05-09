type EnvConfig = {
  VITE_API_BASE_URL?: string;
};

type ApiClientOptions = {
  baseUrl?: string;
  fetchImpl?: typeof fetch;
};

type ApiResponse = {
  response: Response;
  payload: unknown;
};

const defaultEnv =
  (import.meta as unknown as { env?: EnvConfig }).env ?? ({} as EnvConfig);

export function resolveApiBaseUrl(env: EnvConfig = defaultEnv): string {
  const raw = env?.VITE_API_BASE_URL ?? "";
  return raw.trim().replace(/\/+$/, "");
}

export function buildApiUrl(path: string, baseUrl: string): string {
  const trimmedBase = baseUrl.trim().replace(/\/+$/, "");
  if (!trimmedBase) {
    return path;
  }
  const normalizedPath = path.startsWith("/") ? path : `/${path}`;
  return `${trimmedBase}${normalizedPath}`;
}

export function createApiClient(options: ApiClientOptions = {}) {
  const baseUrl = options.baseUrl ?? resolveApiBaseUrl();
  const fetchImpl = options.fetchImpl ?? fetch;

  const sendRequest = async (path: string, options: RequestInit): Promise<ApiResponse> => {
    const response = await fetchImpl(buildApiUrl(path, baseUrl), options);
    const text = await response.text();
    const payload = parsePayload(response, text);
    return { response, payload };
  };

  const request = async <T>(path: string, options: RequestInit): Promise<T> => {
    const { response, payload } = await sendRequest(path, options);
    if (!response.ok) {
      throwResponseError(response, payload);
    }
    return payload as T;
  };

  return { request, sendRequest };
}

const defaultClient = createApiClient();

export const request = defaultClient.request;
export const sendRequest = defaultClient.sendRequest;

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

export function throwResponseError(response: Response, payload: unknown): never {
  const body = payload as { code?: string; message?: string } | null;
  const message = body?.message ?? response.statusText;
  const code = body?.code ? `${body.code}: ${message}` : message;
  throw new Error(code);
}
