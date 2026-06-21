package com.github.wf.ext.feishu;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class FeishuNotifier {
    private static final String BASE = "https://open.feishu.cn/open-apis";
    private static final Gson gson = new Gson();
    private static final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private static final Log log = LogFactory.getLog(FeishuNotifier.class);
    private final FeishuOrgService orgService;

    public FeishuNotifier(FeishuOrgService orgService) {
        this.orgService = orgService;
    }

    public String sendApprovalCard(String userId, String taskId, String instanceId,
                                   String title, String applicant, String baseUrl) {
        try {
            String token = orgService.getToken();
            String completeUrl = baseUrl + "/api/tasks/" + taskId + "/complete";
            String rejectUrl = baseUrl + "/api/tasks/" + taskId + "/reject";
            String cardJson = String.format(
                    "{\"header\":{\"title\":{\"tag\":\"plain_text\",\"content\":\"待审批: %s\"},\"template\":\"blue\"}," +
                            "\"elements\":[" +
                            "{\"tag\":\"div\",\"fields\":[" +
                            "{\"is_short\":true,\"text\":{\"tag\":\"lark_md\",\"content\":\"**申请人**\\n%s\"}}," +
                            "{\"is_short\":true,\"text\":{\"tag\":\"lark_md\",\"content\":\"**实例**\\n%s\"}}" +
                            "]}," +
                            "{\"tag\":\"hr\"}," +
                            "{\"tag\":\"action\",\"actions\":[" +
                            "{\"tag\":\"button\",\"text\":{\"tag\":\"plain_text\",\"content\":\"同意\"},\"type\":\"primary\"," +
                            "\"multi_url\":{\"url\":\"%s\",\"android_url\":\"%s\",\"ios_url\":\"%s\"}}," +
                            "{\"tag\":\"button\",\"text\":{\"tag\":\"plain_text\",\"content\":\"驳回\"},\"type\":\"danger\"," +
                            "\"multi_url\":{\"url\":\"%s\",\"android_url\":\"%s\",\"ios_url\":\"%s\"}}" +
                            "]}" +
                            "]}",
                    title, applicant, instanceId.substring(0, 8),
                    completeUrl, completeUrl, completeUrl, rejectUrl, rejectUrl, rejectUrl);

            JsonObject body = new JsonObject();
            body.addProperty("receive_id", userId);
            body.addProperty("msg_type", "interactive");
            body.addProperty("content", cardJson);
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(BASE + "/im/v1/messages?receive_id_type=user_id"))
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body))).build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            JsonObject r = gson.fromJson(resp.body(), JsonObject.class);
            return r.get("code").getAsInt() == 0 ? r.getAsJsonObject("data").get("message_id").getAsString() : null;
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return null;
        }
    }
}
