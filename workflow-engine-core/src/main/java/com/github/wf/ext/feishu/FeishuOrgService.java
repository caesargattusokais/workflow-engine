package com.github.wf.ext.feishu;

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
import java.util.*;

/**
 * Feishu (飞书) organization service.
 * Uses Feishu Open API — appId/appSecret for tenant access token.
 *
 * Config: feishu.app-id, feishu.app-secret
 */
public class FeishuOrgService implements OrgService {

    private static final String BASE = "https://open.feishu.cn/open-apis";
    private static final Gson gson = new Gson();
    private static final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)).build();

    private final String appId, appSecret;
    private volatile String accessToken;
    private volatile long tokenExpiresAt;

    // Cache: userId → OrgUser
    private final Map<String, OrgUser> userCache = new LinkedHashMap<>() {
        protected boolean removeEldestEntry(Map.Entry<String, OrgUser> e) { return size() > 5000; }
    };

    public FeishuOrgService(Properties props) {
        this.appId = props.getProperty("feishu.app-id");
        this.appSecret = props.getProperty("feishu.app-secret");
    }

    private synchronized String getToken() {
        if (accessToken != null && System.currentTimeMillis() < tokenExpiresAt) return accessToken;
        try {
            JsonObject body = new JsonObject();
            body.addProperty("app_id", appId);
            body.addProperty("app_secret", appSecret);
            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/auth/v3/tenant_access_token/internal"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body)))
                .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            JsonObject json = gson.fromJson(resp.body(), JsonObject.class);
            accessToken = json.get("tenant_access_token").getAsString();
            tokenExpiresAt = System.currentTimeMillis() + json.get("expire").getAsLong() * 1000 - 60000;
            return accessToken;
        } catch (Exception e) {
            throw new RuntimeException("Feishu auth failed", e);
        }
    }

    private JsonObject get(String path) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + path))
                .header("Authorization", "Bearer " + getToken())
                .GET().build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            return gson.fromJson(resp.body(), JsonObject.class);
        } catch (Exception e) {
            throw new RuntimeException("Feishu API error: " + path, e);
        }
    }

    @Override
    public OrgUser getUser(String uid) {
        OrgUser cached = userCache.get(uid);
        if (cached != null) return cached;
        try {
            JsonObject resp = get("/contact/v3/users/" + uid + "?user_id_type=user_id");
            if (resp.get("code").getAsInt() != 0) return null;
            JsonObject u = resp.getAsJsonObject("data").getAsJsonObject("user");
            OrgUser orgUser = mapUser(u);
            userCache.put(uid, orgUser);
            return orgUser;
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
            String deptId = findDeptId(group);
            if (deptId == null) return userIds;
            String pageToken = "";
            do {
                JsonObject resp = get("/contact/v3/users/find_by_department?department_id="
                    + deptId + "&page_size=100&page_token=" + pageToken);
                if (resp.get("code").getAsInt() != 0) break;
                JsonObject data = resp.getAsJsonObject("data");
                JsonArray items = data.getAsJsonArray("items");
                if (items != null) {
                    for (int i = 0; i < items.size(); i++)
                        userIds.add(items.get(i).getAsJsonObject().get("user_id").getAsString());
                }
                pageToken = data.has("page_token") ? data.get("page_token").getAsString() : "";
            } while (!pageToken.isEmpty());
        } catch (Exception ignored) {}
        return userIds;
    }

    @Override
    public List<OrgUser> searchUsers(String query) {
        List<OrgUser> result = new ArrayList<>();
        if (query == null || query.isEmpty()) return result;
        try {
            String pageToken = "";
            do {
                JsonObject resp = get("/contact/v3/users?page_size=50&page_token=" + pageToken
                    + "&name=" + urlEncode(query));
                if (resp.get("code").getAsInt() != 0) break;
                JsonObject data = resp.getAsJsonObject("data");
                JsonArray items = data.getAsJsonArray("items");
                if (items != null) {
                    for (int i = 0; i < items.size(); i++) {
                        OrgUser u = mapUser(items.get(i).getAsJsonObject());
                        userCache.put(u.getUid(), u);
                        result.add(u);
                    }
                }
                pageToken = data.has("page_token") ? data.get("page_token").getAsString() : "";
            } while (!pageToken.isEmpty());
        } catch (Exception ignored) {}
        return result;
    }

    @Override
    public List<String> listGroups() {
        List<String> depts = new ArrayList<>();
        try {
            loadAllDeptNames("0", depts);
        } catch (Exception ignored) {}
        return depts;
    }

    private void loadAllDeptNames(String parentId, List<String> result) {
        try {
            String pageToken = "";
            do {
                JsonObject resp = get("/contact/v3/departments?parent_department_id=" + parentId
                    + "&page_size=100&page_token=" + pageToken);
                if (resp.get("code").getAsInt() != 0) break;
                JsonObject data = resp.getAsJsonObject("data");
                JsonArray items = data.getAsJsonArray("items");
                if (items != null) {
                    for (int i = 0; i < items.size(); i++) {
                        JsonObject d = items.get(i).getAsJsonObject();
                        String deptId = d.get("department_id").getAsString();
                        result.add(d.get("name").getAsString());
                        loadAllDeptNames(deptId, result);
                    }
                }
                pageToken = data.has("page_token") ? data.get("page_token").getAsString() : "";
            } while (!pageToken.isEmpty());
        } catch (Exception ignored) {}
    }

    @Override
    public List<OrgTree> getOrgTree() {
        return buildDeptTree("0");
    }

    private List<OrgTree> buildDeptTree(String parentId) {
        List<OrgTree> result = new ArrayList<>();
        try {
            String pageToken = "";
            do {
                JsonObject resp = get("/contact/v3/departments?parent_department_id=" + parentId
                    + "&fetch_child=true&page_size=100&page_token=" + pageToken);
                if (resp.get("code").getAsInt() != 0) break;
                JsonObject data = resp.getAsJsonObject("data");
                JsonArray items = data.getAsJsonArray("items");
                if (items != null) {
                    for (int i = 0; i < items.size(); i++) {
                        JsonObject d = items.get(i).getAsJsonObject();
                        String deptId = d.get("department_id").getAsString();
                        String deptName = d.get("name").getAsString();
                        OrgTree deptNode = new OrgTree("dept-" + deptId, deptName, "");
                        // Add users under this department
                        List<OrgTree> children = new ArrayList<>();
                        loadDeptUsers(deptId, children);
                        // Add sub-departments
                        children.addAll(buildDeptTree(deptId));
                        deptNode.setChildren(children);
                        result.add(deptNode);
                    }
                }
                pageToken = data.has("page_token") ? data.get("page_token").getAsString() : "";
            } while (!pageToken.isEmpty());
        } catch (Exception ignored) {}
        return result;
    }

    private void loadDeptUsers(String deptId, List<OrgTree> children) {
        try {
            String pageToken = "";
            do {
                JsonObject resp = get("/contact/v3/users/find_by_department?department_id="
                    + deptId + "&page_size=100&page_token=" + pageToken);
                if (resp.get("code").getAsInt() != 0) break;
                JsonObject data = resp.getAsJsonObject("data");
                JsonArray items = data.getAsJsonArray("items");
                if (items != null) {
                    for (int i = 0; i < items.size(); i++) {
                        JsonObject u = items.get(i).getAsJsonObject();
                        String uid = u.get("user_id").getAsString();
                        String name = u.get("name").getAsString();
                        children.add(new OrgTree(uid, name, ""));
                        userCache.put(uid, mapUser(u));
                    }
                }
                pageToken = data.has("page_token") ? data.get("page_token").getAsString() : "";
            } while (!pageToken.isEmpty());
        } catch (Exception ignored) {}
    }

    private OrgUser mapUser(JsonObject u) {
        String uid = u.has("user_id") ? u.get("user_id").getAsString() : u.get("open_id").getAsString();
        String cn = u.get("name").getAsString();
        String email = u.has("email") ? u.get("email").getAsString() : null;
        String dept = null;
        JsonArray depts = u.getAsJsonArray("department_ids");
        if (depts != null && depts.size() > 0) dept = depts.get(0).getAsString();
        String manager = u.has("leader_user_id") && !u.get("leader_user_id").isJsonNull()
            ? u.get("leader_user_id").getAsString() : null;
        return new OrgUser(uid, cn, email, dept, manager);
    }

    private String findDeptId(String name) {
        try {
            String pageToken = "";
            do {
                JsonObject resp = get("/contact/v3/departments?page_size=100&page_token=" + pageToken);
                if (resp.get("code").getAsInt() != 0) break;
                JsonObject data = resp.getAsJsonObject("data");
                JsonArray items = data.getAsJsonArray("items");
                if (items != null) {
                    for (int i = 0; i < items.size(); i++) {
                        JsonObject d = items.get(i).getAsJsonObject();
                        if (name.equals(d.get("name").getAsString()))
                            return d.get("department_id").getAsString();
                    }
                }
                pageToken = data.has("page_token") ? data.get("page_token").getAsString() : "";
            } while (!pageToken.isEmpty());
        } catch (Exception ignored) {}
        return null;
    }

    private static String urlEncode(String s) {
        try {
            return java.net.URLEncoder.encode(s, "UTF-8");
        } catch (Exception e) { return s; }
    }
}
