import { test, expect } from './fixtures';

test.describe('Drafts API', () => {
  test('create a draft', async ({ api }) => {
    const draft = await api.createDraft('Test Draft');
    expect(draft).toHaveProperty('id');
    expect(draft.name).toBe('Test Draft');
  });

  test('create draft with blank name fails', async ({ api }) => {
    await expect(api.createDraft('')).rejects.toThrow();
  });

  test('create draft with duplicate name fails', async ({ api }) => {
    await api.createDraft('Unique Draft');
    await expect(api.createDraft('Unique Draft')).rejects.toThrow();
  });

  test('list drafts returns paginated results', async ({ api }) => {
    const result = await api.listDrafts();
    expect(result).toHaveProperty('items');
    expect(result).toHaveProperty('total');
  });

  test('get draft by ID', async ({ api }) => {
    const draft = await api.createDraft('Get Test');
    const fetched = await api.getDraft(draft.id);
    expect(fetched.name).toBe('Get Test');
  });

  test('update draft name', async ({ api }) => {
    const draft = await api.createDraft('Old Name');
    const updated = await api.updateDraft(draft.id, { name: 'New Name' });
    expect(updated.name).toBe('New Name');
  });

  test('update draft name to duplicate fails', async ({ api }) => {
    await api.createDraft('Draft A');
    const draftB = await api.createDraft('Draft B');
    await expect(api.updateDraft(draftB.id, { name: 'Draft A' })).rejects.toThrow();
  });

  test('update draft nodes', async ({ api }) => {
    const draft = await api.createDraft('Node Test');
    const nodes = [{ id: 'start', type: 'startEvent', position: { x: 100, y: 50 }, data: {} }];
    const updated = await api.updateDraft(draft.id, { nodes });
    expect(updated.nodes.length).toBe(1);
  });

  test('update draft edges', async ({ api }) => {
    const draft = await api.createDraft('Edge Test');
    const edges = [{ id: 'e1', source: 'start', target: 'end' }];
    const updated = await api.updateDraft(draft.id, { edges });
    expect(updated.edges.length).toBe(1);
  });

  test('update draft version', async ({ api }) => {
    const draft = await api.createDraft('Version Test');
    const updated = await api.updateDraft(draft.id, { version: 2 });
    expect(updated.version).toBe(2);
  });

  test('delete draft', async ({ api }) => {
    const draft = await api.createDraft('Delete Me');
    await api.deleteDraft(draft.id);
    await expect(api.getDraft(draft.id)).rejects.toThrow();
  });

  test('copy draft', async ({ api }) => {
    const draft = await api.createDraft('Original');
    const copy = await api.copyDraft(draft.id);
    expect(copy.name).toBe('Original (Copy)');
    expect(copy.id).not.toBe(draft.id);
  });

  test('copy draft with name collision', async ({ api }) => {
    const draft = await api.createDraft('Collision');
    await api.copyDraft(draft.id); // first copy
    const copy2 = await api.copyDraft(draft.id); // second copy
    expect(copy2.name).toContain('Copy 2');
  });

  test('import draft from YAML', async ({ api }) => {
    const nodes = [
      { id: 'start', type: 'startEvent', position: { x: 100, y: 50 }, data: { name: 'Start' } },
      { id: 'end', type: 'endEvent', position: { x: 100, y: 200 }, data: { name: 'End' } },
    ];
    const edges = [{ id: 'e1', source: 'start', target: 'end', data: { edgeType: 'direct' } }];
    const imported = await api.importDraft('Imported Flow', nodes, edges);
    expect(imported).toHaveProperty('id');
    expect(imported.name).toBe('Imported Flow');
  });

  test('import with duplicate name auto-renames', async ({ api }) => {
    await api.createDraft('Same Name');
    const nodes: any[] = [];
    const edges: any[] = [];
    const imported = await api.importDraft('Same Name', nodes, edges);
    expect(imported.name).toContain('Same Name');
    expect(imported.name).not.toBe('Same Name');
  });

  test('get non-existent draft returns error', async ({ api }) => {
    await expect(api.getDraft('nonexistent-id')).rejects.toThrow();
  });
});
