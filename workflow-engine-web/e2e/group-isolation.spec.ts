import { test, expect, ApiClient } from './fixtures';

/**
 * Group data sharing. E2E runs with auth.header-compat-enabled=true, so the
 * X-Group-Ids / X-Group-Id headers act as the trusted identity scope.
 * user-a/user-b belong to group-alpha; outsider belongs to group-beta.
 */
const ALPHA = 'group-alpha';

test('group members see each other instances and can complete their tasks', async () => {
  const a = new ApiClient('user-a', [ALPHA], ALPHA);
  const b = new ApiClient('user-b', [ALPHA], ALPHA);
  const outsider = new ApiClient('user-c', ['group-beta'], 'group-beta');
  const tag = `${Date.now()}`;

  const yaml = `id: group-share-${tag}
name: 群共享验证
nodes:
  - id: start
    type: startEvent
    name: 开始
  - id: approve
    type: userTask
    name: 审批
    assignee: user-b
  - id: end
    type: endEvent
    name: 结束
transitions:
  - from: start
    to: approve
  - from: approve
    to: end
`;
  const def = await a.deploy(yaml);
  const inst = await a.startInstance(def.id, {});
  expect(inst.variables._userId).toBe('user-a');
  expect(inst.variables._groupId).toBe(ALPHA);

  // Same group: visible detail + can operate the task
  const seenByB = await b.getInstance(inst.id);
  expect(seenByB.id).toBe(inst.id);
  const tasks = await b.listTasks({ instanceId: inst.id });
  expect(tasks.length).toBeGreaterThan(0);
  await b.completeTask(tasks[0].id, {}, 'group approve');

  // Outsider: 404 on detail and no tasks
  await expect(outsider.getInstance(inst.id)).rejects.toThrow(/404/);
  expect(await outsider.listTasks({ instanceId: inst.id })).toEqual([]);
});

test('drafts shared within group are editable by members and private to outsiders', async () => {
  const a = new ApiClient('user-d', [ALPHA], ALPHA);
  const b = new ApiClient('user-e', [ALPHA], ALPHA);
  const outsider = new ApiClient('user-f', ['group-beta'], 'group-beta');
  const name = `group-draft-${Date.now()}`;

  const draft = await a.createDraft(name);
  expect((await b.getDraft(draft.id)).id).toBe(draft.id);
  await expect(outsider.getDraft(draft.id)).rejects.toThrow(/404/);

  await b.updateDraft(draft.id, { name: `${name}-renamed` });
  expect((await a.getDraft(draft.id)).name).toBe(`${name}-renamed`);
});

test('definitions shared within group are visible to members and private to outsiders', async () => {
  const a = new ApiClient('user-g', [ALPHA], ALPHA);
  const b = new ApiClient('user-h', [ALPHA], ALPHA);
  const outsider = new ApiClient('user-i', ['group-beta'], 'group-beta');
  const id = `group-def-${Date.now()}`;
  const yaml = `id: ${id}
name: 群定义验证
nodes:
  - id: start
    type: startEvent
    name: 开始
  - id: end
    type: endEvent
    name: 结束
transitions:
  - from: start
    to: end
`;
  await a.deploy(yaml);
  expect((await b.getDefinition(id)).id).toBe(id);
  await expect(outsider.getDefinition(id)).rejects.toThrow(/404/);
});
