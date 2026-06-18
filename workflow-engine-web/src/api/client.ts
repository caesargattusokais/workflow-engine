const BASE = '/api';

function userId(): string {
  return localStorage.getItem('userId') || 'anonymous';
}

function authHeaders(extra?: Record<string,string>): Record<string,string> {
  return { 'X-User-Id': userId(), ...(extra || {}) };
}

async function apiGet(path: string) {
  const res = await fetch(`${BASE}${path}`, { headers: authHeaders() });
  if (!res.ok) throw new Error(`${path} failed: ${res.status}`);
  return res.json();
}

async function apiPost(path: string, body?: unknown) {
  const res = await fetch(`${BASE}${path}`, {
    method: 'POST',
    headers: authHeaders({ 'Content-Type': 'application/json' }),
    body: body ? JSON.stringify(body) : undefined
  });
  if (!res.ok) throw new Error(`${path} failed: ${res.status}`);
  return res.json();
}

async function apiPut(path: string, body: unknown) {
  const res = await fetch(`${BASE}${path}`, {
    method: 'PUT',
    headers: authHeaders({ 'Content-Type': 'application/json' }),
    body: JSON.stringify(body)
  });
  if (!res.ok) throw new Error(`${path} failed: ${res.status}`);
  return res.json();
}

async function apiDelete(path: string) {
  const res = await fetch(`${BASE}${path}`, { method: 'DELETE', headers: authHeaders() });
  if (!res.ok) throw new Error(`${path} failed: ${res.status}`);
}

export async function listInstances() { return apiGet('/instances'); }
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

export async function deployDefinition(yaml: string, positions?: Record<string, {x:number;y:number}>) {
  return apiPost('/definitions', { yaml, positions });
}
export async function getDefinitionGraph(definitionId: string) {
  return apiGet(`/definitions/${encodeURIComponent(definitionId)}/graph`);
}

export async function listDrafts() { return apiGet('/drafts'); }
export async function getDraft(id: string) { return apiGet(`/drafts/${id}`); }
export async function createDraft(name: string) {
  return apiPost('/drafts', { name, nodes: [], edges: [] });
}
export async function updateDraft(id: string, data: Record<string, unknown>) {
  return apiPut(`/drafts/${id}`, data);
}
export async function deleteDraft(id: string) { return apiDelete(`/drafts/${id}`); }


export { userId, authHeaders, apiGet, apiPost };
