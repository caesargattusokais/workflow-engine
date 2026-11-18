// Stub OpenAI-compatible LLM for E2E testing — returns a fixed valid leave-approval YAML.
// Set STUB_REJECT_TEMPERATURE=1 to behave like an o-series / GPT-5 model, which
// answers any non-default `temperature` with HTTP 400. Used to exercise the
// client-side parameter fallback.
const http = require('http');
const REJECT_TEMPERATURE = process.env.STUB_REJECT_TEMPERATURE === '1';
const YAML = `id: ai-leave-flow
name: 'AI 请假审批'
version: 1
nodes:
  - id: start
    type: startEvent
    name: 开始
  - id: apply
    type: userTask
    name: 提交请假申请
    assignee: "\${applicant}"
  - id: gateway
    type: exclusiveGateway
    name: 天数判断
    conditions:
      - expr: "days <= 3"
        to: manager
      - expr: "days > 3"
        to: director
      - default: true
        to: manager
  - id: manager
    type: userTask
    name: 经理审批
    assignee: manager
    boundaryTimer: "PT30M"
  - id: director
    type: userTask
    name: 总监审批
    assignee: director
  - id: end
    type: endEvent
    name: 结束
transitions:
  - from: start
    to: apply
  - from: apply
    to: gateway
  - from: manager
    to: end
  - from: manager
    to: director
    type: timeout
  - from: director
    to: end
`;
http.createServer((req, res) => {
  let body = '';
  req.on('data', c => body += c);
  req.on('end', () => {
    let payload = {};
    try { payload = JSON.parse(body) || {}; } catch (_) { /* not JSON — ignore */ }

    if (REJECT_TEMPERATURE && Object.prototype.hasOwnProperty.call(payload, 'temperature')) {
      const message = "Unsupported value: 'temperature' does not support 0.2 with this model. " +
        'Only the default (1) value is supported.';
      const err = JSON.stringify({ error: { message, type: 'invalid_request_error', param: 'temperature' } });
      console.log('rejected: temperature present');
      res.writeHead(400, { 'Content-Type': 'application/json', 'Content-Length': Buffer.byteLength(err) });
      res.end(err);
      return;
    }

    const data = JSON.stringify({
      choices: [{ message: { role: 'assistant', content: '```yaml\n' + YAML + '\n```' } }]
    });
    console.log('ok: temperature ' + (Object.prototype.hasOwnProperty.call(payload, 'temperature') ? 'present' : 'absent'));
    res.writeHead(200, { 'Content-Type': 'application/json', 'Content-Length': Buffer.byteLength(data) });
    res.end(data);
  });
}).listen(9999, () => console.log('stub LLM on :9999 (rejectTemperature=' + REJECT_TEMPERATURE + ')'));
