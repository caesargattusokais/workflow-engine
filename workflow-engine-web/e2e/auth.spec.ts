import { test, expect, ApiClient, TEST_USER, API_BASE } from './fixtures';

test.describe('Authentication / X-User-Id', () => {
  test('missing X-User-Id header returns 401', async () => {
    const res = await fetch(`${API_BASE}/drafts`);
    expect(res.status).toBe(401);
    const body = await res.json();
    expect(body.error).toContain('Missing X-User-Id');
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

  test('multi-tenant data isolation', async ({ api }) => {
    const draft = await api.createDraft('user-a-draft');
    const userB = new ApiClient('user-b');
    const bDrafts = await userB.listDrafts();
    const found = (bDrafts.items as any[]).some((d: any) => d.id === draft.id);
    expect(found).toBe(false);
  });
});
