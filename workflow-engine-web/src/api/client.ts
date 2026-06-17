const BASE = '/api';

export async function listInstances() {
  const res = await fetch(`${BASE}/instances`);
  if (!res.ok) throw new Error(`listInstances failed: ${res.status}`);
  return res.json();
}

export async function queryTasks(params: { instanceId: string }) {
  const url = new URL(`${BASE}/tasks`, window.location.origin);
  url.searchParams.set('instanceId', params.instanceId);
  const res = await fetch(url.toString());
  if (!res.ok) throw new Error(`queryTasks failed: ${res.status}`);
  return res.json();
}

export async function completeTask(taskId: string) {
  const res = await fetch(`${BASE}/tasks/${taskId}/complete`, { method: 'POST' });
  if (!res.ok) throw new Error(`completeTask failed: ${res.status}`);
  return res.json();
}

export async function getDefinitionGraph(definitionId: string) {
  const res = await fetch(`${BASE}/definitions/${encodeURIComponent(definitionId)}/graph`);
  if (!res.ok) throw new Error(`getDefinitionGraph failed: ${res.status}`);
  return res.json();
}

export async function resumeInstance(instanceId: string) {
  const res = await fetch(`${BASE}/instances/${instanceId}/resume`, { method: 'POST' });
  if (!res.ok) throw new Error(`resumeInstance failed: ${res.status}`);
  return res.json();
}

export async function terminateInstance(instanceId: string, reason?: string) {
  const res = await fetch(`${BASE}/instances/${instanceId}/terminate`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ reason: reason || 'terminated by user' })
  });
  if (!res.ok) throw new Error(`terminateInstance failed: ${res.status}`);
  return res.json();
}

export async function startInstance(defId: string, vars: Record<string, unknown>) {
  const res = await fetch(`${BASE}/instances`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ definitionId: defId, variables: vars })
  });
  if (!res.ok) throw new Error(`startInstance failed: ${res.status}`);
  return res.json();
}

export async function deployDefinition(yaml: string, positions?: Record<string, {x:number;y:number}>) {
  const res = await fetch(`${BASE}/definitions`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ yaml, positions })
  });
  if (!res.ok) throw new Error(`deployDefinition failed: ${res.status}`);
  return res.json();
}

// ── Drafts ─────────────────────────────
export async function listDrafts() {
  const res = await fetch(`${BASE}/drafts`);
  if (!res.ok) throw new Error(`listDrafts failed: ${res.status}`);
  return res.json();
}

export async function getDraft(id: string) {
  const res = await fetch(`${BASE}/drafts/${id}`);
  if (!res.ok) throw new Error(`getDraft failed: ${res.status}`);
  return res.json();
}

export async function createDraft(name: string) {
  const res = await fetch(`${BASE}/drafts`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ name, nodes: [], edges: [] })
  });
  if (!res.ok) throw new Error(`createDraft failed: ${res.status}`);
  return res.json();
}

export async function updateDraft(id: string, data: Record<string, unknown>) {
  const res = await fetch(`${BASE}/drafts/${id}`, {
    method: 'PUT', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(data)
  });
  if (!res.ok) throw new Error(`updateDraft failed: ${res.status}`);
  return res.json();
}

export async function deleteDraft(id: string) {
  const res = await fetch(`${BASE}/drafts/${id}`, { method: 'DELETE' });
  if (!res.ok) throw new Error(`deleteDraft failed: ${res.status}`);
}
