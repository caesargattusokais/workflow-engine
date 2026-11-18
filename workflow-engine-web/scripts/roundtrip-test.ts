/* Round-trip self-test: graph → YAML → graph → YAML, compare key fields. */
import { graphToYaml } from '../src/designer/graphToYaml';
import { yamlToGraph } from '../src/designer/yamlToGraph';
import type { Node, Edge } from '@xyflow/react';

const mk = (id: string, type: string, data: Record<string, unknown>): Node =>
  ({ id, type, position: { x: 0, y: 0 }, data } as unknown as Node);

const nodes: Node[] = [
  mk('start', 'startEvent', { name: '开始' }),
  mk('gw', 'exclusiveGateway', { name: '判断', conditions: [
    { expr: "days > 3", to: 'manager', isDefault: false },
    { to: 'dept', isDefault: true },
  ] }),
  mk('approve', 'userTask', { name: '审批', assignee: '${applicant}.manager', boundaryTimer: 'PT30M',
    candidateGroups: ['hr', 'it-dept'] }),
  mk('http', 'serviceTask', { name: '调用', httpMode: true, url: 'https://api.example.com/check',
    method: 'POST', headerEntries: [{ key: 'Content-Type', value: 'application/json' }],
    paramEntries: [{ key: 'amount', value: '${amount}' }], retryMaxAttempts: 3, retryDelayMs: 1000, retryBackoff: 2,
    retryOn: [{ expr: "exception.type.contains('Timeout')" }] }),
  mk('sub', 'callActivity', { name: '子流程', calledId: 'child-flow', calledVersion: 2,
    inputMapping: [{ from: 'days', to: 'd', expr: 'days + 1' }], outputMapping: [{ from: 'result', to: 'r' }] }),
  mk('end', 'endEvent', { name: '结束' }),
];

const edges: Edge[] = [
  { id: 'e1', source: 'start', target: 'gw', type: 'smoothstep', data: { edgeType: 'direct' } },
  { id: 'e2', source: 'gw', target: 'manager', type: 'smoothstep', data: { edgeType: 'conditional', expr: 'days > 3' } },
  { id: 'e3', source: 'gw', target: 'dept', type: 'smoothstep', data: { edgeType: 'default', isDefault: true } },
  { id: 'e4', source: 'approve', target: 'http', type: 'smoothstep', data: { edgeType: 'direct' } },
  { id: 'e5', source: 'http', target: 'sub', type: 'smoothstep', data: { edgeType: 'result', expr: "result.status == 'PASS'" } },
  { id: 'e6', source: 'sub', target: 'end', type: 'smoothstep', data: { edgeType: 'direct' } },
] as Edge[];

const yaml1 = graphToYaml(nodes, edges, 'leave-approval', '请假审批', 1);
console.log('=== YAML (export) ===');
console.log(yaml1);

const g1 = yamlToGraph(yaml1);
console.log('=== Import check ===');
console.log('nodes:', g1.nodes.length, 'edges:', g1.edges.length, 'name:', g1.name);

const dataOf = (id: string) => g1.nodes.find(n => n.id === id)?.data as Record<string, unknown> | undefined;
const gw = dataOf('gw');
const http = dataOf('http');
const sub = dataOf('sub');
const approve = dataOf('approve');

const checks: Array<[string, boolean]> = [
  ['name preserved', g1.name === '请假审批'],
  ['node count', g1.nodes.length === 6],
  ['gateway conditions round-trip', JSON.stringify(gw?.conditions) === JSON.stringify([
      { expr: 'days > 3', to: 'manager', isDefault: false }, { expr: '', to: 'dept', isDefault: true }])],
  ['userTask assignee + timer + groups',
    approve?.assignee === '${applicant}.manager' && approve?.boundaryTimer === 'PT30M'
      && JSON.stringify(approve?.candidateGroups) === JSON.stringify(['hr', 'it-dept'])],
  ['http url/method/headers', http?.url === 'https://api.example.com/check' && http?.method === 'POST'
      && JSON.stringify(http?.headerEntries) === JSON.stringify([{ key: 'Content-Type', value: 'application/json' }])],
  ['http body params', JSON.stringify(http?.paramEntries) === JSON.stringify([{ key: 'amount', value: '${amount}' }])],
  ['http retry config', http?.retryMaxAttempts === 3 && http?.retryDelayMs === 1000 && http?.retryBackoff === 2
      && JSON.stringify(http?.retryOn) === JSON.stringify([{ expr: "exception.type.contains('Timeout')" }])],
  ['callActivity mappings', sub?.calledId === 'child-flow' && sub?.calledVersion === 2
      && JSON.stringify(sub?.inputMapping) === JSON.stringify([{ from: 'days', to: 'd', expr: 'days + 1' }])
      && JSON.stringify(sub?.outputMapping) === JSON.stringify([{ from: 'result', to: 'r' }])],
  ['edge types preserved', (() => {
    const byTarget = (t: string) => g1.edges.find(e => e.target === t)?.data as Record<string, unknown> | undefined;
    const httpEdge = g1.edges.find(e => e.source === 'approve' && e.target === 'http')?.data as Record<string, unknown> | undefined;
    return httpEdge?.edgeType === 'direct'
      && JSON.stringify(g1.edges.find(e => e.source === 'http')?.data) === JSON.stringify({ edgeType: 'result', expr: "result.status == 'PASS'" });
  })()],
  ['unique edge ids', new Set(g1.edges.map(e => e.id)).size === g1.edges.length],
];

let failed = 0;
for (const [label, ok] of checks) {
  console.log((ok ? 'PASS' : 'FAIL') + '  ' + label);
  if (!ok) failed++;
}

// Round-trip stability: export the imported graph again, import again, compare structures
const yaml2 = graphToYaml(g1.nodes, g1.edges, 'leave-approval', '请假审批', 1);
const g2 = yamlToGraph(yaml2);
const stable = g2.nodes.length === g1.nodes.length && g2.edges.length === g1.edges.length
  && JSON.stringify(g2.nodes.map(n => [n.id, n.type, n.data])) === JSON.stringify(g1.nodes.map(n => [n.id, n.type, n.data]));
console.log((stable ? 'PASS' : 'FAIL') + '  re-export/import stable');
if (!stable) {
  console.log('--- yaml2 ---'); console.log(yaml2);
  failed++;
}
process.exit(failed === 0 ? 0 : 1);
