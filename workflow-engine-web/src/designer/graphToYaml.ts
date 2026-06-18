import type { Node, Edge } from '@xyflow/react';

interface ConditionItem {
  expr: string;
  to: string;
  isDefault: boolean;
}

export function graphToYaml(nodes: Node[], edges: Edge[], name: string = 'workflow', version: number = 1): string {
  const lines: string[] = [];
  lines.push(`id: ${name}`);
  lines.push(`version: ${version}`);
  lines.push('nodes:');

  for (const node of nodes) {
    const data = node.data as Record<string, unknown>;
    lines.push(`  - id: ${node.id}`);
    lines.push(`    type: ${node.type}`);
    if (data.name) lines.push(`    name: "${data.name}"`);

    // Gateway conditions (on-node, not on-edge)
    if (node.type === 'exclusiveGateway' || node.type === 'inclusiveGateway') {
      const conds = data.conditions as ConditionItem[] | undefined;
      if (conds && conds.length > 0) {
        lines.push('    conditions:');
        for (const c of conds) {
          if (c.isDefault) {
            lines.push(`      - default: true`);
            lines.push(`        to: ${c.to}`);
          } else {
            lines.push(`      - expr: "${c.expr}"`);
            lines.push(`        to: ${c.to}`);
          }
        }
      }
    }

    // UserTask fields
    if (data.assignee && node.type === 'userTask') {
      lines.push(`    assignee: "${data.assignee}"`);
    }

    // ServiceTask fields
    if (node.type === 'serviceTask') {
      const httpMode = data.httpMode as boolean;
      if (httpMode) {
        lines.push('    httpMode: true');
      }
      if (data.handlerClass && !httpMode) {
        lines.push(`    handlerClass: "${data.handlerClass}"`);
      }
      if (data.url && httpMode) {
        // Assemble URL with query params for GET/DELETE
        const paramEntries = (data.paramEntries as Array<{key:string;value:string}>) || [];
        const method = (data.method as string) || 'POST';
        const urlStr = (data.url as string) || '';
        if (['GET', 'DELETE'].includes(method) && paramEntries.length > 0) {
          const qs = paramEntries
            .filter(p => p.key)
            .map(p => `${encodeURIComponent(p.key)}=${encodeURIComponent(p.value)}`).join('&');
          lines.push(`    url: "${urlStr}?${qs}"`);
        } else {
          lines.push(`    url: "${urlStr}"`);
        }
        if (data.method) lines.push(`    method: ${method}`);
        // Headers from KV
        const headerEntries = (data.headerEntries as Array<{key:string;value:string}>) || [];
        if (headerEntries.length > 0) {
          lines.push('    headers:');
          for (const h of headerEntries) {
            if (h.key) lines.push(`      ${h.key}: "${h.value}"`);
          }
        }
        // Body from KV for POST/PUT/PATCH
        if (['POST', 'PUT', 'PATCH'].includes(method) && paramEntries.length > 0) {
          const bodyObj: Record<string, string> = {};
          for (const p of paramEntries) {
            if (p.key) bodyObj[p.key] = p.value;
          }
          if (Object.keys(bodyObj).length > 0) {
            lines.push(`    body: '${JSON.stringify(bodyObj)}'`);
          }
        }
      }
    }

    // Retry config
    if (node.type === 'serviceTask' && data.retryMaxAttempts) {
      lines.push('    retry:');
      lines.push(`      maxAttempts: ${data.retryMaxAttempts}`);
      lines.push(`      delayMs: ${data.retryDelayMs || 1000}`);
      lines.push(`      backoffMultiplier: ${data.retryBackoff || 2}`);
    }
    // Result routes
    const resultRoutes = data.resultRoutes as any[] | undefined;
    if (resultRoutes && resultRoutes.length > 0) {
      lines.push('    resultRouting:');
      for (const r of resultRoutes) {
        if (r.isDefault) { lines.push(`      - default: true`); lines.push(`        to: ${r.to}`); }
        else { lines.push(`      - expr: "${r.expr}"`); lines.push(`        to: ${r.to}`); }
      }
    }
    // Exception routes
    const exceptionRoutes = data.exceptionRoutes as any[] | undefined;
    if (exceptionRoutes && exceptionRoutes.length > 0) {
      lines.push('    exceptionRouting:');
      for (const r of exceptionRoutes) {
        if (r.isDefault) { lines.push(`      - default: true`); lines.push(`        to: ${r.to}`); }
        else { lines.push(`      - expr: "${r.expr}"`); lines.push(`        to: ${r.to}`); }
      }
    }

    // Candidate groups
    const groups = data.candidateGroups as string[] | undefined;
    if (groups && groups.length > 0) {
      lines.push(`    candidateGroups: [${groups.map(g => `"${g}"`).join(', ')}]`);
    }
  }

  lines.push('transitions:');
  for (const edge of edges) {
    const edgeData = edge.data as Record<string, unknown> | undefined;
    const label = edgeData?.label as string | undefined;

    // Skip edges from gateways that have conditions on the node
    const sourceNode = nodes.find(n => n.id === edge.source);
    const sourceData = sourceNode?.data as Record<string, unknown> | undefined;
    const hasNodeConditions = sourceData?.conditions &&
      (sourceData.conditions as ConditionItem[]).length > 0 &&
      (sourceNode!.type === 'exclusiveGateway' || sourceNode!.type === 'inclusiveGateway');

    if (hasNodeConditions) continue;

    lines.push(`  - from: ${edge.source}`);
    lines.push(`    to: ${edge.target}`);
    if (label) {
      if (label === 'default') {
        lines.push(`    type: default`);
      } else {
        lines.push(`    type: conditional`);
        lines.push(`    expr: "${label}"`);
      }
    }
  }

  return lines.join('\n');
}
