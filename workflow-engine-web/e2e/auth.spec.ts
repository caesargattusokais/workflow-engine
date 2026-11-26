import { test, expect, ApiClient, TEST_USER, API_BASE } from './fixtures';

test.describe('Authentication / X-User-Id', () => {
  test('missing credentials returns 401', async () => {
    const res = await fetch(`${API_BASE}/drafts`);
    expect(res.status).toBe(401);
    const body = await res.json();
    // The auth filter now handles this before the legacy interceptor; it
    // deliberately returns a generic error so callers can't probe which
    // credential mechanism is missing.
    expect(body.error).toContain('Unauthorized');
  });

  test('blank X-User-Id header returns 401', async () => {
    const res = await fetch(`${API_BASE}/drafts`, { headers: { 'X-User-Id': '   ' } });
    expect(res.status).toBe(401);
  });

  test('valid X-User-Id header passes', async () => {
    const api = new ApiClient(TEST_USER);
    const result = await api.listDrafts();
    expect(result).toHaveProperty('items');
  });

  test('non-API paths skip interceptor', async () => {
    const res = await fetch('http://localhost:8080/swagger-ui.html');
    expect(res.status).toBe(200);
  });

  test('forged token with admin header is rejected (no header fallback)', async () => {
    const res = await fetch(`${API_BASE}/drafts`, {
      headers: { 'X-User-Id': 'admin', 'Authorization': 'Bearer forged.token.invalid' },
    });
    expect(res.status).toBe(401);
  });

  test('task list does not leak other tenants tasks', async ({ api }) => {
    // testuser starts an instance with a pending task; user-b must not see it.
    const def = await api.deploy(`id: tenant-task-${Date.now()}
name: Tenant Task
nodes:
  - id: start
    type: startEvent
  - id: review
    type: userTask
    name: Review
    assignee: ${TEST_USER}
  - id: end
    type: endEvent
transitions:
  - from: start
    to: review
  - from: review
    to: end`);
    const inst = await api.startInstance(def.id);
    await api.waitForTasks({ instanceId: inst.id, assignee: TEST_USER }, 8000);

    const userB = new ApiClient('user-b');
    const foreignTasks = await userB.listTasks({ instanceId: inst.id, status: 'PENDING' });
    expect(foreignTasks, 'user-b must not see testuser task/variables').toHaveLength(0);

    const ownTasks = await api.listTasks({ instanceId: inst.id, status: 'PENDING' });
    expect(ownTasks.length).toBeGreaterThan(0);
  });

  test('multi-tenant data isolation', async ({ api }) => {
    const draft = await api.createDraft(`user-a-draft-${Date.now()}`);
    const userB = new ApiClient('user-b');
    const bDrafts = await userB.listDrafts();
    const found = (bDrafts.items as any[]).some((d: any) => d.id === draft.id);
    expect(found).toBe(false);
  });
});
