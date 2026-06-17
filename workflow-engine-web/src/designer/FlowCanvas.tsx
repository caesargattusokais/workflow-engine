import { useCallback, useRef, useState, useEffect } from 'react';
import {
  ReactFlow, Background, Controls,
  addEdge, Connection, MarkerType,
  type Node, type Edge
} from '@xyflow/react';
import '@xyflow/react/dist/style.css';
import { nodeTypes } from './nodes';

interface FlowCanvasProps {
  nodes: Node[];
  edges: Edge[];
  onNodesChange: any;
  onEdgesChange: any;
  setNodes: (nodes: Node[]) => void;
  setEdges: (edges: Edge[]) => void;
  onNodeSelect: (node: Node | null) => void;
}

interface ContextMenuState {
  x: number;
  y: number;
  type: 'node' | 'edge' | 'pane';
  nodeId?: string;
  edgeId?: string;
}

let nodeIdCounter = 0;

export default function FlowCanvas({ nodes, edges, onNodesChange, onEdgesChange, setNodes, setEdges, onNodeSelect }: FlowCanvasProps) {
  const reactFlowWrapper = useRef<HTMLDivElement>(null);
  const [menu, setMenu] = useState<ContextMenuState | null>(null);

  // Close menu on any click outside
  useEffect(() => {
    const close = () => setMenu(null);
    window.addEventListener('click', close);
    return () => window.removeEventListener('click', close);
  }, []);

  const onConnect = useCallback((params: Connection) => {
    setEdges([...edges, {
      ...params,
      id: `edge_${Date.now()}`,
      markerEnd: { type: MarkerType.ArrowClosed, color: '#666' },
      style: { stroke: '#666', strokeWidth: 2 },
      interactionWidth: 20
    } as Edge]);
  }, [edges, setEdges]);

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
      data: { name: type, assignee: '', candidateGroups: [], handlerClass: '' },
      draggable: true,
      selectable: true
    };
    setNodes([...nodes, newNode]);
  }, [nodes, setNodes]);

  // ── Click handlers ──────────────────────────
  const onNodeClick = useCallback((_: unknown, node: Node) => {
    onNodeSelect(node);
  }, [onNodeSelect]);

  const onPaneClick = useCallback(() => {
    onNodeSelect(null);
  }, [onNodeSelect]);

  // ── Right-click → context menu ──────────────
  const onNodeContextMenu = useCallback((e: React.MouseEvent, node: Node) => {
    e.preventDefault();
    onNodeSelect(node);
    setMenu({ x: e.clientX, y: e.clientY, type: 'node', nodeId: node.id });
  }, [onNodeSelect]);

  const onEdgeContextMenu = useCallback((e: React.MouseEvent, edge: Edge) => {
    e.preventDefault();
    setMenu({ x: e.clientX, y: e.clientY, type: 'edge', edgeId: edge.id });
  }, []);

  const onPaneContextMenu = useCallback((e: any) => {
    e.preventDefault();
    setMenu({ x: e.clientX, y: e.clientY, type: 'pane' });
  }, []);

  // ── Menu actions ────────────────────────────
  const deleteNode = useCallback((nodeId: string) => {
    setNodes(nodes.filter(n => n.id !== nodeId));
    setEdges(edges.filter(e => e.source !== nodeId && e.target !== nodeId));
    onNodeSelect(null);
    setMenu(null);
  }, [nodes, edges, setNodes, setEdges, onNodeSelect]);

  const deleteEdge = useCallback((edgeId: string) => {
    setEdges(edges.filter(e => e.id !== edgeId));
    setMenu(null);
  }, [edges, setEdges]);

  const duplicateNode = useCallback((nodeId: string) => {
    const node = nodes.find(n => n.id === nodeId);
    if (!node) return;
    const copy: Node = {
      ...node,
      id: `node_${++nodeIdCounter}`,
      position: { x: node.position.x + 40, y: node.position.y + 40 },
      selected: false
    };
    setNodes([...nodes, copy]);
    setMenu(null);
  }, [nodes, setNodes]);

  return (
    <div ref={reactFlowWrapper} className="flex-1 h-full relative" style={{ minHeight: 400, background: '#1a1a2e' }}>
      <ReactFlow
        nodes={nodes}
        edges={edges}
        onNodesChange={onNodesChange}
        onEdgesChange={onEdgesChange}
        onConnect={onConnect}
        onDrop={onDrop}
        onDragOver={onDragOver}
        onNodeClick={onNodeClick}
        onNodeContextMenu={onNodeContextMenu}
        onEdgeContextMenu={onEdgeContextMenu}
        onPaneClick={onPaneClick}
        onPaneContextMenu={onPaneContextMenu}
        nodeTypes={nodeTypes}
        fitView
        deleteKeyCode={['Backspace', 'Delete']}
        multiSelectionKeyCode="Shift"
        defaultEdgeOptions={{
          style: { stroke: '#555', strokeWidth: 2 },
          markerEnd: { type: MarkerType.ArrowClosed, color: '#555' },
          interactionWidth: 20
        }}
        style={{ background: '#1a1a2e' }}
      >
        <Background color="#2a2a4a" gap={20} size={1} />
        <Controls className="!bg-gray-800 !border-gray-700 !fill-gray-300" />
      </ReactFlow>

      {/* ── Context Menu ──────────────────────── */}
      {menu && (
        <div
          className="fixed z-50 bg-gray-800 border border-gray-600 rounded shadow-xl py-1 min-w-[140px]"
          style={{ left: menu.x, top: menu.y }}
          onClick={e => e.stopPropagation()}
        >
          {menu.type === 'node' && (
            <>
              <div className="px-3 py-1 text-xs text-gray-500 border-b border-gray-700">
                Node: {nodes.find(n => n.id === menu.nodeId)?.type}
              </div>
              <button
                onClick={() => duplicateNode(menu.nodeId!)}
                className="w-full text-left px-3 py-1.5 text-sm text-gray-300 hover:bg-gray-700 transition-colors">
                Duplicate
              </button>
              <button
                onClick={() => deleteNode(menu.nodeId!)}
                className="w-full text-left px-3 py-1.5 text-sm text-red-400 hover:bg-gray-700 transition-colors">
                Delete
              </button>
            </>
          )}
          {menu.type === 'edge' && (
            <>
              <div className="px-3 py-1 text-xs text-gray-500 border-b border-gray-700">
                Edge
              </div>
              <button
                onClick={() => deleteEdge(menu.edgeId!)}
                className="w-full text-left px-3 py-1.5 text-sm text-red-400 hover:bg-gray-700 transition-colors">
                Delete Edge
              </button>
            </>
          )}
          {menu.type === 'pane' && (
            <>
              <div className="px-3 py-1 text-xs text-gray-500 border-b border-gray-700">
                Canvas
              </div>
              <div className="px-3 py-1.5 text-sm text-gray-500">
                Drag nodes from palette
              </div>
            </>
          )}
        </div>
      )}
    </div>
  );
}
