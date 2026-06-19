import type { Node, Edge } from '@xyflow/react';

/**
 * Parse a workflow YAML string back into ReactFlow nodes and edges.
 * Inverse of graphToYaml — used for YAML import.
 */
export function yamlToGraph(yaml: string): { name: string; nodes: Node[]; edges: Edge[] } {
  const lines = yaml.split('\n');
  let name = 'Imported';
  const nodes: Node[] = [];
  const edges: Edge[] = [];
  let nodeIdCounter = 0;

  let section: 'header' | 'nodes' | 'transitions' = 'header';
  let current: Record<string, unknown> | null = null;

  // Flush the current item (node or edge) before starting a new one
  function flush() {
    if (!current) return;
    if (section === 'nodes') {
      flushNode(current, nodes, ++nodeIdCounter);
    } else if (section === 'transitions') {
      flushEdge(current, edges);
    }
    current = null;
  }

  for (let i = 0; i < lines.length; i++) {
    const line = lines[i];
    const indent = line.search(/\S/);
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith('#')) continue;

    // Top-level keys — flush previous item before switching sections
    if (indent === 0) {
      if (section === 'nodes' || section === 'transitions') flush();
      if (trimmed.startsWith('id:') && section === 'header') {
        name = trimmed.substring(3).trim();
      } else if (trimmed === 'nodes:') {
        section = 'nodes';
      } else if (trimmed === 'transitions:') {
        section = 'transitions';
      }
      continue;
    }

    // New list item (indent === 2, starts with "- ")
    if (indent === 2 && trimmed.startsWith('- ')) {
      flush(); // save previous item

      if (section === 'nodes' && trimmed.startsWith('- id:')) {
        current = { id: trimmed.substring(5).trim() };
      } else if (section === 'transitions' && trimmed.startsWith('- from:')) {
        current = { from: trimmed.substring(7).trim() };
      }
      continue;
    }

    // Nested property (indent >= 4) — add to current item
    if (current && indent >= 4) {
      const kv = parseYamlLine(trimmed);
      if (kv) current[kv.key] = kv.value;
    }
  }

  // Flush the last item
  flush();

  return { name, nodes, edges };
}

function parseYamlLine(trimmed: string): { key: string; value: unknown } | null {
  const colon = trimmed.indexOf(':');
  if (colon < 0) return null;
  const key = trimmed.substring(0, colon).trim();
  const raw = trimmed.substring(colon + 1).trim();
  let value: unknown = raw;

  // Unquote strings
  if ((raw.startsWith("'") && raw.endsWith("'")) || (raw.startsWith('"') && raw.endsWith('"'))) {
    value = raw.slice(1, -1);
  } else if (raw === 'true') {
    value = true;
  } else if (raw === 'false') {
    value = false;
  } else if (/^-?\d+$/.test(raw)) {
    value = parseInt(raw);
  }

  return { key, value };
}

function flushNode(cur: Record<string, unknown>, nodes: Node[], counter: number) {
  const id = (cur.id as string) || `node_${counter}`;
  const type = (cur.type as string) || 'userTask';
  const name = cur.name as string | undefined;

  const node: Node = {
    id,
    type,
    position: { x: 50 + nodes.length * 200, y: 150 + (nodes.length % 3) * 150 },
    data: { name: name || type },
  };

  // Copy known properties to node data
  for (const prop of ['assignee', 'handlerClass', 'url', 'method', 'duration',
    'boundaryTimer', 'dynamicRouter']) {
    if (cur[prop] !== undefined) (node.data as any)[prop] = cur[prop];
  }
  if (cur.httpMode) (node.data as any).httpMode = true;
  if (cur.until) (node.data as any).deadline = cur.until;
  if (Array.isArray(cur.candidateGroups)) (node.data as any).candidateGroups = cur.candidateGroups;

  // retry config (nested under retry: in YAML, becomes flat keys in our simple parser)
  if (typeof cur.maxAttempts === 'number') (node.data as any).retryMaxAttempts = cur.maxAttempts;
  if (typeof cur.delayMs === 'number') (node.data as any).retryDelayMs = cur.delayMs;
  if (typeof cur.backoffMultiplier === 'number') (node.data as any).retryBackoff = cur.backoffMultiplier;

  nodes.push(node);
}

function flushEdge(cur: Record<string, unknown>, edges: Edge[]) {
  const from = cur.from as string;
  const to = cur.to as string;
  if (!from || !to) return;

  const edgeType = (cur.type as string) || 'direct';
  const expr = cur.expr as string | undefined;
  const isDefault = cur.default === true;

  const edge: Edge = {
    id: `edge_${from}_${to}_${Date.now()}`,
    source: from,
    target: to,
    type: 'smoothstep',
    data: { edgeType, ...(expr ? { expr } : {}), ...(isDefault ? { isDefault: true } : {}) },
  };

  edges.push(edge);
}
