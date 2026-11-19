import type { Node, Edge } from '@xyflow/react';
import { stringify as yamlStringify } from 'yaml';

interface ConditionItem { expr: string; to: string; isDefault: boolean; }
type HeaderEntry = { key: string; value: string };

/** Build a JSON body string from param entries (same semantics as before). */
function buildBody(params: HeaderEntry[]): string | undefined {
  const bodyObj: Record<string, string> = {};
  for (const p of params) { if (p.key) bodyObj[p.key] = p.value; }
  return Object.keys(bodyObj).length > 0 ? JSON.stringify(bodyObj) : undefined;
}

/** Convert header entries to a plain object (undefined when empty). */
function buildHeaders(entries: HeaderEntry[]): Record<string, string> | undefined {
  const map: Record<string, string> = {};
  for (const h of entries) { if (h.key) map[h.key] = h.value; }
  return Object.keys(map).length > 0 ? map : undefined;
}

/** Split "?a=1&b=2" from a URL into param entries (GET/DELETE query string). */
function splitQueryParams(url: string): { url: string; params: HeaderEntry[] } {
  const qIdx = url.indexOf('?');
  if (qIdx < 0) return { url, params: [] };
  const base = url.substring(0, qIdx);
  const params: HeaderEntry[] = [];
  for (const pair of url.substring(qIdx + 1).split('&')) {
    if (!pair) continue;
    const eq = pair.indexOf('=');
    const k = eq < 0 ? pair : pair.substring(0, eq);
    const v = eq < 0 ? '' : pair.substring(eq + 1);
    params.push({ key: decodeURIComponent(k), value: decodeURIComponent(v) });
  }
  return { url: base, params };
}

interface RoutingItem { expr?: string; to?: string; isDefault?: boolean; }

function routingToYaml(items: RoutingItem[] | undefined): Array<Record<string, unknown>> | undefined {
  if (!items || items.length === 0) return undefined;
  return items.map(r => r.isDefault ? { default: true, to: r.to } : { expr: r.expr, to: r.to });
}

function conditionsToYaml(items: ConditionItem[] | undefined): Array<Record<string, unknown>> | undefined {
  if (!items || items.length === 0) return undefined;
  return items.map(c => c.isDefault ? { default: true, to: c.to } : { expr: c.expr, to: c.to });
}

export function graphToYaml(nodes: Node[], edges: Edge[], defId: string, defName: string, version = 1): string {
  const yamlNodes: Record<string, unknown>[] = [];

  for (const node of nodes) {
    const data = node.data as Record<string, unknown>;
    const n: Record<string, unknown> = { id: node.id, type: node.type };
    if (data.name) n.name = data.name as string;

    if (node.type === 'exclusiveGateway' || node.type === 'inclusiveGateway') {
      const conds = conditionsToYaml(data.conditions as ConditionItem[] | undefined);
      if (conds) n.conditions = conds;
    }

    if (node.type === 'userTask') {
      if (data.assignee) n.assignee = data.assignee as string;
      if (data.boundaryTimer) n.boundaryTimer = String(data.boundaryTimer);
    }

    // UserTask / ServiceTask HTTP mode (shared fields)
    if ((node.type === 'userTask' || node.type === 'serviceTask') && data.httpMode) {
      n.httpMode = true;
      let url = data.url as string | undefined;
      const method = (data.method as string) || 'POST';
      const headerEntries = (data.headerEntries as HeaderEntry[]) || [];
      const paramEntries = (data.paramEntries as HeaderEntry[]) || [];

      if (node.type === 'serviceTask' && url && ['GET', 'DELETE'].includes(method) && paramEntries.length > 0) {
        // Fold params into the query string (ServiceTask GET/DELETE semantics)
        const qs = paramEntries.filter(p => p.key)
          .map(p => `${encodeURIComponent(p.key)}=${encodeURIComponent(p.value)}`).join('&');
        url = url + (url.includes('?') ? '&' : '?') + qs;
      }
      if (url) n.url = url;
      if (data.method) n.method = method;
      const headers = buildHeaders(headerEntries);
      if (headers) n.headers = headers;
      if (['POST', 'PUT', 'PATCH'].includes(method)) {
        const body = buildBody(paramEntries);
        if (body) n.body = body;
      }
    }

    if (node.type === 'serviceTask') {
      if (!data.httpMode && data.handlerClass) n.handlerClass = data.handlerClass as string;
      // Retry
      const maxAttempts = data.retryMaxAttempts as number | undefined;
      if (maxAttempts && maxAttempts > 1) {
        const retry: Record<string, unknown> = {
          maxAttempts,
          delayMs: (data.retryDelayMs as number) || 1000,
          backoffMultiplier: (data.retryBackoff as number) || 2,
        };
        const retryOn = data.retryOn as Array<{ expr?: string; className?: string }> | undefined;
        if (retryOn && retryOn.length > 0) {
          retry.retryOn = retryOn.filter(r => r.expr).map(r => ({ expr: r.expr }));
        }
        n.retry = retry;
      }
      const rr = routingToYaml(data.resultRoutes as RoutingItem[] | undefined);
      if (rr) n.resultRouting = rr;
      const er = routingToYaml(data.exceptionRoutes as RoutingItem[] | undefined);
      if (er) n.exceptionRouting = er;
    }

    if (node.type === 'aiTask') {
      if (data.mode) n.mode = data.mode as string;
      if (data.prompt) n.prompt = data.prompt as string;
      if (data.outputVar) n.outputVar = data.outputVar as string;
      if (Array.isArray(data.options) && data.options.length > 0) n.options = data.options;
      // Retry for AI tasks — same shape as service tasks
      const maxAttempts = data.retryMaxAttempts as number | undefined;
      if (maxAttempts && maxAttempts > 1) {
        n.retry = {
          maxAttempts,
          delayMs: (data.retryDelayMs as number) || 1000,
          backoffMultiplier: (data.retryBackoff as number) || 2,
        };
      }
    }

    if (node.type === 'callActivity') {
      if (data.calledId) n.calledId = data.calledId as string;
      if (data.calledVersion) n.calledVersion = data.calledVersion as number;
      if (data.async) n.async = true;
      const toMappings = (m: unknown) => {
        const arr = m as Array<{ from: string; to: string; expr?: string }> | undefined;
        if (!arr || arr.length === 0) return undefined;
        return arr.map(mm => mm.expr ? { from: mm.from, to: mm.to, expr: mm.expr } : { from: mm.from, to: mm.to });
      };
      const im = toMappings(data.inputMapping);
      if (im) n.inputMapping = im;
      const om = toMappings(data.outputMapping);
      if (om) n.outputMapping = om;
    }

    if (node.type === 'userTask' || node.type === 'callActivity') {
      if (data.dynamicRouter) n.dynamicRouter = data.dynamicRouter as string;
    }
    if (node.type === 'timer') {
      if (data.duration) n.duration = String(data.duration);
      if (data.deadline) n.until = String(data.deadline);
    }

    const groups = data.candidateGroups as string[] | undefined;
    if (node.type === 'userTask' && groups && groups.length > 0) {
      n.candidateGroups = [...groups];
    }

    yamlNodes.push(n);
  }

  const yamlTransitions: Record<string, unknown>[] = [];
  for (const edge of edges) {
    const sourceNode = nodes.find(nd => nd.id === edge.source);
    const sourceData = sourceNode?.data as Record<string, unknown> | undefined;
    // Gateway nodes with node-level conditions export their branches from
    // conditions (not edges) — skip those edges here (restored on import).
    const hasNodeConditions = sourceData?.conditions &&
      (sourceData.conditions as ConditionItem[]).length > 0 &&
      (sourceNode!.type === 'exclusiveGateway' || sourceNode!.type === 'inclusiveGateway');
    if (hasNodeConditions) continue;

    const edgeData = (edge.data ?? {}) as { edgeType?: string; expr?: string; isDefault?: boolean };
    const edgeType = edgeData.edgeType || 'direct';
    const t: Record<string, unknown> = { from: edge.source, to: edge.target };
    if (edgeType !== 'direct') {
      if (edgeData.isDefault) t.default = true;
      else if (edgeData.expr) t.expr = edgeData.expr;
      t.type = edgeType;
    }
    yamlTransitions.push(t);
  }

  const doc = { id: defId, name: defName, version, nodes: yamlNodes, transitions: yamlTransitions };
  return yamlStringify(doc, { lineWidth: 0 });
}
