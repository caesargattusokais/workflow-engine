package com.github.wf.mockldap;

import com.github.wf.ext.OrgService;
import com.github.wf.ext.OrgTree;
import com.github.wf.ext.OrgUser;

import java.util.*;

/**
 * In-memory OrgService with hardcoded test organization data.
 * Activate with: spring.profiles.active=mock-ldap
 *
 * Test org:
 *   zhangsan(张三/tech) → reports to lisi
 *   lisi(李四/tech)     → reports to wangwu
 *   wangwu(王五/总监)    → no manager
 *   zhaoliu(赵六/finance)→ reports to wangwu
 *
 * Groups:
 *   developers: zhangsan, lisi
 *   managers:   lisi, wangwu
 *   finance:    zhaoliu
 *   role-approver-dept-tech: lisi
 */
public class MockOrgService implements OrgService {

    private final Map<String, OrgUser> users = new LinkedHashMap<>();
    private final Map<String, List<String>> groups = new LinkedHashMap<>();

    public MockOrgService() {
        addUser("zhangsan", "张三", "zhangsan@test.com", "tech", "lisi");
        addUser("lisi", "李四", "lisi@test.com", "tech", "wangwu");
        addUser("wangwu", "王五", "wangwu@test.com", "management", null);
        addUser("zhaoliu", "赵六", "zhaoliu@test.com", "finance", "wangwu");

        addGroup("developers", "zhangsan", "lisi");
        addGroup("managers", "lisi", "wangwu");
        addGroup("finance", "zhaoliu");
        addGroup("role-approver-dept-tech", "lisi");
    }

    private void addUser(String uid, String cn, String email, String dept, String manager) {
        users.put(uid, new OrgUser(uid, cn, email, dept, manager));
    }
    private void addGroup(String name, String... members) {
        groups.put(name, List.of(members));
    }

    @Override public OrgUser getUser(String uid) { return users.get(uid); }

    @Override public String getManager(String uid) {
        OrgUser u = users.get(uid);
        return u != null ? u.getManager() : null;
    }

    @Override public List<String> getGroups(String uid) {
        List<String> result = new ArrayList<>();
        for (var e : groups.entrySet()) {
            if (e.getValue().contains(uid)) result.add(e.getKey());
        }
        return result;
    }

    @Override public List<String> getGroupMembers(String group) {
        return groups.getOrDefault(group, List.of());
    }

    @Override public List<String> getReports(String uid) {
        List<String> result = new ArrayList<>();
        for (OrgUser u : users.values()) {
            if (uid.equals(u.getManager())) result.add(u.getUid());
        }
        return result;
    }

    @Override public List<OrgUser> searchUsers(String query) {
        if (query == null || query.isEmpty()) return new ArrayList<>(users.values());
        String q = query.toLowerCase();
        return users.values().stream()
            .filter(u -> u.getUid().toLowerCase().contains(q)
                      || u.getCn().toLowerCase().contains(q))
            .toList();
    }

    @Override public List<String> listGroups() {
        return new ArrayList<>(groups.keySet());
    }

    @Override
    public String resolveRole(String role, String context) {
        String groupName = "role-" + role + "-dept-" + (context != null ? context : "any");
        List<String> members = getGroupMembers(groupName);
        if (members.isEmpty()) {
            // fallback: try without -dept- prefix
            groupName = "role-" + role;
            members = getGroupMembers(groupName);
        }
        return members.isEmpty() ? null : members.get(0);
    }

    @Override
    public boolean isGroupMember(String uid, String group) {
        return getGroups(uid).contains(group);
    }

    @Override
    public List<OrgTree> getOrgTree() {
        // Find roots: users with no manager
        List<OrgTree> roots = new ArrayList<>();
        for (OrgUser u : users.values()) {
            if (u.getManager() == null || !users.containsKey(u.getManager())) {
                roots.add(buildTreeNode(u.getUid()));
            }
        }
        return roots;
    }

    private OrgTree buildTreeNode(String uid) {
        OrgUser u = users.get(uid);
        if (u == null) return new OrgTree(uid, uid, "");
        OrgTree node = new OrgTree(u.getUid(), u.getCn(), u.getDepartment());
        List<String> reports = getReports(uid);
        if (!reports.isEmpty()) {
            List<OrgTree> children = new ArrayList<>();
            for (String r : reports) children.add(buildTreeNode(r));
            node.setChildren(children);
        }
        return node;
    }
}
