import { useRef, useEffect } from 'react';
import { ReactFlow, Background, type Node, type Edge } from '@xyflow/react';
import '@xyflow/react/dist/style.css';
import { nodeTypes } from '../designer/nodes';
import { useT } from '../i18n';

export default function InstanceFlow({ nodes, edges, error }: { nodes: Node[], edges: Edge[], error?: string }) {
  const { t } = useT();
  const rfRef = useRef<any>(null);

  // Fit view after nodes change
  useEffect(() => {
    if (nodes.length > 0 && rfRef.current) {
      setTimeout(() => rfRef.current?.fitView({ duration: 300 }), 50);
    }
  }, [nodes]);

  if (error) {
    return (
      <div className="flex-1 flex items-center justify-center" style={{ minHeight: 300, background: '#1a1a2e' }}>
        <div className="text-center">
          <div className="text-red-400 text-sm mb-2">Failed to load flow</div>
          <div className="text-gray-500 text-xs">{error}</div>
        </div>
      </div>
    );
  }

  if (!nodes || nodes.length === 0) {
    return (
      <div className="flex-1 flex items-center justify-center" style={{ minHeight: 300, background: '#1a1a2e' }}>
        <div className="text-center text-gray-500 text-sm">Select an instance to view its flow</div>
      </div>
    );
  }

  const safeNodes = nodes.map(n => ({
    id: n.id,
    type: n.type,
    position: { x: (n as any).x ?? n.position?.x ?? 200, y: (n as any).y ?? n.position?.y ?? 50 },
    data: n.data || {},
    style: n.data?.active
      ? { border: '2px solid #3b82f6', boxShadow: '0 0 12px rgba(59,130,246,0.5)' }
      : n.data?.status === 'done' ? { opacity: 0.4 } : {}
  }));

  return (
    <div className="flex-1 h-full" style={{ minHeight: 300, background: '#1a1a2e' }}>
      <ReactFlow nodes={safeNodes} edges={edges} nodeTypes={nodeTypes}
        onInit={(rf: any) => { rfRef.current = rf; }}
        nodesDraggable={false} nodesConnectable={false} elementsSelectable={false}>
        <Background />
      </ReactFlow>
    </div>
  );
}
