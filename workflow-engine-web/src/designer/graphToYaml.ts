import type { Node, Edge } from '@xyflow/react';

export function graphToYaml(nodes: Node[], edges: Edge[], name: string = 'workflow'): string {
  const lines: string[] = [];
  lines.push(`id: ${name}`);
  lines.push('version: 1');
  lines.push('nodes:');

  for (const node of nodes) {
    const data = node.data as Record<string, unknown>;
    lines.push(`  - id: ${node.id}`);
    lines.push(`    type: ${node.type}`);
    if (data.name) lines.push(`    name: "${data.name}"`);
    if (data.assignee && node.type === 'userTask') lines.push(`    assignee: "${data.assignee}"`);
    if (data.handlerClass && node.type === 'serviceTask') lines.push(`    handlerClass: "${data.handlerClass}"`);
    const groups = data.candidateGroups as string[] | undefined;
    if (groups && groups.length > 0) {
      lines.push(`    candidateGroups: [${groups.map(g => `"${g}"`).join(', ')}]`);
    }
  }

  lines.push('transitions:');
  for (const edge of edges) {
    const label = (edge as any).label as string | undefined;
    if (label) {
      lines.push(`  - from: ${edge.source}`);
      lines.push(`    to: ${edge.target}`);
      if (label === 'default') {
        lines.push(`    type: default`);
      } else {
        lines.push(`    type: conditional`);
        lines.push(`    expr: "${label}"`);
      }
    } else {
      lines.push(`  - from: ${edge.source}`);
      lines.push(`    to: ${edge.target}`);
    }
  }

  return lines.join('\n');
}
