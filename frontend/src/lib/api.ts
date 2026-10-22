import type { User } from "./types";
type ErrorBody = {
  code?: string;
  message?: string;
  violations?: { path: string; code: string }[];
};
export class ApiError extends Error {
  constructor(
    public status: number,
    public body: ErrorBody,
  ) {
    super(body.message ?? body.code ?? `HTTP ${status}`);
  }
}
export function errorMessage(error: unknown): string {
  if (!(error instanceof ApiError))
    return "No se pudo conectar. Revisa tu conexión y vuelve a intentar; los cambios no fueron confirmados.";
  if (error.status === 401)
    return "Tu sesión terminó o las credenciales no son correctas. Inicia sesión nuevamente.";
  if (error.status === 403)
    return "No tienes permiso o venció el token de seguridad. Recarga la página; verifica el responsable del área antes de volver a intentar.";
  if (error.message.includes("STALE_SESSION"))
    return "Otra persona modificó esta sesión. Recarga los datos antes de editar nuevamente; tu borrador no se guardó.";
  if (error.message.includes("STALE_PREVIEW"))
    return "El plan cambió desde la vista previa. Genera una nueva vista previa antes de confirmar.";
  if (error.message.includes("BLOCKED_DATE"))
    return "La fecha coincide con un bloqueo. Elige otra fecha explícitamente o pide revisar el bloqueo. No movimos la sesión.";
  if (error.status === 409)
    return "Conflicto: el recurso ya existe o tiene datos relacionados. Recarga y revisa las sesiones, temas o áreas vinculadas antes de eliminar.";
  if (error.status === 400)
    return (
      "Revisa los campos y el año escolar. " +
      (error.body.violations?.map((v) => `${v.path}: ${v.code}`).join("; ") ??
        "Los valores enviados no cumplen las reglas del plan.")
    );
  if (error.status === 404)
    return "El recurso ya no está disponible en tu escuela. Recarga los datos.";
  return "El servidor no pudo completar la operación. No asumas que se guardó: recarga antes de volver a intentar.";
}
/** Planning paths must always capture the selected context, including mutations. */
export function contextPath(path: string, contextId: string): string {
  if (!contextId) throw new Error("An academic context is required");
  return `${path}${path.includes("?") ? "&" : "?"}contextId=${encodeURIComponent(contextId)}`;
}
export class ApiClient {
  private csrf: { token: string; headerName: string } | null = null;
  constructor(private fetcher: typeof fetch = fetch) {}
  private async send(path: string, init: RequestInit = {}): Promise<Response> {
    const headers: Record<string, string> = {
      ...(init.headers as Record<string, string>),
    };
    if (this.csrf && path !== "/csrf" && !headers[this.csrf.headerName]) {
      headers[this.csrf.headerName] = this.csrf.token;
    }
    const hasHeaders = Object.keys(headers).length > 0;
    // Native browser fetch requires its platform receiver, not the ApiClient instance.
    const response = await this.fetcher.call(globalThis, `/api${path}`, {
      ...init,
      ...(hasHeaders ? { headers } : {}),
      credentials: "same-origin",
      cache: "no-store",
    });
    if (!response.ok) {
      let body: ErrorBody = {};
      try {
        body = await response.json();
      } catch {
        /* Servlet error pages are not JSON. */
      }
      throw new ApiError(response.status, body);
    }
    return response;
  }
  async get<T>(path: string, init?: RequestInit): Promise<T> {
    return (await this.send(path, init)).json();
  }
  getCsrf(): { token: string; headerName: string } | null {
    return this.csrf;
  }
  async ensureCsrf(): Promise<{ token: string; headerName: string }> {
    if (!this.csrf) await this.refreshCsrf();
    return this.csrf!;
  }
  async refreshCsrf() {
    this.csrf = await this.get("/csrf");
  }
  async mutate<T = unknown>(
    path: string,
    method: string,
    body?: unknown,
  ): Promise<T> {
    if (!this.csrf) await this.refreshCsrf();
    const response = await this.send(path, {
      method,
      headers: {
        "Content-Type": "application/json",
        [this.csrf!.headerName]: this.csrf!.token,
      },
      body: body === undefined ? undefined : JSON.stringify(body),
    });
    const text = await response.text();
    return (text ? JSON.parse(text) : undefined) as T;
  }
  async login(username: string, password: string): Promise<User> {
    await this.refreshCsrf();
    await this.send("/login", {
      method: "POST",
      headers: {
        "Content-Type": "application/x-www-form-urlencoded",
        [this.csrf!.headerName]: this.csrf!.token,
      },
      body: new URLSearchParams({ username, password }).toString(),
    });
    this.csrf = null;
    await this.refreshCsrf();
    return this.get("/me");
  }
  async logout() {
    await this.mutate("/logout", "POST");
    this.csrf = null;
    await this.refreshCsrf();
  }
  async download(
    path: string,
    name: string,
    isCurrent: () => boolean = () => true,
  ) {
    const blob = await (await this.send(path)).blob();
    if (!isCurrent()) return;
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url;
    link.download = name;
    link.click();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
  }
}
export const api = new ApiClient();
