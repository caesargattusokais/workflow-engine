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
  const res = await fetch(`${BASE}${path}`, { headers: authHeaders() });
  if (!res.ok) throw await parseError(res, path);
  return res.json();
}

async function apiPost(path: string, body?: unknown) {
  const res = await fetch(`${BASE}${path}`, {
    method: 'POST',
    headers: authHeaders({ 'Content-Type': 'application/json' }),
    body: body ? JSON.stringify(body) : undefined
  });
  if (!res.ok) throw await parseError(res, path);
  const text = await res.text();
  return text ? JSON.parse(text) : null;
}

async function apiPut(path: string, body: unknown) {
  const res = await fetch(`${BASE}${path}`, {
    method: 'PUT',
    headers: authHeaders({ 'Content-Type': 'application/json' }),
    body: JSON.stringify(body)
  });
  if (!res.ok) throw await parseError(res, path);
  const text = await res.text();
  return text ? JSON.parse(text) : null;
}

async function apiDelete(path: string) {
  const res = await fetch(`${BASE}${path}`, { method: 'DELETE', headers: authHeaders() });
  if (!res.ok) throw await parseError(res, path);
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
export async function updateDraft(id: string, data: Record<string, unknown>) {
  return apiPut(`/drafts/${id}`, data);
}
export async function deleteDraft(id: string) { return apiDelete(`/drafts/${id}`); }

export async function copyDraft(id: string) { return apiPost(`/drafts/${id}/copy`); }
export async function importDraft(name: string, nodes: unknown[], edges: unknown[]) {
  return apiPost('/drafts/import', { name, nodes, edges });
}

export async function fetchInstanceSummary() { return apiGet('/instances/summary'); }

export { userId, authHeaders, apiGet, apiPost };
