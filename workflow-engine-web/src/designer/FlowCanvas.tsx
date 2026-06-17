import { useCallback, useRef, type Dispatch, type SetStateAction } from 'react';
import {
  ReactFlow, Background, Controls, MiniMap,
  addEdge, Connection, MarkerType,
  type Node, type Edge, type OnNodesChange, type OnEdgesChange
} from '@xyflow/react';
import '@xyflow/react/dist/style.css';
import { nodeTypes } from './nodes';

interface FlowCanvasProps {
  nodes: Node[];
  edges: Edge[];
  onNodesChange: OnNodesChange;
  onEdgesChange: OnEdgesChange;
  setNodes: Dispatch<SetStateAction<Node[]>>;
  setEdges: Dispatch<SetStateAction<Edge[]>>;
  onNodeSelect: (node: Node | null) => void;
}

let nodeIdCounter = 0;

export default function FlowCanvas({ nodes, edges, onNodesChange, onEdgesChange, setNodes, setEdges, onNodeSelect }: FlowCanvasProps) {
  const reactFlowWrapper = useRef<HTMLDivElement>(null);

  const onConnect = useCallback((params: Connection) => {
    setEdges((eds: Edge[]) => addEdge({
      ...params,
      markerEnd: { type: MarkerType.ArrowClosed, color: '#666' },
      style: { stroke: '#666', strokeWidth: 2 }
    }, eds));
  }, [setEdges]);

  const onDragOver = useCallback((e: React.DragEvent) => {
    e.preventDefault();
    e.dataTransfer.dropEffect = 'move';
  }, []);

  const onDrop = useCallback((e: React.DragEvent) => {
    e.preventDefault();
    const type = e.dataTransfer.getData('application/reactflow');
    if (!type || !reactFlowWrapper.current) return;

    const bounds = reactFlowWrapper.current.getBoundingClientRect();
    const position = { x: e.clientX - bounds.left - 60, y: e.clientY - bounds.top - 20 };

    const newNode: Node = {
      id: `node_${++nodeIdCounter}`,
      type,
      position,
      data: { name: type, assignee: '', candidateGroups: [], handlerClass: '' }
    };
    setNodes([...nodes, newNode]);
  }, [nodes, setNodes]);

  const onNodeClick = useCallback((_: unknown, node: Node) => onNodeSelect(node), [onNodeSelect]);
  const onPaneClick = useCallback(() => onNodeSelect(null), [onNodeSelect]);

  return (
    <div ref={reactFlowWrapper} className="flex-1 h-full" style={{ minHeight: 400 }}>
      <ReactFlow
        nodes={nodes}
        edges={edges}
        onNodesChange={onNodesChange}
        onEdgesChange={onEdgesChange}
        onConnect={onConnect}
        onDrop={onDrop}
        onDragOver={onDragOver}
        onNodeClick={onNodeClick}
        onPaneClick={onPaneClick}
        nodeTypes={nodeTypes}
        fitView
        deleteKeyCode={['Backspace', 'Delete']}
        multiSelectionKeyCode="Shift"
      >
        <Background />
        <Controls />
        <MiniMap nodeColor={n => n.type === 'userTask' ? '#2563eb' :
          n.type === 'startEvent' ? '#22c55e' : '#6b7280'} />
      </ReactFlow>
    </div>
  );
}
