package com.github.wf.ext.dingtalk;

import com.github.wf.ext.OrgService;
import com.github.wf.ext.OrgTree;
import com.github.wf.ext.OrgUser;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * DingTalk (钉钉) organization service.
 * Uses DingTalk Open API — appKey/appSecret for OAuth, then REST calls.
 *
 * Config: dingtalk.app-key, dingtalk.app-secret
 */
public class DingTalkOrgService implements OrgService {

    private static final String BASE = "https://oapi.dingtalk.com";
    private static final Gson gson = new Gson();
    private static final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)).build();

    private final String appKey, appSecret;
    private volatile String accessToken;
    private volatile long tokenExpiresAt;

    public DingTalkOrgService(Properties props) {
        this.appKey = props.getProperty("dingtalk.app-key");
        this.appSecret = props.getProperty("dingtalk.app-secret");
    }

    private synchronized String getToken() {
        if (accessToken != null && System.currentTimeMillis() < tokenExpiresAt) return accessToken;
        try {
            String url = BASE + "/gettoken?appkey=" + appKey + "&appsecret=" + appSecret;
            HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            JsonObject json = gson.fromJson(resp.body(), JsonObject.class);
            accessToken = json.get("access_token").getAsString();
            tokenExpiresAt = System.currentTimeMillis() + json.get("expires_in").getAsLong() * 1000 - 60000;
            return accessToken;
        } catch (Exception e) {
            throw new RuntimeException("DingTalk auth failed", e);
        }
    }

    private JsonObject post(String path, Object body) {
        try {
            String url = BASE + path + "?access_token=" + getToken();
            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body)))
                .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            return gson.fromJson(resp.body(), JsonObject.class);
        } catch (Exception e) {
            throw new RuntimeException("DingTalk API error: " + path, e);
        }
    }

    @Override
    public OrgUser getUser(String uid) {
        try {
            JsonObject body = new JsonObject(); body.addProperty("userid", uid);
            JsonObject resp = post("/topapi/v2/user/get", body);
            if (resp.get("errcode").getAsInt() != 0) return null;
            JsonObject u = resp.getAsJsonObject("result");
            return new OrgUser(
                u.get("userid").getAsString(),
                u.get("name").getAsString(),
                u.has("email") ? u.get("email").getAsString() : null,
                getDepartmentName(u),
                getManagerUserId(u));
        } catch (Exception e) { return null; }
    }

    @Override
    public String getManager(String uid) {
        OrgUser u = getUser(uid);
        return u != null ? u.getManager() : null;
    }

    @Override
    public List<String> getGroups(String uid) {
        List<String> depts = new ArrayList<>();
        OrgUser u = getUser(uid);
        if (u != null && u.getDepartment() != null) depts.add(u.getDepartment());
        return depts;
    }

    @Override
    public List<String> getGroupMembers(String group) {
        List<String> userIds = new ArrayList<>();
        try {
            Long deptId = findDeptId(group);
            if (deptId == null) return userIds;
            JsonObject body = new JsonObject(); body.addProperty("dept_id", deptId);
            body.addProperty("cursor", 0); body.addProperty("size", 100);
            JsonObject resp = post("/topapi/v2/user/list", body);
            if (resp.get("errcode").getAsInt() == 0) {
                JsonArray list = resp.getAsJsonObject("result").getAsJsonArray("list");
                if (list != null) list.forEach(e -> userIds.add(e.getAsJsonObject().get("userid").getAsString()));
            }
        } catch (Exception ignored) {}
        return userIds;
    }

    @Override
    public List<OrgUser> searchUsers(String query) {
        List<OrgUser> result = new ArrayList<>();
        if (query == null || query.isEmpty()) return result;
        try {
            JsonObject body = new JsonObject(); body.addProperty("name", query);
            body.addProperty("offset", 0); body.addProperty("size", 50);
            JsonObject resp = post("/topapi/user/getByMobile", body);
            // DingTalk user search is limited — try exact name match via getByMobile or list by dept
        } catch (Exception ignored) {}
        return result;
    }

    @Override
    public List<String> listGroups() {
        List<String> depts = new ArrayList<>();
        try {
            JsonObject body = new JsonObject();
            body.addProperty("dept_id", 1L);
            body.addProperty("fetch_child", true);
            JsonObject resp = post("/topapi/v2/department/listsub", body);
            if (resp.get("errcode").getAsInt() == 0) {
                JsonArray list = resp.getAsJsonArray("result");
                if (list != null) list.forEach(e -> {
                    JsonObject d = e.getAsJsonObject();
                    depts.add(d.get("name").getAsString());
                });
            }
        } catch (Exception ignored) {}
        return depts;
    }

    @Override
    public List<OrgTree> getOrgTree() {
        // Build tree from department hierarchy + users
        Map<Long, List<String>> deptUsers = new HashMap<>();
        Map<Long, String> deptNames = new HashMap<>();
        Map<Long, Long> deptParents = new HashMap<>();
        loadDeptTree(1L, deptUsers, deptNames, deptParents);
        // Build OrgTree
        return buildOrgTree(1L, deptUsers, deptNames, deptParents);
    }

    private void loadDeptTree(long deptId, Map<Long, List<String>> deptUsers,
                               Map<Long, String> deptNames, Map<Long, Long> deptParents) {
        try {
            // Get sub-departments
            JsonObject body = new JsonObject(); body.addProperty("dept_id", deptId);
            JsonObject resp = post("/topapi/v2/department/listsub", body);
            if (resp.get("errcode").getAsInt() == 0) {
                JsonArray depts = resp.getAsJsonArray("result");
                if (depts != null) {
                    for (int i = 0; i < depts.size(); i++) {
                        JsonObject d = depts.get(i).getAsJsonObject();
                        long id = d.get("dept_id").getAsLong();
                        deptNames.put(id, d.get("name").getAsString());
                        deptParents.put(id, d.has("parent_id") ? d.get("parent_id").getAsLong() : deptId);
                        deptUsers.put(id, new ArrayList<>());
                        loadDeptTree(id, deptUsers, deptNames, deptParents);
                    }
                }
            }
            // Get users in this department
            JsonObject userBody = new JsonObject(); userBody.addProperty("dept_id", deptId);
            userBody.addProperty("cursor", 0); userBody.addProperty("size", 100);
            JsonObject userResp = post("/topapi/v2/user/list", userBody);
            if (userResp.get("errcode").getAsInt() == 0) {
                JsonObject r = userResp.getAsJsonObject("result");
                JsonArray users = r.getAsJsonArray("list");
                if (users != null) {
                    List<String> uids = deptUsers.computeIfAbsent(deptId, k -> new ArrayList<>());
                    for (int i = 0; i < users.size(); i++) {
                        uids.add(users.get(i).getAsJsonObject().get("userid").getAsString());
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    private List<OrgTree> buildOrgTree(long deptId, Map<Long, List<String>> deptUsers,
                                        Map<Long, String> deptNames, Map<Long, Long> deptParents) {
        List<OrgTree> roots = new ArrayList<>();
        String name = deptNames.getOrDefault(deptId, "根部门");
        OrgTree root = new OrgTree("dept-" + deptId, name, "");
        // Add users under this dept
        List<String> uids = deptUsers.getOrDefault(deptId, List.of());
        List<OrgTree> children = new ArrayList<>();
        for (String uid : uids) {
            try {
                OrgUser u = getUser(uid);
                children.add(new OrgTree(uid, u != null ? u.getCn() : uid, ""));
            } catch (Exception e) {
                children.add(new OrgTree(uid, uid, ""));
            }
        }
        // Add sub-departments as children
        for (var entry : deptParents.entrySet()) {
            if (entry.getValue() == deptId) {
                children.addAll(buildOrgTree(entry.getKey(), deptUsers, deptNames, deptParents));
            }
        }
        root.setChildren(children);
        roots.add(root);
        return roots;
    }

    private String getDepartmentName(JsonObject user) {
        JsonArray depts = user.getAsJsonArray("dept_id_list");
        if (depts != null && depts.size() > 0) {
            long deptId = depts.get(0).getAsLong();
            try {
                JsonObject body = new JsonObject(); body.addProperty("dept_id", deptId);
                JsonObject resp = post("/topapi/v2/department/get", body);
                if (resp.get("errcode").getAsInt() == 0)
                    return resp.getAsJsonObject("result").get("name").getAsString();
            } catch (Exception ignored) {}
        }
        return null;
    }

    private String getManagerUserId(JsonObject user) {
        JsonArray depts = user.getAsJsonArray("dept_id_list");
        if (depts != null && depts.size() > 0) {
            long deptId = depts.get(0).getAsLong();
            try {
                JsonObject body = new JsonObject(); body.addProperty("dept_id", deptId);
                JsonObject resp = post("/topapi/v2/department/get", body);
                if (resp.get("errcode").getAsInt() == 0) {
                    JsonObject r = resp.getAsJsonObject("result");
                    if (r.has("dept_manager_userid_list")) {
                        String managers = r.get("dept_manager_userid_list").getAsString();
                        if (managers != null && !managers.isEmpty()) {
                            String[] parts = managers.split("\\|");
                            String currentUserId = user.get("userid").getAsString();
                            // Return the first manager that's not the user themselves
                            for (String m : parts) {
                                if (!m.equals(currentUserId)) return m.trim();
                            }
                        }
                    }
                }
            } catch (Exception ignored) {}
        }
        return null;
    }

    private Long findDeptId(String name) {
        try {
            JsonObject body = new JsonObject();
            body.addProperty("dept_id", 1L);
            body.addProperty("fetch_child", true);
            JsonObject resp = post("/topapi/v2/department/listsub", body);
            if (resp.get("errcode").getAsInt() == 0) {
                JsonArray list = resp.getAsJsonArray("result");
                if (list != null) {
                    for (int i = 0; i < list.size(); i++) {
                        JsonObject d = list.get(i).getAsJsonObject();
                        if (name.equals(d.get("name").getAsString())) return d.get("dept_id").getAsLong();
                    }
                }
            }
        } catch (Exception ignored) {}
        return null;
    }
}
