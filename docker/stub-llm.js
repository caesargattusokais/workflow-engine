// Stub OpenAI-compatible LLM for E2E testing — returns a fixed valid leave-approval YAML.
const http = require('http');
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
    const data = JSON.stringify({
      choices: [{ message: { role: 'assistant', content: '```yaml\n' + YAML + '\n```' } }]
    });
    res.writeHead(200, { 'Content-Type': 'application/json', 'Content-Length': Buffer.byteLength(data) });
    res.end(data);
  });
}).listen(9999, () => console.log('stub LLM on :9999'));
