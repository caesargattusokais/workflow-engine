import { MarkerType } from '@xyflow/react';

/**
 * Semantic edge styling. Edges carry their meaning in `data.edgeType` (set by
 * yamlToGraph / the server graph mapper / the edge context menu), but ReactFlow
 * does not derive `style` from `data`. FlowCanvas normalizes every edge through
 * `styleEdgeForType` so imported templates, loaded drafts and hand-drawn edges
 * all render with the same color language.
 */
export type EdgeType = 'direct' | 'conditional' | 'default' | 'result' | 'exception' | 'timeout';

export interface EdgeStyleDef {
  stroke: string;
  strokeWidth: number;
  strokeDasharray?: string;
  label: string;      // short human label for the legend
  labelZh: string;
}

export const EDGE_STYLES: Record<EdgeType, EdgeStyleDef> = {
  direct:     { stroke: '#7d8ba3', strokeWidth: 2, label: 'Direct',     labelZh: '顺序' },
  conditional:{ stroke: '#f5b301', strokeWidth: 2.2, label: 'Condition',labelZh: '条件' },
  default:    { stroke: '#9aa4b8', strokeWidth: 2, strokeDasharray: '3,3', label: 'Default', labelZh: '默认' },
  result:     { stroke: '#34d17a', strokeWidth: 2.2, label: 'Result',    labelZh: '结果' },
  exception:  { stroke: '#f2555a', strokeWidth: 2.2, strokeDasharray: '5,4', label: 'Exception', labelZh: '异常' },
  timeout:    { stroke: '#ff8a3d', strokeWidth: 2.2, strokeDasharray: '6,4', label: 'Timeout',  labelZh: '超时' },
};

export function getEdgeStyle(type?: string) {
  const def = EDGE_STYLES[(type as EdgeType) || 'direct'] || EDGE_STYLES.direct;
  const style: { stroke: string; strokeWidth: number; strokeDasharray?: string } = {
    stroke: def.stroke,
    strokeWidth: def.strokeWidth,
  };
  if (def.strokeDasharray) style.strokeDasharray = def.strokeDasharray;
  return style;
}

export function getEdgeMarker(type?: string) {
  return { type: MarkerType.ArrowClosed, color: getEdgeStyle(type).stroke, width: 16, height: 16 };
}

/** Return a copy of `edge` whose style + arrow always match its semantic type. */
export function styleEdgeForType<T extends { data?: Record<string, unknown>; style?: any; markerEnd?: any }>(edge: T): T {
  const type = (edge.data?.edgeType as string) || 'direct';
  return {
    ...edge,
    style: { ...edge.style, ...getEdgeStyle(type) },
    markerEnd: getEdgeMarker(type),
  };
}

/* ── Edge shape (visual) ────────────────────────────────────────────────
 * Orthogonal to `edgeType` (semantic). Stored in `data.edgeShape` so it
 * survives draft save/load; ReactFlow's `edge.type` is derived from it.
 * Values are ReactFlow's built-in edge type names — no custom edge needed.
 */
export type EdgeShape = 'smoothstep' | 'default' | 'straight' | 'step';

export const EDGE_SHAPES: Record<EdgeShape, { label: string; labelZh: string; icon: string }> = {
  smoothstep: { label: 'Rounded', labelZh: '圆角折线', icon: 'M2 14 h5 a3 3 0 0 0 3 -3 v-6 a3 3 0 0 1 3 -3 h5' },
  default:    { label: 'Curve',    labelZh: '曲线',     icon: 'M2 14 C 9 14, 9 2, 20 2' },
  straight:   { label: 'Straight', labelZh: '直线',     icon: 'M2 14 L20 2' },
  step:       { label: 'Elbow',    labelZh: '直角折线', icon: 'M2 14 h8 v-12 h10' },
};

export const DEFAULT_EDGE_SHAPE: EdgeShape = 'smoothstep';

export function isEdgeShape(v: unknown): v is EdgeShape {
  return typeof v === 'string' && Object.prototype.hasOwnProperty.call(EDGE_SHAPES, v);
}

/** Explicit data.edgeShape wins; otherwise the ReactFlow type, then `fallback`. */
export function getEdgeShape(
  edge: { type?: string; data?: Record<string, unknown> },
  fallback: EdgeShape = DEFAULT_EDGE_SHAPE,
): EdgeShape {
  const explicit = edge.data?.edgeShape;
  if (isEdgeShape(explicit)) return explicit;
  if (isEdgeShape(edge.type)) return edge.type;
  return fallback;
}

/** Return a copy of `edge` whose ReactFlow type + persisted shape are `shape`. */
export function shapeEdge<T extends { type?: string; data?: Record<string, unknown> }>(edge: T, shape: EdgeShape): T {
  return { ...edge, type: shape, data: { ...edge.data, edgeShape: shape } };
}
