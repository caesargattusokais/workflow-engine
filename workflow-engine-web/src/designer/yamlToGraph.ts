import type { Node, Edge } from '@xyflow/react';

/**
 * Parse a workflow YAML string back into ReactFlow nodes and edges.
 * Inverse of graphToYaml — used for YAML import.
 */
export function yamlToGraph(yaml: string): { name: string; nodes: Node[]; edges: Edge[] } {
  const lines = yaml.split('\n');
  let name = 'Imported';
  let version = 1;
  const nodes: Node[] = [];
  const edges: Edge[] = [];
  let nodeIdCounter = 0;

  let section: 'header' | 'nodes' | 'transitions' = 'header';
  let currentNode: Record<string, unknown> | null = null;

  for (let i = 0; i < lines.length; i++) {
    const line = lines[i];
    const indent = line.search(/\S/);
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith('#')) continue;

    // Top-level keys
    if (indent === 0) {
      currentNode = null;
      if (trimmed.startsWith('id:') && section === 'header') {
        name = trimmed.substring(3).trim();
      } else if (trimmed.startsWith('version:')) {
        version = parseInt(trimmed.substring(8).trim()) || 1;
      } else if (trimmed === 'nodes:') {
        section = 'nodes';
      } else if (trimmed === 'transitions:') {
        section = 'transitions';
      }
      continue;
    }

    if (section === 'nodes') {
      if (indent === 2 && trimmed.startsWith('- id:')) {
        // New node
        if (currentNode) flushNode(currentNode, nodes, ++nodeIdCounter);
        currentNode = { id: trimmed.substring(5).trim() };
      } else if (currentNode && indent >= 4) {
        const kv = parseYamlLine(trimmed);
        if (kv) currentNode[kv.key] = kv.value;
        // Handle nested structures (conditions, retry, resultRouting, etc.) as simple string
      }
    }

    if (section === 'transitions') {
      if (indent === 2 && trimmed.startsWith('- from:')) {
        if (currentNode) flushNode(currentNode, nodes, ++nodeIdCounter);
        currentNode = { from: trimmed.substring(7).trim() };
      } else if (currentNode && indent >= 4) {
        const kv = parseYamlLine(trimmed);
        if (kv) currentNode[kv.key] = kv.value;
      } else if (currentNode && trimmed.startsWith('- from:')) {
        // Flush previous edge, start new
        flushEdge(currentNode, edges);
        currentNode = { from: trimmed.substring(7).trim() };
      }
    }
  }

  // Flush last node/edge
  if (section === 'nodes' && currentNode) flushNode(currentNode, nodes, ++nodeIdCounter);
  if (section === 'transitions' && currentNode) flushEdge(currentNode, edges);

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

function flushNode(current: Record<string, unknown>, nodes: Node[], counter: number) {
  const id = (current.id as string) || `node_${counter}`;
  const type = (current.type as string) || 'userTask';
  const name = current.name as string | undefined;

  const node: Node = {
    id,
    type,
    position: { x: 50 + nodes.length * 200, y: 150 + (nodes.length % 3) * 150 },
    data: { name: name || type },
  };

  // Copy known properties to node data
  for (const prop of ['assignee', 'handlerClass', 'url', 'method', 'duration',
    'boundaryTimer', 'dynamicRouter']) {
    if (current[prop] !== undefined) (node.data as any)[prop] = current[prop];
  }
  if (current.httpMode) (node.data as any).httpMode = true;
  if (current.until) (node.data as any).deadline = current.until;

  // candidateGroups
  if (current.candidateGroups) {
    const cg = current.candidateGroups;
    if (Array.isArray(cg)) (node.data as any).candidateGroups = cg;
  }

  // retry config
  if (typeof current.maxAttempts === 'number') (node.data as any).retryMaxAttempts = current.maxAttempts;
  if (typeof current.delayMs === 'number') (node.data as any).retryDelayMs = current.delayMs;
  if (typeof current.backoffMultiplier === 'number') (node.data as any).retryBackoff = current.backoffMultiplier;

  nodes.push(node);
}

function flushEdge(current: Record<string, unknown>, edges: Edge[]) {
  const from = current.from as string;
  const to = current.to as string;
  if (!from || !to) return;

  const edgeType = (current.type as string) || 'direct';
  const expr = current.expr as string | undefined;
  const isDefault = current.default === true;

  const edge: Edge = {
    id: `edge_${from}_${to}_${Date.now()}`,
    source: from,
    target: to,
    type: 'smoothstep',
    data: { edgeType, ...(expr ? { expr } : {}), ...(isDefault ? { isDefault: true } : {}) },
  };

  edges.push(edge);
}
