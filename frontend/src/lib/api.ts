import type {
  ApiEnvelope,
  Application,
  ApplicationStatus,
  AuditLog,
  AuthResponse,
  Category,
  Credential,
  CredentialStatus,
  Deliverable,
  Engagement,
  EngagementStatus,
  PageResponse,
  Payment,
  PaymentConfig,
  Refund,
  Review,
  Settlement,
  SettlementBatch,
  SettlementSummary,
  TicketDetail,
  TicketKind,
  TicketStatus,
  TicketSummary,
  UserResponse,
  WorkerProfile,
} from "../types";

const API_BASE = (import.meta.env.VITE_API_BASE_URL || "/api").replace(/\/$/, "");

export const demoFallbackEnabled = import.meta.env.VITE_ENABLE_DEMO_FALLBACK !== "false";

type QueryValue = string | number | boolean | null | undefined;

export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly field?: string;
  readonly details?: unknown;

  constructor(message: string, status = 0, code = "NETWORK_ERROR", field?: string, details?: unknown) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.code = code;
    this.field = field;
    this.details = details;
  }
}

const storageKeys = {
  access: "marketplace.accessToken",
  refresh: "marketplace.refreshToken",
  auth: "marketplace.auth",
};

export const tokenStore = {
  access: () => localStorage.getItem(storageKeys.access),
  refresh: () => localStorage.getItem(storageKeys.refresh),
  save(auth: AuthResponse) {
    localStorage.setItem(storageKeys.access, auth.accessToken);
    localStorage.setItem(storageKeys.refresh, auth.refreshToken);
    localStorage.setItem(storageKeys.auth, JSON.stringify(auth));
  },
  auth(): AuthResponse | null {
    try {
      const raw = localStorage.getItem(storageKeys.auth);
      return raw ? (JSON.parse(raw) as AuthResponse) : null;
    } catch {
      return null;
    }
  },
  clear() {
    Object.values(storageKeys).forEach((key) => localStorage.removeItem(key));
  },
};

function query(values: object) {
  const params = new URLSearchParams();
  Object.entries(values).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== "") params.set(key, String(value));
  });
  const output = params.toString();
  return output ? `?${output}` : "";
}

let refreshInFlight: Promise<boolean> | null = null;

async function refreshAccessToken(): Promise<boolean> {
  const refreshToken = tokenStore.refresh();
  if (!refreshToken) return false;

  if (!refreshInFlight) {
    refreshInFlight = fetch(`${API_BASE}/auth/refresh`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ refreshToken }),
    })
      .then(async (response) => {
        if (!response.ok) return false;
        const envelope = (await response.json()) as ApiEnvelope<AuthResponse>;
        if (!envelope.data) return false;
        tokenStore.save(envelope.data);
        return true;
      })
      .catch(() => false)
      .finally(() => {
        refreshInFlight = null;
      });
  }
  return refreshInFlight;
}

async function request<T>(path: string, init: RequestInit = {}, retry = true): Promise<T> {
  const headers = new Headers(init.headers);
  const token = tokenStore.access();
  if (token) headers.set("Authorization", `Bearer ${token}`);
  if (init.body && !(init.body instanceof FormData) && !headers.has("Content-Type")) {
    headers.set("Content-Type", "application/json");
  }

  let response: Response;
  try {
    response = await fetch(`${API_BASE}${path}`, { ...init, headers });
  } catch (error) {
    throw new ApiError(error instanceof Error ? error.message : "Unable to reach the API");
  }

  if (response.status === 401 && retry && tokenStore.refresh()) {
    const refreshed = await refreshAccessToken();
    if (refreshed) return request<T>(path, init, false);
    tokenStore.clear();
    window.dispatchEvent(new Event("marketplace:session-expired"));
  }

  const contentType = response.headers.get("content-type") || "";
  const payload = contentType.includes("application/json")
    ? ((await response.json()) as ApiEnvelope<T>)
    : null;

  if (!response.ok || payload?.success === false) {
    throw new ApiError(
      payload?.message || (response.status === 0 ? "Unable to reach the API" : response.statusText),
      response.status,
      payload?.error?.code || `HTTP_${response.status}`,
      payload?.error?.field,
      payload?.error?.details,
    );
  }

  if (!payload || payload.data === undefined) return undefined as T;
  return payload.data;
}

const json = (body?: unknown): RequestInit => ({
  method: "POST",
  body: body === undefined ? undefined : JSON.stringify(body),
});

export interface TicketSearch {
  kind?: TicketKind;
  categoryId?: number;
  q?: string;
  minPrice?: string;
  maxPrice?: string;
  city?: string;
  locationMode?: string;
  pricingMode?: string;
  serviceFrom?: string;
  serviceTo?: string;
  sort?: string;
  page?: number;
  size?: number;
}

export const api = {
  auth: {
    login: (body: { email: string; password: string }) => request<AuthResponse>("/auth/login", json(body)),
    register: (body: { name: string; email: string; password: string; phone?: string }) =>
      request<AuthResponse>("/auth/register", json(body)),
    me: () => request<UserResponse>("/auth/me"),
    logout: () => request<void>("/auth/logout", json()),
  },

  categories: {
    list: () => request<Category[]>("/categories"),
    get: (code: string) => request<Category>(`/categories/${encodeURIComponent(code)}`),
  },

  tickets: {
    list: (search: TicketSearch = {}) => request<PageResponse<TicketSummary>>(`/tickets${query(search)}`),
    get: (id: number | string) => request<TicketDetail>(`/tickets/${id}`),
    mine: (search: { kind?: TicketKind; status?: TicketStatus; page?: number; size?: number; sort?: string } = {}) =>
      request<PageResponse<TicketSummary>>(`/me/tickets${query(search)}`),
    mineOne: (id: number | string) => request<TicketDetail>(`/me/tickets/${id}`),
    create: (body: Record<string, unknown>) => request<TicketDetail>("/tickets", json(body)),
    update: (id: number | string, body: Record<string, unknown>) =>
      request<TicketDetail>(`/tickets/${id}`, { method: "PUT", body: JSON.stringify(body) }),
    publish: (id: number | string) => request<TicketDetail>(`/tickets/${id}/publish`, json()),
    close: (id: number | string) => request<TicketDetail>(`/tickets/${id}/close`, json()),
    uploadImage: (id: number | string, file: File, caption?: string) => {
      const body = new FormData();
      body.append("file", file);
      if (caption) body.append("caption", caption);
      return request(`/tickets/${id}/images`, { method: "POST", body });
    },
    deleteImage: (ticketId: number | string, imageId: number | string) =>
      request(`/tickets/${ticketId}/images/${imageId}`, { method: "DELETE" }),
    reorderImages: (ticketId: number | string, imageIds: number[]) =>
      request(`/tickets/${ticketId}/images/order`, { method: "PUT", body: JSON.stringify(imageIds) }),
  },

  workers: {
    list: (page = 0, size = 20, verified = false) =>
      request<PageResponse<WorkerProfile>>(`/workers${verified ? "/verified" : ""}${query({ page, size })}`),
    get: (id: number | string) => request<WorkerProfile>(`/workers/${id}`),
    reviews: (id: number | string, page = 0, size = 20) =>
      request<PageResponse<Review>>(`/workers/${id}/reviews${query({ page, size })}`),
    saveProfile: (body: Record<string, unknown>, exists = true) =>
      request<WorkerProfile>("/workers/profile", {
        method: exists ? "PUT" : "POST",
        body: JSON.stringify(body),
      }),
  },

  applications: {
    apply: (ticketId: number | string, body: Record<string, unknown>) =>
      request<Application>(`/tickets/${ticketId}/applications`, json(body)),
    forTicket: (ticketId: number | string, status?: ApplicationStatus, page = 0, size = 20) =>
      request<PageResponse<Application>>(`/tickets/${ticketId}/applications${query({ status, page, size })}`),
    mine: (status?: ApplicationStatus, page = 0, size = 100) =>
      request<PageResponse<Application>>(`/me/applications${query({ status, page, size })}`),
    accept: (id: number | string) => request<Application>(`/applications/${id}/accept`, json()),
    reject: (id: number | string) => request<Application>(`/applications/${id}/reject`, json()),
    withdraw: (id: number | string) => request<Application>(`/applications/${id}/withdraw`, json()),
  },

  engagements: {
    list: (role: "client" | "worker", status?: EngagementStatus, page = 0, size = 20) =>
      request<PageResponse<Engagement>>(`/engagements${query({ role, status, page, size })}`),
    get: (id: number | string) => request<Engagement>(`/engagements/${id}`),
    pay: (id: number | string, requestId: string) => request<Payment>(`/engagements/${id}/pay`, json({ requestId })),
    start: (id: number | string) => request<Engagement>(`/engagements/${id}/start`, json()),
    deliver: (id: number | string) => request<Engagement>(`/engagements/${id}/deliver`, json()),
    approve: (id: number | string) => request<Engagement>(`/engagements/${id}/approve`, json()),
    dispute: (id: number | string, reason: string) => request<Engagement>(`/engagements/${id}/dispute`, json({ reason })),
    cancel: (id: number | string, reason?: string) => request<Engagement>(`/engagements/${id}/cancel`, json(reason ? { reason } : undefined)),
    deliverables: (id: number | string) => request<Deliverable[]>(`/engagements/${id}/deliverables`),
    addDeliverable: (id: number | string, file: File, note?: string) => {
      const body = new FormData();
      body.append("file", file);
      if (note) body.append("note", note);
      return request<Deliverable>(`/engagements/${id}/deliverables`, { method: "POST", body });
    },
    deleteDeliverable: (engagementId: number | string, deliverableId: number | string) =>
      request(`/engagements/${engagementId}/deliverables/${deliverableId}`, { method: "DELETE" }),
    review: (id: number | string, body: { rating: number; comment?: string }) =>
      request<Review>(`/engagements/${id}/reviews`, json(body)),
  },

  payments: {
    config: () => request<PaymentConfig>("/payments/config"),
  },

  credentials: {
    mine: (page = 0, size = 20) => request<PageResponse<Credential>>(`/me/credentials${query({ page, size })}`),
    submit: (body: Record<string, unknown>) => request<Credential>("/me/credentials", json(body)),
    uploadDocument: (id: number | string, file: File) => {
      const body = new FormData();
      body.append("file", file);
      return request<Credential>(`/me/credentials/${id}/document`, { method: "POST", body });
    },
  },

  refunds: {
    list: (status?: string, page = 0, size = 20) => request<PageResponse<Refund>>(`/refunds${query({ status, page, size })}`),
    get: (id: number | string) => request<Refund>(`/refunds/${id}`),
  },

  settlements: {
    list: (status?: string, page = 0, size = 20) => request<PageResponse<Settlement>>(`/settlements${query({ status, page, size })}`),
    summary: () => request<SettlementSummary>("/settlements/summary"),
    get: (id: number | string) => request<Settlement>(`/settlements/${id}`),
  },

  reviews: {
    forUser: (id: number | string, page = 0, size = 20) =>
      request<PageResponse<Review>>(`/users/${id}/reviews${query({ page, size })}`),
  },

  admin: {
    credentials: (status?: CredentialStatus, page = 0, size = 20) =>
      request<PageResponse<Credential>>(`/admin/credentials${query({ status, page, size })}`),
    verifyCredential: (id: number | string) => request<Credential>(`/admin/credentials/${id}/verify`, json()),
    rejectCredential: (id: number | string, reason: string) =>
      request<Credential>(`/admin/credentials/${id}/reject`, json({ reason })),
    resolveEngagement: (id: number | string, resolution: "COMPLETED" | "REFUNDED", reason?: string) =>
      request<Engagement>(`/admin/engagements/${id}/resolve`, json({ resolution, reason })),
    createCategory: (body: Record<string, unknown>) => request<Category>("/admin/categories", json(body)),
    updateCategory: (id: number | string, body: Record<string, unknown>) =>
      request<Category>(`/admin/categories/${id}`, { method: "PUT", body: JSON.stringify(body) }),
    setCategoryActive: (id: number | string, active: boolean) =>
      request<Category>(`/admin/categories/${id}/${active ? "activate" : "deactivate"}`, json()),
    settlements: (status?: string, page = 0, size = 20) =>
      request<PageResponse<Settlement>>(`/settlements${query({ status, page, size })}`),
    refunds: (status?: string, page = 0, size = 20) =>
      request<PageResponse<Refund>>(`/refunds${query({ status, page, size })}`),
    runBatch: () => request(`/admin/settlements/batch`, json()),
    batches: (page = 0, size = 20) => request<PageResponse<SettlementBatch>>(`/admin/settlements/batches${query({ page, size })}`),
    audit: (entityType: string, entityId: number | string, page = 0, size = 20) =>
      request<PageResponse<AuditLog>>(`/audit-logs${query({ entityType, entityId, page, size })}`),
  },
};

export function assetUrl(value: string | null | undefined) {
  if (!value) return "";
  if (/^https?:\/\//.test(value) || value.startsWith("blob:") || value.startsWith("data:") || value.startsWith("/images/")) {
    return value;
  }
  return value.startsWith("/api/") ? value : `${API_BASE}${value.startsWith("/") ? "" : "/"}${value}`;
}

export async function authenticatedBlobUrl(value: string): Promise<string> {
  const headers = new Headers();
  const token = tokenStore.access();
  if (token) headers.set("Authorization", `Bearer ${token}`);
  const response = await fetch(assetUrl(value), { headers });
  if (!response.ok) throw new ApiError("Unable to load private image", response.status, `HTTP_${response.status}`);
  return URL.createObjectURL(await response.blob());
}
