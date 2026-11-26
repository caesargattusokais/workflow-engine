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

    // AI_TASK nodes use the same endpoint with a task-specific system prompt.
    // Give deterministic answers so E2E can assert variable writes + routing.
    const messages = payload.messages || [];
    const system = (messages.find(m => m.role === 'system') || {}).content || '';
    let content;
    if (system.includes('classify text into exactly one category')) {
      const userText = (messages.find(m => m.role === 'user') || {}).content || '';
      const categoryList = (system.match(/Valid categories: (.+)\./) || [])[1];
      const options = categoryList ? categoryList.split(/,\s*/).map(s => s.trim()) : [];
      const isUrgent = /7|long|超过|以上|总监/i.test(userText);
      // Keep LONG/SHORT prompts deterministic for existing tests; for templates
      // with domain-specific options, map urgent -> first option, normal -> last.
      if (options.some(o => o === 'LONG' || o === 'SHORT')) {
        content = isUrgent ? 'LONG' : 'SHORT';
      } else {
        content = options.length ? (isUrgent ? options[0] : options[options.length - 1]) : (isUrgent ? 'LONG' : 'SHORT');
      }
    } else if (system.includes('extract structured data')) {
      const userText = (messages.find(m => m.role === 'user') || {}).content || '';
      const m = userText.match(/(\d+(?:\.\d+)?)/);
      const amount = m ? Number(m[1]) : 100;
      content = JSON.stringify({ category: 'EXPENSE', amount });
    } else if (system.includes('summarise the given content')) {
      content = 'stub summary';
    } else if (system.includes('"severity": "info|warning|error"')) {
      // AI copilot diagnosis: deterministic structured JSON so the E2E can
      // assert the parsed diagnosis without depending on a real model.
      content = JSON.stringify({
        summary: '实例正在等待人工审批',
        cause: '当前活动节点为人工任务，suspendReasons 为空，无异常',
        suggestions: ['催办或转派当前待办人', '如需干预可终止该实例'],
        severity: 'info',
      });
    } else {
      content = '```yaml\n' + YAML + '\n```';
    }

    const data = JSON.stringify({
      choices: [{ message: { role: 'assistant', content } }]
    });
    console.log('ok: ' + (content.length > 60 ? 'yaml' : 'task answer') +
      ' / temperature ' + (Object.prototype.hasOwnProperty.call(payload, 'temperature') ? 'present' : 'absent'));
    res.writeHead(200, { 'Content-Type': 'application/json', 'Content-Length': Buffer.byteLength(data) });
    res.end(data);
  });
}).listen(9999, () => console.log('stub LLM on :9999 (rejectTemperature=' + REJECT_TEMPERATURE + ')'));
