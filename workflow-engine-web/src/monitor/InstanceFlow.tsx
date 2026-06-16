import { ReactFlow, Background, type Node, type Edge } from '@xyflow/react';
import '@xyflow/react/dist/style.css';
import { nodeTypes } from '../designer/nodes';

export default function InstanceFlow({ nodes, edges }: { nodes: Node[], edges: Edge[] }) {
  const styledNodes = nodes.map(n => ({
    ...n,
    style: n.data?.active
      ? { border: '2px solid #3b82f6', boxShadow: '0 0 12px rgba(59,130,246,0.5)' }
      : n.data?.status === 'done' ? { opacity: 0.4 } : {}
  }));

  return (
    <div className="flex-1 h-full" style={{ minHeight: 300 }}>
      <ReactFlow nodes={styledNodes} edges={edges} nodeTypes={nodeTypes}
        fitView nodesDraggable={false} nodesConnectable={false} elementsSelectable={false}>
        <Background />
      </ReactFlow>
    </div>
  );
}
