import { test as base, expect } from '@playwright/test';
import * as fs from 'fs';
import * as path from 'path';

// ── Test User ──
export const TEST_USER = 'testuser';
export const API_BASE = 'http://localhost:8080/api';

// ── API Client ──
export class ApiClient {
  private headers: Record<string, string>;

  constructor(userId = TEST_USER) {
    this.headers = { 'X-User-Id': userId, 'Content-Type': 'application/json' };
  }

  private async request(method: string, url: string, body?: unknown) {
    const res = await fetch(`${API_BASE}${url}`, {
      method,
      headers: this.headers,
      body: body ? JSON.stringify(body) : undefined,
    });
    if (!res.ok) {
      const text = await res.text().catch(() => '');
      throw new Error(`${method} ${url} failed (${res.status}): ${text}`);
    }
    const text = await res.text();
    return text ? JSON.parse(text) : null;
  }

  // Definitions
  deploy(yaml: string, positions?: Record<string, { x: number; y: number }>) {
    return this.request('POST', '/definitions', { yaml, positions });
  }
  listDefinitions(page = 1, size = 10) {
    return this.request('GET', `/definitions?page=${page}&size=${size}`);
  }
  getDefinition(id: string) { return this.request('GET', `/definitions/${id}`); }
  getDefinitionGraph(id: string, version?: number) {
    const q = version != null ? `?version=${version}` : '';
    return this.request('GET', `/definitions/${encodeURIComponent(id)}/graph${q}`);
  }
  deleteDefinition(id: string) { return this.request('DELETE', `/definitions/${id}`); }

  // Instances
  startInstance(defId: string, variables?: Record<string, unknown>) {
    return this.request('POST', '/instances', { definitionId: defId, variables: variables || {} });
  }
  listInstances(page = 1, size = 10, defId?: string, status?: string) {
    let url = `/instances?page=${page}&size=${size}`;
    if (defId) url += `&definitionId=${encodeURIComponent(defId)}`;
    if (status) url += `&status=${encodeURIComponent(status)}`;
    return this.request('GET', url);
  }
  getInstance(id: string) { return this.request('GET', `/instances/${id}`); }
  terminateInstance(id: string, reason?: string) {
    return this.request('POST', `/instances/${id}/terminate`, { reason: reason || 'test terminate' });
  }
  resumeInstance(id: string) { return this.request('POST', `/instances/${id}/resume`); }
  deleteInstance(id: string) { return this.request('DELETE', `/instances/${id}`); }
  getInstanceHistory(id: string) { return this.request('GET', `/instances/${id}/history`); }
  getInstanceSummary() { return this.request('GET', '/instances/summary'); }
  recover() { return this.request('POST', '/instances/recover'); }

  // Tasks
  listTasks(params?: { assignee?: string; candidateGroup?: string; instanceId?: string; status?: string }) {
    const q = new URLSearchParams();
    if (params?.assignee) q.set('assignee', params.assignee);
    if (params?.candidateGroup) q.set('candidateGroup', params.candidateGroup);
    if (params?.instanceId) q.set('instanceId', params.instanceId);
    if (params?.status) q.set('status', params.status);
    return this.request('GET', `/tasks?${q.toString()}`);
  }
  completeTask(id: string, variables?: Record<string, unknown>, comment?: string) {
    return this.request('POST', `/tasks/${id}/complete`, { variables: variables || {}, comment: comment || '' });
  }
  rejectTask(id: string, comment?: string) {
    return this.request('POST', `/tasks/${id}/reject`, { comment: comment || '' });
  }
  delegateTask(id: string, newAssignee: string) {
    return this.request('POST', `/tasks/${id}/delegate`, { newAssignee });
  }

  // Drafts
  listDrafts(page = 1, size = 10) { return this.request('GET', `/drafts?page=${page}&size=${size}`); }
  getDraft(id: string) { return this.request('GET', `/drafts/${id}`); }
  createDraft(name: string) { return this.request('POST', '/drafts', { name, nodes: [], edges: [] }); }
  updateDraft(id: string, data: Record<string, unknown>) { return this.request('PUT', `/drafts/${id}`, data); }
  deleteDraft(id: string) { return this.request('DELETE', `/drafts/${id}`); }
  copyDraft(id: string) { return this.request('POST', `/drafts/${id}/copy`); }
  importDraft(name: string, nodes: unknown[], edges: unknown[]) {
    return this.request('POST', '/drafts/import', { name, nodes, edges });
  }

  // Org
  getOrgTree() { return this.request('GET', '/org/tree'); }
  searchUsers(q: string) { return this.request('GET', `/org/users?q=${encodeURIComponent(q)}`); }
  listGroups() { return this.request('GET', '/org/groups'); }

  // Dashboard
  getDashboardStats(defId?: string) {
    const q = defId ? `?definitionId=${encodeURIComponent(defId)}` : '';
    return this.request('GET', `/dashboard/stats${q}`);
  }
  getTimeline(instanceId: string) { return this.request('GET', `/dashboard/timeline/${instanceId}`); }
}

// ── YAML Workflow Fixtures ──
export const workflows = {
  simpleLinear: `id: simple-linear
name: Simple Linear Flow
version: 1
nodes:
  - id: start
    type: startEvent
  - id: task1
    type: userTask
    name: Review
    assignee: reviewer
  - id: end
    type: endEvent
transitions:
  - from: start
    to: task1
  - from: task1
    to: end`,

  exclusiveGateway: `id: exclusive-gw
name: Exclusive Gateway Test
version: 1
nodes:
  - id: start
    type: startEvent
  - id: approve
    type: userTask
    name: Submit
    assignee: applicant
  - id: gw
    type: exclusiveGateway
  - id: manager
    type: userTask
    name: Manager Approve
    assignee: manager
  - id: director
    type: userTask
    name: Director Approve
    assignee: director
  - id: end
    type: endEvent
transitions:
  - from: start
    to: approve
  - from: approve
    to: gw
  - from: gw
    to: manager
    type: conditional
    expr: "days <= 3"
  - from: gw
    to: director
    type: conditional
    expr: "days > 3"
  - from: manager
    to: end
  - from: director
    to: end`,

  parallelGateway: `id: parallel-gw
name: Parallel Gateway Test
version: 1
nodes:
  - id: start
    type: startEvent
  - id: fork
    type: parallelGateway
  - id: taskA
    type: userTask
    name: Task A
    assignee: userA
  - id: taskB
    type: userTask
    name: Task B
    assignee: userB
  - id: join
    type: parallelGateway
  - id: end
    type: endEvent
transitions:
  - from: start
    to: fork
  - from: fork
    to: taskA
  - from: fork
    to: taskB
  - from: taskA
    to: join
  - from: taskB
    to: join
  - from: join
    to: end`,

  inclusiveGateway: `id: inclusive-gw
name: Inclusive Gateway Test
version: 1
nodes:
  - id: start
    type: startEvent
  - id: gw
    type: inclusiveGateway
  - id: taskA
    type: userTask
    name: Path A
    assignee: userA
  - id: taskB
    type: userTask
    name: Path B
    assignee: userB
  - id: end
    type: endEvent
transitions:
  - from: start
    to: gw
  - from: gw
    to: taskA
    type: conditional
    expr: "flagA == true"
  - from: gw
    to: taskB
    type: conditional
    expr: "flagB == true"
  - from: taskA
    to: end
  - from: taskB
    to: end`,

  timerFlow: `id: timer-flow
name: Timer Test
version: 1
nodes:
  - id: start
    type: startEvent
  - id: wait
    type: timer
    name: Wait 2s
    duration: "PT2S"
  - id: task
    type: userTask
    name: After Timer
    assignee: user1
  - id: end
    type: endEvent
transitions:
  - from: start
    to: wait
  - from: wait
    to: task
  - from: task
    to: end`,

  leaveApproval: `id: leave-approval
name: Leave Approval
version: 1
nodes:
  - id: start
    type: startEvent
  - id: apply
    type: userTask
    name: Submit Leave
    assignee: "\${applicant}"
  - id: gw
    type: exclusiveGateway
  - id: manager-approve
    type: userTask
    name: Manager Approve
    candidateGroups: ["manager"]
  - id: dept-approve
    type: userTask
    name: Dept Approve
    assignee: dept-head
  - id: end
    type: endEvent
transitions:
  - from: start
    to: apply
  - from: apply
    to: gw
  - from: gw
    to: manager-approve
    type: conditional
    expr: "days > 3"
  - from: gw
    to: dept-approve
    type: conditional
    expr: "days <= 3"
  - from: manager-approve
    to: end
  - from: dept-approve
    to: end`,
};

// ── Extended test fixture ──
export const test = base.extend<{ api: ApiClient }>({
  api: async ({}, use) => {
    await use(new ApiClient());
  },
});

export { expect };
