import type { Node, Edge } from '@xyflow/react';
import { parse as yamlParse } from 'yaml';

/**
 * Parse a workflow YAML string back into ReactFlow nodes and edges.
 * Inverse of graphToYaml — used for YAML import.
 *
 * Uses the `yaml` library (full YAML support), so nested structures such as
 * gateway conditions, HTTP headers/body, retry config and CallActivity
 * mappings round-trip correctly.
 */
export function yamlToGraph(yaml: string): { name: string; nodes: Node[]; edges: Edge[] } {
  const doc = yamlParse(yaml) as Record<string, unknown> | null;
  const nodes: Node[] = [];
  const edges: Edge[] = [];

  if (!doc || typeof doc !== 'object') return { name: 'Imported', nodes, edges };

  const name = (typeof doc.name === 'string' && doc.name) || (typeof doc.id === 'string' ? doc.id : 'Imported');
  const rawNodes = Array.isArray(doc.nodes) ? doc.nodes : [];
  const rawTransitions = Array.isArray(doc.transitions) ? doc.transitions : [];

  let nodeCounter = 0;
  let edgeCounter = 0;

  for (const raw of rawNodes) {
    if (!raw || typeof raw !== 'object') continue;
    const cur = raw as Record<string, unknown>;
    const id = (typeof cur.id === 'string' && cur.id) || `node_${++nodeCounter}`;
    const type = (typeof cur.type === 'string' && cur.type) || 'userTask';
    const nameVal = typeof cur.name === 'string' ? cur.name : undefined;

    const node: Node = {
      id,
      type,
      position: { x: 0, y: 0 }, // positioned later by layoutNodes()
      data: { name: nameVal || type } as Record<string, unknown>,
    };
    const data = node.data as Record<string, unknown>;

    // Simple scalar properties
    for (const prop of ['assignee', 'handlerClass', 'dynamicRouter'] as const) {
      if (cur[prop] !== undefined && cur[prop] !== null) data[prop] = cur[prop];
    }
    if (cur.httpMode === true) data.httpMode = true;
    if (typeof cur.url === 'string') data.url = cur.url;
    if (typeof cur.method === 'string') data.method = cur.method;
    if (typeof cur.body === 'string') data.body = cur.body;
    if (typeof cur.boundaryTimer === 'string') data.boundaryTimer = cur.boundaryTimer;
    if (typeof cur.duration === 'string') data.duration = cur.duration;
    if (typeof cur.until === 'string') data.deadline = cur.until;
    if (Array.isArray(cur.candidateGroups)) data.candidateGroups = cur.candidateGroups.map(String);

    // HTTP headers map → headerEntries [{key, value}]
    const headers = cur.headers;
    if (headers && typeof headers === 'object' && !Array.isArray(headers)) {
      data.headerEntries = Object.entries(headers as Record<string, unknown>)
        .map(([key, value]) => ({ key, value: String(value) }));
    }

    // Body JSON string → paramEntries [{key, value}] (export rebuilds body from these)
    if (typeof cur.body === 'string') {
      try {
        const parsed = JSON.parse(cur.body);
        if (parsed && typeof parsed === 'object' && !Array.isArray(parsed)) {
          data.paramEntries = Object.entries(parsed as Record<string, unknown>)
            .map(([key, value]) => ({ key, value: String(value) }));
        }
      } catch {
        // Non-JSON body — keep the raw string in data.body only.
      }
    }

    // ServiceTask GET/DELETE query string → paramEntries
    if (type === 'serviceTask' && typeof cur.url === 'string') {
      const method = (typeof cur.method === 'string' && cur.method) || 'POST';
      if (['GET', 'DELETE'].includes(method)) {
        const { url, params } = splitQueryParams(cur.url);
        data.url = url;
        if (params.length > 0 && !data.paramEntries) data.paramEntries = params;
      }
    }

    // Gateway conditions (node-level branches) → data.conditions + visual edges
    if (Array.isArray(cur.conditions)) {
      const conditions = cur.conditions.filter(isRoutingItem).map(c => ({
        expr: typeof (c as Record<string, unknown>).expr === 'string' ? (c as Record<string, unknown>).expr as string : '',
        to: String((c as Record<string, unknown>).to ?? ''),
        isDefault: (c as Record<string, unknown>).default === true,
      }));
      if (conditions.length > 0) {
        data.conditions = conditions;
        for (const c of conditions) {
          if (!c.to) continue;
          edges.push(makeEdge(`edge_${id}_${c.to}_cond${++edgeCounter}`, id, c.to,
            { edgeType: c.isDefault ? 'default' : 'conditional', ...(c.isDefault ? { isDefault: true } : c.expr ? { expr: c.expr } : {}) }));
        }
      }
    }

    // Retry config — nested retry: {maxAttempts, ...} (also accept legacy flat keys)
    const retry = cur.retry as Record<string, unknown> | undefined;
    const maxAttempts = (retry ? retry.maxAttempts : cur.maxAttempts);
    const delayMs = (retry ? retry.delayMs : cur.delayMs);
    const backoff = (retry ? retry.backoffMultiplier : cur.backoffMultiplier);
    if (typeof maxAttempts === 'number') data.retryMaxAttempts = maxAttempts;
    if (typeof delayMs === 'number') data.retryDelayMs = delayMs;
    if (typeof backoff === 'number') data.retryBackoff = backoff;
    if (retry && Array.isArray(retry.retryOn)) {
      data.retryOn = retry.retryOn.filter(isRoutingItem)
        .map(r => ({ expr: String((r as Record<string, unknown>).expr ?? '') }));
    }

    // Result / exception routing → data.resultRoutes / data.exceptionRoutes
    if (Array.isArray(cur.resultRouting)) {
      data.resultRoutes = cur.resultRouting.filter(isRoutingItem).map(toRouteItem);
    }
    if (Array.isArray(cur.exceptionRouting)) {
      data.exceptionRoutes = cur.exceptionRouting.filter(isRoutingItem).map(toRouteItem);
    }

    // CallActivity
    if (type === 'callActivity') {
      if (cur.calledId !== undefined) data.calledId = cur.calledId;
      if (cur.calledVersion !== undefined && cur.calledVersion !== null) data.calledVersion = cur.calledVersion;
      if (cur.async === true) data.async = true;
      if (Array.isArray(cur.inputMapping)) data.inputMapping = cur.inputMapping.filter(isMappingItem).map(toMappingItem);
      if (Array.isArray(cur.outputMapping)) data.outputMapping = cur.outputMapping.filter(isMappingItem).map(toMappingItem);
    }

    nodes.push(node);
  }

  // Transitions → edges (skip targets already covered by node-level conditions)
  const conditionTargets = new Set(
    nodes.flatMap(n => ((n.data as Record<string, unknown>).conditions as Array<{ to?: string }> | undefined) || [])
      .map(c => c.to).filter(Boolean) as string[]
  );

  for (const raw of rawTransitions) {
    if (!raw || typeof raw !== 'object') continue;
    const cur = raw as Record<string, unknown>;
    const from = typeof cur.from === 'string' ? cur.from : undefined;
    const to = typeof cur.to === 'string' ? cur.to : undefined;
    if (!from || !to) continue;

    const edgeType = (typeof cur.type === 'string' && cur.type) || 'direct';
    const isDefault = cur.default === true;
    const expr = typeof cur.expr === 'string' ? cur.expr : undefined;
    const data: Record<string, unknown> = { edgeType };
    if (isDefault) data.isDefault = true;
    else if (expr) data.expr = expr;

    const dedupe = conditionTargets.has(to) && edgeType === 'conditional' ? `d${++edgeCounter}` : '';
    edges.push(makeEdge(`edge_${from}_${to}_${dedupe || ++edgeCounter}`, from, to, data));
  }

  // Layout nodes based on flow topology
  layoutNodes(nodes, edges);

  return { name, nodes, edges };
}

function makeEdge(id: string, source: string, target: string, data: Record<string, unknown>): Edge {
  return { id, source, target, type: 'smoothstep', data } as Edge;
}

interface RouteLike { expr?: string; to?: string; default?: boolean; }

function isRoutingItem(v: unknown): v is Record<string, unknown> {
  return !!v && typeof v === 'object' && !Array.isArray(v);
}

function toRouteItem(c: Record<string, unknown>) {
  return {
    expr: typeof c.expr === 'string' ? c.expr : '',
    to: String(c.to ?? ''),
    isDefault: c.default === true,
  };
}

function isMappingItem(v: unknown): v is Record<string, unknown> {
  return isRoutingItem(v);
}

function toMappingItem(m: Record<string, unknown>) {
  return {
    from: String(m.from ?? ''),
    to: String(m.to ?? ''),
    ...(typeof m.expr === 'string' && m.expr ? { expr: m.expr } : {}),
  };
}

/** Split "?a=1&b=2" from a URL into param entries. */
function splitQueryParams(url: string): { url: string; params: Array<{ key: string; value: string }> } {
  const qIdx = url.indexOf('?');
  if (qIdx < 0) return { url, params: [] };
  const base = url.substring(0, qIdx);
  const params: Array<{ key: string; value: string }> = [];
  for (const pair of url.substring(qIdx + 1).split('&')) {
    if (!pair) continue;
    const eq = pair.indexOf('=');
    const k = eq < 0 ? pair : pair.substring(0, eq);
    const v = eq < 0 ? '' : pair.substring(eq + 1);
    try {
      params.push({ key: decodeURIComponent(k), value: decodeURIComponent(v) });
    } catch {
      params.push({ key: k, value: v });
    }
  }
  return { url: base, params };
}

/** Layered graph layout: topological distance from start nodes → X, center-spread → Y */
function layoutNodes(nodes: Node[], edges: Edge[]) {
  const LAYER_GAP = 220;   // horizontal spacing between layers
  const NODE_GAP = 100;    // vertical spacing between nodes in same layer
  const START_X = 50;
  const START_Y = 200;

  // Build adjacency for BFS
  const outMap = new Map<string, string[]>();
  const inDegree = new Map<string, number>();
  for (const n of nodes) {
    outMap.set(n.id, []);
    inDegree.set(n.id, 0);
  }
  for (const e of edges) {
    outMap.get(e.source)?.push(e.target);
    inDegree.set(e.target, (inDegree.get(e.target) || 0) + 1);
  }

  // Find start nodes (no incoming edges) — layer 0
  const layers = new Map<string, number>();
  const queue: string[] = [];
  for (const n of nodes) {
    if (inDegree.get(n.id) === 0) {
      layers.set(n.id, 0);
      queue.push(n.id);
    }
  }

  // If all nodes have incoming edges (cycle), pick first node as layer 0
  if (queue.length === 0 && nodes.length > 0) {
    layers.set(nodes[0].id, 0);
    queue.push(nodes[0].id);
  }

  // BFS to assign layers
  while (queue.length > 0) {
    const cur = queue.shift()!;
    const curLayer = layers.get(cur)!;
    for (const next of outMap.get(cur) || []) {
      const newLayer = curLayer + 1;
      const existing = layers.get(next);
      if (existing === undefined || newLayer > existing) {
        layers.set(next, newLayer);
        // Re-queue if layer changed (handles diamond dependencies)
        if (existing === undefined || !queue.includes(next)) queue.push(next);
      }
    }
  }

  // Assign layers to any unvisited nodes (disconnected)
  let maxLayer = 0;
  for (const n of nodes) {
    const l = layers.get(n.id);
    if (l !== undefined && l > maxLayer) maxLayer = l;
  }
  for (const n of nodes) {
    if (!layers.has(n.id)) layers.set(n.id, maxLayer + 1);
  }

  // Group nodes by layer
  const layerGroups = new Map<number, Node[]>();
  for (const n of nodes) {
    const l = layers.get(n.id) || 0;
    if (!layerGroups.has(l)) layerGroups.set(l, []);
    layerGroups.get(l)!.push(n);
  }

  // Position nodes: layer → X (topological distance), index → Y (centered spread)
  for (const [layer, layerNodes] of layerGroups) {
    const totalHeight = (layerNodes.length - 1) * NODE_GAP;
    layerNodes.forEach((n, i) => {
      n.position = {
        x: START_X + layer * LAYER_GAP,
        y: START_Y + i * NODE_GAP - totalHeight / 2,
      };
    });
  }
}
