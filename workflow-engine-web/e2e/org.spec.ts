import { test, expect } from './fixtures';

test.describe('Organization API', () => {
  test('get org tree with mock-ldap', async ({ api }) => {
    const tree = await api.getOrgTree();
    expect(Array.isArray(tree)).toBe(true);
    // mock-ldap provides tree with root nodes
    if (tree.length > 0) {
      expect(tree[0]).toHaveProperty('uid');
    }
  });

  test('search users', async ({ api }) => {
    const users = await api.searchUsers('zhang');
    expect(Array.isArray(users)).toBe(true);
    // mock-ldap should have zhangsan
    if (users.length > 0) {
      expect(users[0]).toHaveProperty('uid');
    }
  });

  test('search users empty query', async ({ api }) => {
    const users = await api.searchUsers('');
    expect(Array.isArray(users)).toBe(true);
  });

  test('list groups', async ({ api }) => {
    const groups = await api.listGroups();
    expect(Array.isArray(groups)).toBe(true);
    // mock-ldap provides groups like developers, managers
    if (groups.length > 0) {
      expect(typeof groups[0]).toBe('string');
    }
  });
});
