import { test, expect, request as pwRequest, type APIRequestContext } from '@playwright/test';

/**
 * Group administration API. The E2E server runs the memory profile with
 * bootstrap users admin (role ADMIN, groups alpha/beta) and testuser.
 * Tokens are used here instead of X-User-Id headers because group admin rights
 * are read from the store, which the header-compat principal does not carry.
 */
const API = (process.env.E2E_API_BASE || 'http://localhost:8080/api').replace(/\/api$/, '');

async function api(token?: string): Promise<APIRequestContext> {
  return pwRequest.newContext({
    baseURL: API,
    extraHTTPHeaders: token ? { Authorization: `Bearer ${token}` } : {},
  });
}

async function login(ctx: APIRequestContext, username: string, password = 'pass1234'): Promise<string> {
  const res = await ctx.post('/api/auth/login', { data: { username, password } });
  if (!res.ok()) throw new Error(`login ${username} failed (${res.status()}): ${await res.text()}`);
  return (await res.json()).token;
}

async function register(ctx: APIRequestContext, username: string) {
  const res = await ctx.post('/api/auth/register', {
    data: { username, password: 'pass1234', displayName: username },
  });
  if (!res.ok()) throw new Error(`register ${username} failed (${res.status()}): ${await res.text()}`);
}

test('any signed-in user can create a group; anonymous cannot', async () => {
  const anon = await api();
  const id = `g-open-${Date.now().toString(36)}`;

  // Must run before login: the login response sets a cookie on this context.
  expect([401, 403]).toContain((await anon.post('/api/groups', { data: { id, name: 'Nope' } })).status());

  const user = await api(await login(anon, 'testuser', 'test123'));
  const created = await user.post('/api/groups', { data: { id, name: 'Mine', description: 'e2e' } });
  expect(created.status()).toBe(201);
  expect((await created.json()).id).toBe(id);

  // The creator becomes the group's first admin and can manage members.
  const mine: any[] = await (await user.get('/api/groups')).json();
  expect(mine.find(v => v.group.id === id)?.groupAdmin).toBe(true);
  const duplicate = await user.post('/api/groups', { data: { id, name: 'Dup' } });
  expect(duplicate.status()).toBe(409);

  // Only the creator or a global admin may delete; an unrelated user may not.
  const otherName = `oth${Date.now().toString(36)}`;
  await register(anon, otherName);
  const other = await api(await login(anon, otherName));
  expect((await other.delete(`/api/groups/${id}`)).status()).toBe(403);

  // The creator must leave their own group before deleting it.
  expect((await user.post(`/api/groups/${id}/leave`)).status()).toBe(200);
  expect((await user.delete(`/api/groups/${id}`)).status()).toBe(200);
});

test('group admin manages members; members can only leave', async () => {
  const anon = await api();
  const adminToken = await login(anon, 'admin', 'admin123');
  const admin = await api(adminToken);
  const id = `g-life-${Date.now().toString(36)}`;
  const gm = `gm${Date.now().toString(36)}`;
  const mem = `mem${Date.now().toString(36)}`;
  const outsider = `out${Date.now().toString(36)}`;
  for (const u of [gm, mem, outsider]) await register(anon, u);

  expect((await admin.post('/api/groups', { data: { id, name: 'Lifecycle' } })).status()).toBe(201);

  // A populated group cannot be deleted.
  expect((await admin.post(`/api/groups/${id}/members`, { data: { username: gm } })).status()).toBe(200);
  expect((await admin.delete(`/api/groups/${id}`)).status()).toBe(409);

  // Appoint a group admin — this also joins the group.
  expect((await admin.put(`/api/groups/${id}/admin`, { data: { username: gm, admin: true } })).status()).toBe(200);
  const gmToken = await login(anon, gm);
  const gmApi = await api(gmToken);
  const gmView = await (await gmApi.get('/api/groups')).json();
  expect(gmView.find((v: any) => v.group.id === id)?.groupAdmin).toBe(true);

  // Group admin can add members but cannot appoint another group admin.
  expect((await gmApi.post(`/api/groups/${id}/members`, { data: { username: mem } })).status()).toBe(200);
  expect((await gmApi.put(`/api/groups/${id}/admin`, { data: { username: mem, admin: true } })).status()).toBe(403);

  const memToken = await login(anon, mem);
  const memApi = await api(memToken);
  expect((await memApi.post(`/api/groups/${id}/members`, { data: { username: outsider } })).status()).toBe(403);

  // Removal by a group admin is allowed for ordinary members; self-leave works too.
  expect((await gmApi.delete(`/api/groups/${id}/members/${mem}`)).status()).toBe(200);
  expect((await memApi.post(`/api/groups/${id}/leave`)).status()).toBe(404);
  expect((await gmApi.post(`/api/groups/${id}/leave`)).status()).toBe(200);

  // Empty group can now be removed (the creator auto-joined, so leave first).
  expect((await admin.post(`/api/groups/${id}/leave`)).status()).toBe(200);
  expect((await admin.delete(`/api/groups/${id}`)).status()).toBe(200);
  const remaining: any[] = await (await admin.get('/api/groups')).json();
  expect(remaining.some(g => g.group.id === id)).toBe(false);
});

test('group visibility is scoped to members', async () => {
  const anon = await api();
  const adminToken = await login(anon, 'admin', 'admin123');
  const admin = await api(adminToken);
  const id = `g-vis-${Date.now().toString(36)}`;
  const user = `vis${Date.now().toString(36)}`;
  const outsider = `viso${Date.now().toString(36)}`;
  for (const u of [user, outsider]) await register(anon, u);
  await admin.post('/api/groups', { data: { id, name: 'Visibility' } });
  await admin.post(`/api/groups/${id}/members`, { data: { username: user } });

  const userApi = await api(await login(anon, user));
  const outsiderApi = await api(await login(anon, outsider));
  const mine = await (await userApi.get('/api/groups')).json();
  expect(mine.some((g: any) => g.group.id === id)).toBe(true);
  const theirs = await (await outsiderApi.get('/api/groups')).json();
  expect(theirs.some((g: any) => g.group.id === id)).toBe(false);
  expect((await outsiderApi.get(`/api/groups/${id}/members`)).status()).toBe(404);
});
