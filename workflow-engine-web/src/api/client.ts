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
  const res = await fetch(`${BASE}/definitions/${definitionId}/graph`);
  if (!res.ok) throw new Error(`getDefinitionGraph failed: ${res.status}`);
  return res.json();
}

export async function resumeInstance(instanceId: string) {
  const res = await fetch(`${BASE}/instances/${instanceId}/resume`, { method: 'POST' });
  if (!res.ok) throw new Error(`resumeInstance failed: ${res.status}`);
  return res.json();
}

export async function terminateInstance(instanceId: string) {
  const res = await fetch(`${BASE}/instances/${instanceId}/terminate`, { method: 'POST' });
  if (!res.ok) throw new Error(`terminateInstance failed: ${res.status}`);
  return res.json();
}

export async function deployDefinition(yaml: string) {
  const res = await fetch(`${BASE}/definitions`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ yaml })
  });
  if (!res.ok) throw new Error(`deployDefinition failed: ${res.status}`);
  return res.json();
}
