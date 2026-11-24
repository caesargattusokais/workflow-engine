import { useRef, useEffect, useMemo, memo } from 'react';
import { ReactFlow, Background, BackgroundVariant, MarkerType, type Node, type Edge } from '@xyflow/react';
import '@xyflow/react/dist/style.css';
import { nodeTypes } from '../designer/nodes';
import { useT } from '../i18n';
import EmptyStateArt from '../components/EmptyStateArt';

function edgeStyle(edgeType?: string) {
  switch (edgeType) {
    case 'result': return { stroke: '#22c55e', strokeWidth: 2 };
    case 'exception': return { stroke: '#ef4444', strokeWidth: 2, strokeDasharray: '5,5' };
    case 'timeout': return { stroke: '#f97316', strokeWidth: 2, strokeDasharray: '5,5' };
    case 'conditional': return { stroke: '#e5a50a', strokeWidth: 2 };
    case 'default': return { stroke: '#8b94a8', strokeWidth: 2, strokeDasharray: '3,3' };
    default: return { stroke: '#7d879c', strokeWidth: 2 };
  }
}

/** Memoized — avoids re-render when parent state (defGroups) changes but nodes/edges/error stay the same. */
export default memo(function InstanceFlow({ nodes, edges, error }: { nodes: Node[], edges: Edge[], error?: string }) {
  const { t } = useT();
  const rfRef = useRef<any>(null);
  const nodesKeyRef = useRef<string>('');

  // Build a stable key from node IDs to detect instance switches
  const nodesKey = useMemo(() => nodes.map(n => n.id).sort().join(','), [nodes]);

  // Fit view after nodes change — use rAF to wait for ReactFlow measurements
  useEffect(() => {
    if (nodes.length === 0 || !rfRef.current) return;
    const key = nodesKey;
    // Use requestAnimationFrame + small delay to ensure ReactFlow has measured new nodes
    const raf = requestAnimationFrame(() => {
      const timer = setTimeout(() => {
        // Guard: only fit if nodes haven't changed since scheduling
        if (rfRef.current && nodesKeyRef.current === key) {
          try { rfRef.current.fitView({ duration: 300, padding: 0.2 }); } catch {}
        }
      }, 100);
      // Store cleanup
      nodesKeyRef.current = key;
      return () => clearTimeout(timer);
    });
    return () => cancelAnimationFrame(raf);
  }, [nodesKey]); // eslint-disable-line react-hooks/exhaustive-deps

  const safeEdges = useMemo(() => edges.map(e => ({
    ...e,
    style: edgeStyle((e.data as any)?.edgeType),
    markerEnd: e.markerEnd === 'arrowclosed'
      ? { type: MarkerType.ArrowClosed, color: edgeStyle((e.data as any)?.edgeType).stroke as string }
      : e.markerEnd
      ? { type: MarkerType.ArrowClosed, color: '#7d879c' }
      : undefined,
  })), [edges]);

  // Memoize safeNodes — only rebuild when nodes reference changes
  const safeNodes = useMemo(() => nodes.map(n => ({
    id: n.id,
    type: n.type,
    position: { x: n.position?.x ?? 200, y: n.position?.y ?? 50 },
    data: n.data || {},
    style: n.data?.active
      ? { border: '2px solid #5e6ad2', boxShadow: '0 0 16px rgba(94,106,210,0.45)' }
      : n.data?.status === 'done' ? { opacity: 0.4 } : {}
  })), [nodes]);

  if (error) {
    return (
      <div className="flex-1 flex items-center justify-center" style={{ minHeight: 300, background: 'radial-gradient(ellipse at 50% 35%, #1b2130 0%, #171c27 55%, #12161e 100%)' }}>
        <div className="text-center">
          <div className="text-red-400 text-sm mb-2">Failed to load flow</div>
          <div className="text-gray-500 text-xs">{error}</div>
        </div>
      </div>
    );
  }

  if (!nodes || nodes.length === 0) {
    return (
      <div className="flex-1 flex flex-col" style={{ minHeight: 300, background: 'radial-gradient(circle at 50% 42%, rgba(109,116,232,0.09), transparent 55%), radial-gradient(ellipse at 50% 35%, #1b2130 0%, #171c27 55%, #12161e 100%)' }}>
        <EmptyStateArt
          art="flow"
          title="Select an instance to view its flow"
          hint="从左侧实例列表选择一个，实时查看它走过的节点路径与当前停留位置。"
        />
      </div>
    );
  }

  return (
    <div className="flex-1 h-full" style={{ minHeight: 300, background: 'radial-gradient(ellipse at 50% 35%, #1b2130 0%, #171c27 55%, #12161e 100%)' }}>
      <ReactFlow nodes={safeNodes} edges={safeEdges} nodeTypes={nodeTypes}
        onInit={(rf: any) => { rfRef.current = rf; }}
        nodesDraggable={false} nodesConnectable={false} elementsSelectable={false}>
        <Background variant={BackgroundVariant.Dots} color="#384159" gap={22} size={1.3} />
      </ReactFlow>
    </div>
  );
});
