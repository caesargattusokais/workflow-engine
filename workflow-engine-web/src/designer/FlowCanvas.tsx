import { useCallback, useRef, useState, useEffect } from 'react';
import { useT } from '../i18n';
import {
  ReactFlow, Background, BackgroundVariant,
  Connection,
  type Node, type Edge
} from '@xyflow/react';
import '@xyflow/react/dist/style.css';
import { nodeTypes } from './nodes';
import { EDGE_STYLES, getEdgeStyle, getEdgeMarker, styleEdgeForType } from './edgeStyles';

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
  const [locked, setLocked] = useState(false);
  const rfInstance = useRef<any>(null);
  const { t, lang } = useT();

  // Close menu on any click outside
  useEffect(() => {
    const close = () => setMenu(null);
    window.addEventListener('click', close);
    return () => window.removeEventListener('click', close);
  }, []);

  // Normalize every edge's color/arrow from its semantic type. Edges arrive from
  // template import, loaded drafts and the server graph without a `style`, so
  // without this pass they all render the same default grey. Only write back
  // when something actually differs, to avoid a render loop.
  useEffect(() => {
    let changed = false;
    const normalized = edges.map(edge => {
      const type = (edge.data?.edgeType as string) || 'direct';
      const want = getEdgeStyle(type);
      const cur = (edge.style || {}) as Record<string, unknown>;
      const curMarker = edge.markerEnd as any;
      const markerColor = curMarker && typeof curMarker === 'object' ? curMarker.color : undefined;
      if (cur.stroke === want.stroke && cur.strokeWidth === want.strokeWidth
          && (cur.strokeDasharray || '') === (want.strokeDasharray || '')
          && markerColor === want.stroke) return edge;
      changed = true;
      return styleEdgeForType(edge);
    });
    if (changed) setEdges(normalized);
  }, [edges, setEdges]);

  const onConnect = useCallback((params: Connection) => {
    if (locked) return;
    setEdges([...edges, {
      ...params,
      id: `edge_${Date.now()}`,
      markerEnd: getEdgeMarker('direct'),
      style: getEdgeStyle('direct'),
      interactionWidth: 20,
      data: { edgeType: 'direct' }
    } as Edge]);
  }, [edges, setEdges, locked]);

  const onReconnect = useCallback((oldEdge: Edge, newConnection: Connection) => {
    if (locked) return;
    setEdges(edges.map(e => e.id === oldEdge.id ? {
      ...e,
      source: newConnection.source,
      sourceHandle: newConnection.sourceHandle,
      target: newConnection.target,
      targetHandle: newConnection.targetHandle,
    } : e));
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
      data: { name: type, assignee: '', candidateGroups: [], handlerClass: '',
              ...(type === 'aiTask' ? { mode: 'summary', prompt: '', options: [], outputVar: '' } : {}) }
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
    <div ref={reactFlowWrapper} className="flex-1 h-full relative" style={{ minHeight: 400, background: 'radial-gradient(ellipse at 50% 35%, #1b2130 0%, #171c27 55%, #12161e 100%)' }}>
      <ReactFlow
        nodes={nodes}
        edges={edges}
        onNodesChange={onNodesChange}
        onEdgesChange={onEdgesChange}
        onConnect={onConnect}
        onReconnect={onReconnect}
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
        edgesReconnectable={!locked}
        elementsSelectable={!locked}
        panOnDrag={!locked}
        zoomOnScroll={!locked}
        onInit={(rf: any) => { rfInstance.current = rf; }}
        deleteKeyCode={['Backspace', 'Delete']}
        multiSelectionKeyCode="Shift"
        defaultEdgeOptions={{
          style: getEdgeStyle('direct'),
          markerEnd: getEdgeMarker('direct'),
          interactionWidth: 20,
          data: { edgeType: 'direct' }
        }}
        style={{ background: 'transparent' }}
      >
        <Background variant={BackgroundVariant.Dots} color="#384159" gap={22} size={1.3} />
      </ReactFlow>

      {/* ── Empty canvas hint (does not block dragging) ─── */}
      {nodes.length === 0 && (
        <div className="absolute inset-0 z-[5] flex flex-col items-center justify-center pointer-events-none select-none">
          <div className="w-40 h-28 mb-3 opacity-80">
            <svg viewBox="0 0 160 110" className="w-full h-full" fill="none">
              <rect x="18" y="30" width="124" height="66" rx="10" fill="#6d74e8" fillOpacity="0.05"
                stroke="#6d74e8" strokeOpacity="0.25" strokeWidth="1.5" strokeDasharray="5 5" />
              <rect x="34" y="50" width="32" height="24" rx="6" fill="#6d74e8" fillOpacity="0.16" stroke="#6d74e8" strokeOpacity="0.5" strokeWidth="1.5" />
              <circle cx="50" cy="62" r="3.5" fill="#6d74e8" />
              <path d="M78 62h20m-6-5l6 5-6 5" stroke="#6d74e8" strokeOpacity="0.5" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" />
              <rect x="110" y="50" width="32" height="24" rx="6" fill="#4ade80" fillOpacity="0.12" stroke="#4ade80" strokeOpacity="0.45" strokeWidth="1.5" />
            </svg>
          </div>
          <div className="text-[13px] font-medium text-gray-400 mb-1.5">{t.designer.dragHint}</div>
          <div className="text-[11px] text-gray-600">从左侧节点面板拖入开始节点，或点击「AI 生成」描述你的流程</div>
        </div>
      )}

      {/* ── Edge legend (top-right, non-blocking) ── */}
      <div className="absolute top-3 right-3 z-10 pointer-events-none select-none
                      bg-gray-900/80 border border-gray-700/80 rounded-lg px-3 py-2 shadow-lg backdrop-blur-sm">
        <div className="text-[10px] text-gray-500 mb-1.5 font-medium">{lang === 'zh' ? '连线类型' : 'Edge types'}</div>
        <div className="flex flex-col gap-1">
          {(Object.keys(EDGE_STYLES) as Array<keyof typeof EDGE_STYLES>).map(k => {
            const s = EDGE_STYLES[k];
            return (
              <div key={k} className="flex items-center gap-2">
                <svg width="22" height="8" className="shrink-0">
                  <line x1="1" y1="4" x2="21" y2="4" stroke={s.stroke} strokeWidth={s.strokeWidth}
                    strokeDasharray={s.strokeDasharray} />
                </svg>
                <span className="text-[10px] text-gray-400">{lang === 'zh' ? s.labelZh : s.label}</span>
              </div>
            );
          })}
        </div>
      </div>

      {/* ── Bottom-left tools ─────────────────── */}
      <div className="absolute bottom-3 left-3 z-10 flex gap-1.5">
        <button onClick={() => rfInstance.current?.fitView({ duration: 300 })}
          className="bg-gray-800 hover:bg-gray-700 border border-gray-600 rounded px-2.5 py-1.5
                     text-gray-300 text-xs transition-colors shadow-lg"
          title={`${t.designer.locate} — ${t.designer.fitViewHint}`}>
          &#8982; {t.designer.locate}
        </button>
        <button onClick={() => setLocked(!locked)}
          className={`border rounded px-2.5 py-1.5 text-xs transition-colors shadow-lg
            ${locked ? 'bg-red-900 border-red-600 text-red-300' : 'bg-gray-800 hover:bg-gray-700 border-gray-600 text-gray-300'}`}
          title={locked ? `${t.designer.unlock}${t.designer.unlockHint}` : `${t.designer.lock}${t.designer.lockHint}`}>
          {locked ? `🔒 ${t.designer.lock}` : '🔓'}
        </button>
      </div>

      {/* ── Context Menu ──────────────────────── */}
      {menu && (
        <div
          className="fixed z-50 bg-gray-800 border border-gray-700 rounded-lg shadow-popover py-1 min-w-[160px]"
          style={{ left: menu.x, top: menu.y }}
          onClick={e => e.stopPropagation()}
        >
          {menu.type === 'node' && (() => {
              const connectedEdges = edges.filter(e => e.source === menu.nodeId || e.target === menu.nodeId);
              return (
              <>
              <div className="px-3 py-1 text-xs text-gray-500 border-b border-gray-700">
                Node: {nodes.find(n => n.id === menu.nodeId)?.type}
              </div>
              <button
                onClick={() => duplicateNode(menu.nodeId!)}
                className="w-full text-left px-3 py-1.5 text-sm text-gray-300 hover:bg-gray-700 transition-colors">
                {t.designer.duplicate}
              </button>
              {connectedEdges.length > 0 && (
                <>
                  <div className="px-3 py-1 text-[10px] text-gray-600 border-t border-gray-700 mt-0.5">
                    Edges ({connectedEdges.length})
                  </div>
                  {connectedEdges.map(e => {
                    const srcName = (nodes.find(n => n.id === e.source)?.data as any)?.name || e.source;
                    const tgtName = (nodes.find(n => n.id === e.target)?.data as any)?.name || e.target;
                    const et = (e.data as any)?.edgeType || 'direct';
                    return (
                      <button key={e.id}
                        onClick={() => { deleteEdge(e.id); setMenu(null); }}
                        className="w-full text-left px-3 py-1 text-xs text-gray-400 hover:bg-gray-700 hover:text-red-400 transition-colors flex justify-between items-center">
                        <span className="truncate max-w-[120px]">{srcName} → {tgtName}</span>
                        <span className="text-[10px] text-gray-600 ml-1">{et}</span>
                      </button>
                    );
                  })}
                </>
              )}
              <div className="border-t border-gray-700 mt-0.5" />
              <button
                onClick={() => deleteNode(menu.nodeId!)}
                className="w-full text-left px-3 py-1.5 text-sm text-red-400 hover:bg-gray-700 transition-colors">
                {t.designer.delete}
              </button>
              </>
            );
            })()}
          {menu.type === 'edge' && (() => {
              const edge = edges.find(e => e.id === menu.edgeId);
              const srcNode = nodes.find(n => n.id === edge?.source);
              const srcType = srcNode?.type || '';
              const allowed: Record<string, string[]> = {
                serviceTask: ['direct','result','exception'],
                aiTask: ['direct','result','exception'],
                userTask: ['direct','timeout'],
                exclusiveGateway: ['conditional','default'],
                parallelGateway: ['direct'],
                inclusiveGateway: ['conditional','default'],
                startEvent: ['direct'],
                endEvent: [],
                timer: ['direct'],
                callActivity: ['direct'],
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
                    ...e, data: { ...e.data, edgeType: t }, style, markerEnd: getEdgeMarker(t)
                  } : e));
                  setMenu(null);
                }}
                  className={`w-full text-left px-3 py-1.5 text-sm hover:bg-gray-700 ${t === curType ? 'text-white' : 'text-gray-400'}`}>
                  {t === curType ? `● ${t}` : `  ${t}`}
                </button>
              ))}
              {needsExpr && (
                <div className="px-3 py-1" onClick={e => e.stopPropagation()}>
                  <input className="w-full bg-gray-900/70 border border-gray-700 rounded-md px-2 py-1 text-gray-200 text-xs mt-1"
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
                className="w-full text-left px-3 py-1.5 text-sm text-red-400 hover:bg-gray-700">{t.designer.delete}</button>
              </div>
            );
            })()}
          {menu.type === 'pane' && (
            <>
              <div className="px-3 py-1 text-xs text-gray-500 border-b border-gray-700">
                {t.designer.canvas}
              </div>
              <div className="px-3 py-1.5 text-sm text-gray-500">
                {t.designer.dragHint}
              </div>
            </>
          )}
        </div>
      )}
    </div>
  );
}
