import type { Node, Edge } from '@xyflow/react';

/** Quote for YAML: single-quote preferred, escape internal single quotes */
function y(s: string): string {
  if (!s.includes("'")) return `'${s}'`;
  if (!s.includes('"')) return `"${s}"`;
  return `"${s.replace(/"/g, '\\"')}"`;
}

interface ConditionItem { expr: string; to: string; isDefault: boolean; }

export function graphToYaml(nodes: Node[], edges: Edge[], name = 'workflow', version = 1): string {
  const lines: string[] = [];
  lines.push(`id: ${name}`);
  lines.push(`version: ${version}`);
  lines.push('nodes:');

  for (const node of nodes) {
    const data = node.data as Record<string, unknown>;
    lines.push(`  - id: ${node.id}`);
    lines.push(`    type: ${node.type}`);
    if (data.name) lines.push(`    name: ${y(data.name as string)}`);

    if (node.type === 'exclusiveGateway' || node.type === 'inclusiveGateway') {
      const conds = data.conditions as ConditionItem[] | undefined;
      if (conds && conds.length > 0) {
        lines.push('    conditions:');
        for (const c of conds) {
          if (c.isDefault) { lines.push(`      - default: true`); lines.push(`        to: ${c.to}`); }
          else { lines.push(`      - expr: ${y(c.expr)}`); lines.push(`        to: ${c.to}`); }
        }
      }
    }

    if (data.assignee && node.type === 'userTask') {
      lines.push(`    assignee: ${y(data.assignee as string)}`);
    }
    if (data.boundaryTimer && node.type === 'userTask') {
      lines.push(`    boundaryTimer: "${data.boundaryTimer}"`);
    }

    if (node.type === 'serviceTask') {
      const httpMode = data.httpMode as boolean;
      if (httpMode) lines.push('    httpMode: true');
      if (data.handlerClass && !httpMode) lines.push(`    handlerClass: ${y(data.handlerClass as string)}`);
      if (data.url && httpMode) {
        const paramEntries = (data.paramEntries as Array<{key:string;value:string}>) || [];
        const method = (data.method as string) || 'POST';
        const urlStr = (data.url as string) || '';
        if (['GET','DELETE'].includes(method) && paramEntries.length > 0) {
          const qs = paramEntries.filter(p => p.key).map(p => `${encodeURIComponent(p.key)}=${encodeURIComponent(p.value)}`).join('&');
          lines.push(`    url: ${y(urlStr + '?' + qs)}`);
        } else { lines.push(`    url: ${y(urlStr)}`); }
        if (data.method) lines.push(`    method: ${method}`);
        const headerEntries = (data.headerEntries as Array<{key:string;value:string}>) || [];
        if (headerEntries.length > 0) {
          lines.push('    headers:');
          for (const h of headerEntries) { if (h.key) lines.push(`      ${h.key}: ${y(h.value)}`); }
        }
        if (['POST','PUT','PATCH'].includes(method) && paramEntries.length > 0) {
          const bodyObj: Record<string,string> = {};
          for (const p of paramEntries) { if (p.key) bodyObj[p.key] = p.value; }
          if (Object.keys(bodyObj).length > 0) lines.push(`    body: '${JSON.stringify(bodyObj)}'`);
        }
      }
    }

    // Retry
    if (node.type === 'serviceTask' && (data.retryMaxAttempts as number) && (data.retryMaxAttempts as number) > 1) {
      lines.push('    retry:');
      lines.push(`      maxAttempts: ${data.retryMaxAttempts || 3}`);
      lines.push(`      delayMs: ${data.retryDelayMs || 1000}`);
      lines.push(`      backoffMultiplier: ${data.retryBackoff || 2}`);
      const retryOn = data.retryOn as any[] | undefined;
      if (retryOn && retryOn.length > 0) {
        lines.push('      retryOn:');
        for (const r of retryOn) { if (r.expr) lines.push(`        - expr: ${y(r.expr)}`); }
      }
    }
    // Result routing
    const rrs = data.resultRoutes as any[] | undefined;
    if (rrs && rrs.length > 0) {
      lines.push('    resultRouting:');
      for (const r of rrs) {
        if (r.isDefault) { lines.push('      - default: true'); lines.push(`        to: ${r.to}`); }
        else { lines.push(`      - expr: ${y(r.expr)}`); lines.push(`        to: ${r.to}`); }
      }
    }
    // Exception routing
    const ers = data.exceptionRoutes as any[] | undefined;
    if (ers && ers.length > 0) {
      lines.push('    exceptionRouting:');
      for (const r of ers) {
        if (r.isDefault) { lines.push('      - default: true'); lines.push(`        to: ${r.to}`); }
        else { lines.push(`      - expr: ${y(r.expr)}`); lines.push(`        to: ${r.to}`); }
      }
    }

    if (data.duration && node.type === 'timer') {
      lines.push(`    duration: "${data.duration}"`);
    }
    if (data.deadline && node.type === 'timer') {
      lines.push(`    until: "${data.deadline}"`);
    }

    const groups = data.candidateGroups as string[] | undefined;
    if (groups && groups.length > 0) {
      lines.push(`    candidateGroups: [${groups.map(g => y(g)).join(', ')}]`);
    }
  }

  lines.push('transitions:');
  for (const edge of edges) {
    const sourceNode = nodes.find(n => n.id === edge.source);
    const sourceData = sourceNode?.data as Record<string,unknown> | undefined;
    const hasNodeConditions = sourceData?.conditions &&
      (sourceData.conditions as ConditionItem[]).length > 0 &&
      (sourceNode!.type === 'exclusiveGateway' || sourceNode!.type === 'inclusiveGateway');
    if (hasNodeConditions) continue;
    const edgeType = (edge.data as any)?.edgeType || 'direct';
    const edgeExpr = (edge.data as any)?.expr;
    const isDefault = (edge.data as any)?.isDefault;

    lines.push(`  - from: ${edge.source}`);
    lines.push(`    to: ${edge.target}`);

    if (edgeType !== 'direct') {
      if (isDefault) lines.push('    default: true');
      else if (edgeExpr) lines.push(`    expr: ${y(edgeExpr)}`);
      lines.push(`    type: ${edgeType}`);
    }
  }

  return lines.join('\n');
}
