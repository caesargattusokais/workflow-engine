const BASE = '/api';

function userId(): string {
  return localStorage.getItem('userId') || 'anonymous';
}

function authHeaders(extra?: Record<string,string>): Record<string,string> {
  return { 'X-User-Id': userId(), ...(extra || {}) };
}

/** Parse error response body for a meaningful message */
async function parseError(res: Response, path: string): Promise<Error> {
  const status = res.status;
  if (status === 401) {
    return new Error('未登录或登录已过期，请重新登录 / Not authenticated');
  }
  if (status === 404) {
    return new Error(`资源不存在: ${path} / Not found`);
  }
  try {
    const body = await res.json();
    const msg = body.error || body.message || body.detail || res.statusText;
    return new Error(`${path} failed (${status}): ${msg}`);
  } catch {
    return new Error(`${path} failed: ${status} ${res.statusText}`);
  }
}

async function apiGet(path: string) {
  const res = await fetch(`${BASE}${path}`, { headers: authHeaders(), credentials: 'same-origin' });
  if (!res.ok) { checkUnauthorized(res); throw await parseError(res, path); }
  return res.json();
}

async function apiPost(path: string, body?: unknown) {
  const res = await fetch(`${BASE}${path}`, {
    method: 'POST',
    headers: authHeaders({ 'Content-Type': 'application/json' }),
    credentials: 'same-origin',
    body: body ? JSON.stringify(body) : undefined
  });
  if (!res.ok) { checkUnauthorized(res); throw await parseError(res, path); }
  const text = await res.text();
  return text ? JSON.parse(text) : null;
}

async function apiPut(path: string, body: unknown, keepalive = false) {
  const res = await fetch(`${BASE}${path}`, {
    method: 'PUT',
    headers: authHeaders({ 'Content-Type': 'application/json' }),
    credentials: 'same-origin',
    // keepalive lets an unload-time flush (pagehide / tab hidden) survive
    // navigation instead of being cancelled mid-flight.
    keepalive,
    body: JSON.stringify(body)
  });
  if (!res.ok) { checkUnauthorized(res); throw await parseError(res, path); }
  const text = await res.text();
  return text ? JSON.parse(text) : null;
}

async function apiDelete(path: string) {
  const res = await fetch(`${BASE}${path}`, { method: 'DELETE', headers: authHeaders(), credentials: 'same-origin' });
  if (!res.ok) { checkUnauthorized(res); throw await parseError(res, path); }
}

export async function listInstances(page = 1, size = 10, definitionId?: string, status?: string) {
  let url = `/instances?page=${page}&size=${size}`;
  if (definitionId) url += `&definitionId=${encodeURIComponent(definitionId)}`;
  if (status) url += `&status=${encodeURIComponent(status)}`;
  return apiGet(url);
}
export async function startInstance(defId: string, vars: Record<string, unknown>) {
  return apiPost('/instances', { definitionId: defId, variables: vars });
}
export async function getInstance(id: string) { return apiGet(`/instances/${id}`); }
export async function resumeInstance(id: string) { return apiPost(`/instances/${id}/resume`); }
export async function terminateInstance(id: string, reason?: string) {
  return apiPost(`/instances/${id}/terminate`, { reason: reason || 'terminated by user' });
}
export async function deleteInstance(id: string) { return apiDelete(`/instances/${id}`); }

export async function queryTasks(params: { instanceId: string }) {
  const q = new URLSearchParams(params).toString();
  return apiGet(`/tasks?${q}`);
}
export async function completeTask(taskId: string, vars?: Record<string,unknown>, comment?: string) {
  return apiPost(`/tasks/${taskId}/complete`, { variables: vars || {}, comment: comment || '' });
}

export async function listDefinitions(page = 1, size = 10) { return apiGet(`/definitions?page=${page}&size=${size}`); }

export async function listOrgTree() { return apiGet('/org/tree'); }

export async function deployDefinition(yaml: string, positions?: Record<string, {x:number;y:number}>) {
  return apiPost('/definitions', { yaml, positions });
}
export async function getDefinitionGraph(definitionId: string, version?: number) {
  const q = version != null ? `?version=${version}` : '';
  return apiGet(`/definitions/${encodeURIComponent(definitionId)}/graph${q}`);
}

export async function listDrafts(page?: number, size?: number) { return apiGet(`/drafts?page=${page||1}&size=${size||10}`); }
export async function getDraft(id: string) { return apiGet(`/drafts/${id}`); }
export async function createDraft(name: string) {
  return apiPost('/drafts', { name, nodes: [], edges: [] });
}
export async function updateDraft(id: string, data: Record<string, unknown>, keepalive = false) {
  return apiPut(`/drafts/${id}`, data, keepalive);
}
export async function deleteDraft(id: string) { return apiDelete(`/drafts/${id}`); }

export async function copyDraft(id: string) { return apiPost(`/drafts/${id}/copy`); }
export async function importDraft(name: string, nodes: unknown[], edges: unknown[]) {
  return apiPost('/drafts/import', { name, nodes, edges });
}

export async function generateFlow(prompt: string) {
  return apiPost('/ai/generate', { prompt });
}

export interface AiConfigView {
  configured: boolean;
  source: 'database' | 'environment' | 'none';
  baseUrl?: string;
  model?: string;
  apiKeyMasked?: string | null;
  hasApiKey?: boolean;
}

export async function getAiConfig(): Promise<AiConfigView> { return apiGet('/ai/config'); }
export async function saveAiConfig(cfg: { baseUrl: string, apiKey: string, model: string }) {
  return apiPut('/ai/config', cfg) as Promise<AiConfigView>;
}
export async function resetAiConfig() { return apiGetAfterDelete('/ai/config') as Promise<AiConfigView>; }
export async function testAiConfig(cfg: { baseUrl?: string, apiKey?: string, model?: string }) {
  return apiPost('/ai/config/test', cfg) as Promise<{ ok: boolean, message: string, latencyMs?: number }>;
}

export interface AiGenerationView {
  systemPrompt: string;
  defaultPrompt: string;
  temperature: number;
  source: 'database' | 'environment' | 'default';
  isDefaultPrompt: boolean;
}
export async function getAiGeneration(): Promise<AiGenerationView> { return apiGet('/ai/generation'); }
export async function saveAiGeneration(cfg: { systemPrompt: string, temperature: number }) {
  return apiPut('/ai/generation', cfg) as Promise<AiGenerationView>;
}
export async function resetAiGeneration() { return apiGetAfterDelete('/ai/generation') as Promise<AiGenerationView>; }

/** DELETE that still parses the JSON response body (apiDelete returns void). */
async function apiGetAfterDelete(path: string) {
  const res = await fetch(`${BASE}${path}`, { method: 'DELETE', headers: authHeaders() });
  if (!res.ok) throw await parseError(res, path);
  const text = await res.text();
  return text ? JSON.parse(text) : null;
}

export async function fetchInstanceSummary() { return apiGet('/instances/summary'); }

export interface AiDiagnosis {
  summary: string;
  cause: string;
  suggestions: string[];
  severity: 'info' | 'warning' | 'error';
}
export async function diagnoseInstance(instanceId: string) {
  return apiPost('/ai/diagnose', { instanceId }) as Promise<AiDiagnosis>;
}

// ── Authentication ──────────────────────────────────────────────────────
// The session token lives in an HttpOnly cookie set by the server, so fetch
// sends it automatically (same-origin via the nginx proxy). The token is also
// returned in the body for clients that prefer Authorization: Bearer.
export interface LoginUser { username: string; displayName: string; email?: string | null; roles: string[] }
export interface LoginResult { token: string; user: LoginUser; expiresInSeconds: number }

export async function login(username: string, password: string): Promise<LoginResult> {
  const res = await fetch(`${BASE}/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    credentials: 'same-origin',
    body: JSON.stringify({ username, password })
  });
  if (!res.ok) {
    const msg = res.status === 401
      ? '用户名或密码错误 / Invalid username or password'
      : `登录失败 (${res.status}) / Login failed`;
    throw new Error(msg);
  }
  return res.json();
}

export async function register(username: string, password: string, displayName?: string, email?: string): Promise<LoginResult> {
  const res = await fetch(`${BASE}/auth/register`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    credentials: 'same-origin',
    body: JSON.stringify({ username, password, displayName, email })
  });
  if (!res.ok) {
    let msg = `注册失败 (${res.status}) / Registration failed`;
    try {
      const body = await res.json();
      if (body.message) msg = body.message;
      else if (body.error === 'Username already exists') msg = '用户名已被占用 / Username already exists';
      else if (body.error === 'Registration disabled') msg = '注册已关闭 / Registration disabled';
    } catch { /* keep default */ }
    throw new Error(msg);
  }
  return res.json();
}

export async function logout(): Promise<void> {
  await fetch(`${BASE}/auth/logout`, { method: 'POST', credentials: 'same-origin' }).catch(() => {});
}

export interface AuthStatus { enabled: boolean; headerCompat: boolean; registrationEnabled: boolean }
export async function getAuthStatus(): Promise<AuthStatus> {
  const res = await fetch(`${BASE}/auth/status`, { credentials: 'same-origin' });
  if (!res.ok) return { enabled: true, headerCompat: true, registrationEnabled: true };
  return res.json();
}

export interface AuthMe { authenticated: boolean; user?: LoginUser; source?: string }
export async function getAuthMe(): Promise<AuthMe> {
  const res = await fetch(`${BASE}/auth/me`, { credentials: 'same-origin' });
  if (!res.ok) return { authenticated: false };
  return res.json();
}

/** Global hook: apiGet/Post surface 401 here so the app can show the login screen. */
let unauthorizedHandler: (() => void) | null = null;
export function onUnauthorized(handler: (() => void) | null) { unauthorizedHandler = handler; }
function checkUnauthorized(res: Response) {
  if (res.status === 401 && unauthorizedHandler) unauthorizedHandler();
}

export { userId, authHeaders, apiGet, apiPost };
