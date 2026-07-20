import { test, expect } from './fixtures';

// Use unique names per test to avoid data pollution in shared memory mode
let counter = 0;
function uniqueName(prefix: string) {
  return `${prefix}-${Date.now()}-${++counter}`;
}

test.describe('Drafts API', () => {
  test('create a draft', async ({ api }) => {
    const draft = await api.createDraft(uniqueName('Test Draft'));
    expect(draft).toHaveProperty('id');
    expect(draft).toHaveProperty('name');
  });

  test('create draft with blank name fails', async ({ api }) => {
    await expect(api.createDraft('')).rejects.toThrow();
  });

  test('create draft with duplicate name fails', async ({ api }) => {
    const name = uniqueName('Unique Draft');
    await api.createDraft(name);
    await expect(api.createDraft(name)).rejects.toThrow();
  });

  test('list drafts returns paginated results', async ({ api }) => {
    const result = await api.listDrafts();
    expect(result).toHaveProperty('items');
    expect(result).toHaveProperty('total');
  });

  test('get draft by ID', async ({ api }) => {
    const name = uniqueName('Get Test');
    const draft = await api.createDraft(name);
    const fetched = await api.getDraft(draft.id);
    expect(fetched.name).toBe(name);
  });

  test('update draft name', async ({ api }) => {
    const draft = await api.createDraft(uniqueName('Old Name'));
    const newName = uniqueName('New Name');
    const updated = await api.updateDraft(draft.id, { name: newName });
    expect(updated.name).toBe(newName);
  });

  test('update draft name to duplicate fails', async ({ api }) => {
    const nameA = uniqueName('Draft A');
    await api.createDraft(nameA);
    const draftB = await api.createDraft(uniqueName('Draft B'));
    await expect(api.updateDraft(draftB.id, { name: nameA })).rejects.toThrow();
  });

  test('update draft nodes', async ({ api }) => {
    const draft = await api.createDraft(uniqueName('Node Test'));
    const nodes = [{ id: 'start', type: 'startEvent', position: { x: 100, y: 50 }, data: {} }];
    const updated = await api.updateDraft(draft.id, { nodes });
    expect(updated.nodes.length).toBe(1);
  });

  test('update draft edges', async ({ api }) => {
    const draft = await api.createDraft(uniqueName('Edge Test'));
    const edges = [{ id: 'e1', source: 'start', target: 'end' }];
    const updated = await api.updateDraft(draft.id, { edges });
    expect(updated.edges.length).toBe(1);
  });

  test('update draft version', async ({ api }) => {
    const draft = await api.createDraft(uniqueName('Version Test'));
    const updated = await api.updateDraft(draft.id, { version: 2 });
    expect(updated.version).toBe(2);
  });

  test('delete draft', async ({ api }) => {
    const draft = await api.createDraft(uniqueName('Delete Me'));
    await api.deleteDraft(draft.id);
    await expect(api.getDraft(draft.id)).rejects.toThrow();
  });

  test('copy draft', async ({ api }) => {
    const name = uniqueName('Original');
    const draft = await api.createDraft(name);
    const copy = await api.copyDraft(draft.id);
    expect(copy.name).toBe(`${name} (Copy)`);
    expect(copy.id).not.toBe(draft.id);
  });

  test('copy draft with name collision', async ({ api }) => {
    const name = uniqueName('Collision');
    const draft = await api.createDraft(name);
    await api.copyDraft(draft.id); // first copy
    const copy2 = await api.copyDraft(draft.id); // second copy
    expect(copy2.name).toContain('Copy 2');
  });

  test('import draft from YAML', async ({ api }) => {
    const name = uniqueName('Imported Flow');
    const nodes = [
      { id: 'start', type: 'startEvent', position: { x: 100, y: 50 }, data: { name: 'Start' } },
      { id: 'end', type: 'endEvent', position: { x: 100, y: 200 }, data: { name: 'End' } },
    ];
    const edges = [{ id: 'e1', source: 'start', target: 'end', data: { edgeType: 'direct' } }];
    const imported = await api.importDraft(name, nodes, edges);
    expect(imported).toHaveProperty('id');
    expect(imported.name).toBe(name);
  });

  test('import with duplicate name auto-renames', async ({ api }) => {
    const name = uniqueName('Same Name');
    await api.createDraft(name);
    const nodes: any[] = [];
    const edges: any[] = [];
    const imported = await api.importDraft(name, nodes, edges);
    expect(imported.name).toContain(name.split('-')[0]); // contains base name
    expect(imported.name).not.toBe(name);
  });

  test('get non-existent draft returns error', async ({ api }) => {
    await expect(api.getDraft('nonexistent-id')).rejects.toThrow();
  });
});
