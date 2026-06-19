import { useCallback, useRef, useState, useEffect } from 'react';
import {
  ReactFlow, Background, MiniMap,
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

function getEdgeStyle(type?: string) {
  switch (type) {
    case 'result': return { stroke: '#22c55e', strokeWidth: 2 };
    case 'exception': return { stroke: '#ef4444', strokeWidth: 2, strokeDasharray: '5,5' };
    case 'timeout': return { stroke: '#f97316', strokeWidth: 2, strokeDasharray: '5,5' };
    case 'conditional': return { stroke: '#e5a50a', strokeWidth: 2 };
    case 'default': return { stroke: '#888', strokeWidth: 2, strokeDasharray: '3,3' };
    default: return { stroke: '#666', strokeWidth: 2 };
  }
}

export default function FlowCanvas({ nodes, edges, onNodesChange, onEdgesChange, setNodes, setEdges, onNodeSelect }: FlowCanvasProps) {
  const reactFlowWrapper = useRef<HTMLDivElement>(null);
  const [menu, setMenu] = useState<ContextMenuState | null>(null);
  const [locked, setLocked] = useState(false);
  const rfInstance = useRef<any>(null);

  // Close menu on any click outside
  useEffect(() => {
    const close = () => setMenu(null);
    window.addEventListener('click', close);
    return () => window.removeEventListener('click', close);
  }, []);

  const onConnect = useCallback((params: Connection) => {
    if (locked) return;
    setEdges([...edges, {
      ...params,
      id: `edge_${Date.now()}`,
      markerEnd: { type: MarkerType.ArrowClosed, color: '#666' },
      style: getEdgeStyle(),
      interactionWidth: 20,
      data: { edgeType: 'direct' }
    } as Edge]);
  }, [edges, setEdges, locked]);

  const onDragOver = useCallback((e: React.DragEvent) => {
    e.preventDefault();
    e.dataTransfer.dropEffect = 'move';
  }, []);

  const onDrop = useCallback((e: React.DragEvent) => {
    e.preventDefault();
    if (locked) return;
    const type = e.dataTransfer.getData('application/reactflow');
    if (!type || !reactFlowWrapper.current) return;

    const rf = rfInstance.current;
    // Convert screen coords to flow coords (accounts for zoom/pan)
    const position = rf
      ? rf.screenToFlowPosition({ x: e.clientX, y: e.clientY })
      : { x: e.clientX - reactFlowWrapper.current.getBoundingClientRect().left - 60, y: e.clientY - reactFlowWrapper.current.getBoundingClientRect().top - 20 };

    const newNode: Node = {
      id: `node_${++nodeIdCounter}`,
      type,
      position,
      data: { name: type, assignee: '', candidateGroups: [], handlerClass: '' }
    };
    setNodes([...nodes, newNode]);
  }, [nodes, setNodes, locked]);

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
        nodesDraggable={!locked}
        nodesConnectable={!locked}
        elementsSelectable={!locked}
        panOnDrag={!locked}
        zoomOnScroll={!locked}
        onInit={(rf: any) => { rfInstance.current = rf; }}
        deleteKeyCode={['Backspace', 'Delete']}
        multiSelectionKeyCode="Shift"
        defaultEdgeOptions={{
          style: getEdgeStyle(),
          markerEnd: { type: MarkerType.ArrowClosed, color: '#666' },
          interactionWidth: 20,
          data: { edgeType: 'direct' }
        }}
        style={{ background: '#1a1a2e' }}
      >
        <Background color="#2a2a4a" gap={20} size={1} />
      </ReactFlow>

      {/* ── Bottom-left tools ─────────────────── */}
      <div className="absolute bottom-3 left-3 z-10 flex gap-1.5">
        <button onClick={() => rfInstance.current?.fitView({ duration: 300 })}
          className="bg-gray-800 hover:bg-gray-700 border border-gray-600 rounded px-2.5 py-1.5
                     text-gray-300 text-xs transition-colors shadow-lg"
          title="定位 — 居中显示所有节点">
          &#8982; 定位
        </button>
        <button onClick={() => setLocked(!locked)}
          className={`border rounded px-2.5 py-1.5 text-xs transition-colors shadow-lg
            ${locked ? 'bg-red-900 border-red-600 text-red-300' : 'bg-gray-800 hover:bg-gray-700 border-gray-600 text-gray-300'}`}
          title={locked ? '解锁编辑' : '锁定画布'}>
          {locked ? '🔒 锁定' : '🔓'}
        </button>
      </div>

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
          {menu.type === 'edge' && (() => {
              const edge = edges.find(e => e.id === menu.edgeId);
              const srcNode = nodes.find(n => n.id === edge?.source);
              const srcType = srcNode?.type || '';
              const allowed: Record<string, string[]> = {
                serviceTask: ['direct','result','exception'],
                userTask: ['direct','timeout'],
                exclusiveGateway: ['conditional','default'],
                parallelGateway: ['direct'],
                inclusiveGateway: ['conditional','default'],
                startEvent: ['direct'],
                endEvent: [],
                timer: ['direct'],
              };
              const types = allowed[srcType] || ['direct'];
              const curType = edge?.data?.edgeType as string || 'direct';
              const needsExpr = ['conditional','result','exception'].includes(curType);
              return (
              <div>
              <div className="px-3 py-1 text-xs text-gray-500 border-b border-gray-700">Edge — {srcType}</div>
              {types.map(t => (
                <button key={t} onClick={() => {
                  const style = getEdgeStyle(t);
                  setEdges(edges.map(e => e.id === menu.edgeId ? {
                    ...e, data: { ...e.data, edgeType: t }, style, markerEnd: { type: MarkerType.ArrowClosed, color: style.stroke as string }
                  } : e));
                  setMenu(null);
                }}
                  className={`w-full text-left px-3 py-1.5 text-sm hover:bg-gray-700 ${t === curType ? 'text-white' : 'text-gray-400'}`}>
                  {t === curType ? `● ${t}` : `  ${t}`}
                </button>
              ))}
              {needsExpr && (
                <div className="px-3 py-1" onClick={e => e.stopPropagation()}>
                  <input className="w-full bg-gray-700 rounded px-1.5 py-0.5 text-white text-xs mt-1"
                    placeholder="SpEL: days > 3"
                    defaultValue={(edges.find(e => e.id === menu.edgeId)?.data?.expr as string) || ''}
                    onBlur={e => {
                      setEdges(edges.map(ed => ed.id === menu.edgeId ? {
                        ...ed, data: { ...ed.data, expr: e.target.value }
                      } : ed));
                    }}
                    onKeyDown={e => { if (e.key === 'Enter') (e.target as HTMLInputElement).blur(); }}
                  />
                </div>
              )}
              <div className="border-t border-gray-700" />
              <button onClick={() => { deleteEdge(menu.edgeId!); setMenu(null); }}
                className="w-full text-left px-3 py-1.5 text-sm text-red-400 hover:bg-gray-700">Delete</button>
              </div>
            );
            })()}
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
