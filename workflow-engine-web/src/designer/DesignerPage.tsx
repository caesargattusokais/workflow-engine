import { useState, useCallback, useMemo, useEffect, useRef } from 'react';
import { useNodesState, useEdgesState, type Node, type Edge } from '@xyflow/react';
import NodePalette from './NodePalette';
import FlowCanvas from './FlowCanvas';
import PropertyPanel from './PropertyPanel';
import { deployDefinition, listDrafts, createDraft, updateDraft, deleteDraft as removeDraft, getDraft, startInstance } from '../api/client';
import { graphToYaml } from './graphToYaml';

interface Draft {
  id: string;
  name: string;
  nodes: Node[];
  edges: Edge[];
  createdAt: number;
}

interface VarInfo { name: string; source: string; }

export default function DesignerPage({ onNavigate }: { onNavigate?: (t: 'designer'|'monitor') => void }) {
  const [drafts, setDrafts] = useState<Draft[]>([]);
  const [activeId, setActiveId] = useState<string | null>(null);
  const [loaded, setLoaded] = useState(false);
  const saveTimer = useRef<ReturnType<typeof setTimeout>>();

  const activeDraft = drafts.find(d => d.id === activeId) || null;
  const [nodes, setNodes, onNodesChange] = useNodesState<Node>([]);
  const [edges, setEdges, onEdgesChange] = useEdgesState<Edge>([]);
  const [selectedNode, setSelectedNode] = useState<Node | null>(null);
  const [showVars, setShowVars] = useState(false);
  const [saving, setSaving] = useState(false);
  const [dirty, setDirty] = useState(false);
  const [toast, setToast] = useState<string | null>(null);
  const [deployedYaml, setDeployedYaml] = useState<string | null>(null);
  const [deployedId, setDeployedId] = useState<string | null>(null);

  // ── Load drafts from server on mount ──
  useEffect(() => {
    listDrafts().then(list => {
      if (list.length > 0) {
        setDrafts(list.map((d: any) => ({ ...d, nodes: d.nodes || [], edges: d.edges || [] })));
      }
    }).catch(() => {}).finally(() => setLoaded(true));
  }, []);

  // When switching drafts, load full data
  useEffect(() => {
    if (!activeId || !loaded) return;
    getDraft(activeId).then(d => {
      setNodes(d.nodes || []);
      setEdges(d.edges || []);
      setSelectedNode(null);
      setDirty(false);
    }).catch(() => {});
  }, [activeId, loaded]);

  // Mark dirty on change
  useEffect(() => { if (loaded && activeId) setDirty(true); }, [nodes, edges]);

  // Auto-save to server (debounced 2s)
  useEffect(() => {
    if (!activeId || !loaded || !dirty) return;
    clearTimeout(saveTimer.current);
    saveTimer.current = setTimeout(() => {
      doSave();
    }, 2000);
    return () => clearTimeout(saveTimer.current);
  }, [nodes, edges]);

  const doSave = async () => {
    if (!activeId) return;
    setSaving(true);
    try {
      await updateDraft(activeId, { nodes, edges });
      setDirty(false);
    } catch { alert('Save failed — server unreachable'); }
    finally { setSaving(false); }
  };

  // ── Draft actions ─────────────────────
  const newDraft = async () => {
    try {
      const d = await createDraft(`Draft ${drafts.length + 1}`);
      setDrafts(prev => [...prev, { ...d, nodes: [], edges: [] }]);
      setActiveId(d.id);
    } catch { /* server unavailable */ }
  };

  const renameDraft = async (id: string) => {
    const name = prompt('草稿名称:');
    if (!name) return;
    try {
      await updateDraft(id, { name });
      setDrafts(prev => prev.map(d => d.id === id ? { ...d, name } : d));
    } catch {}
  };

  const delDraft = async (id: string) => {
    if (!confirm('删除这个草稿?')) return;
    try { await removeDraft(id); } catch {}
    setDrafts(prev => {
      const u = prev.filter(d => d.id !== id);
      if (activeId === id) {
        const next = u.length > 0 ? u[0].id : null;
        setActiveId(next);
      }
      return u;
    });
  };

  const switchDraft = (id: string) => setActiveId(id);

  const handleNodeSelect = useCallback((node: Node | null) => setSelectedNode(node), []);
  const handleNodeChange = useCallback((updatedNode: Node) => {
    setNodes(nodes.map(n => n.id === updatedNode.id ? updatedNode : n));
    setSelectedNode(updatedNode);
  }, [nodes, setNodes]);

  const handleDeleteNode = useCallback(() => {
    if (!selectedNode) return;
    setNodes(nodes.filter(n => n.id !== selectedNode.id));
    setEdges(edges.filter(e => e.source !== selectedNode.id && e.target !== selectedNode.id));
    setSelectedNode(null);
  }, [selectedNode, nodes, edges, setNodes, setEdges]);

  const handleDeploy = async () => {
    if (nodes.length === 0) { setToast('Add some nodes first'); return; }
    try {
      const yaml = graphToYaml(nodes, edges, activeDraft?.name || 'workflow');
      const positions: Record<string, {x:number;y:number}> = {};
      for (const n of nodes) positions[n.id] = n.position;
      const result = await deployDefinition(yaml, positions);
      setDeployedYaml(yaml);
      setDeployedId(result.id);
      // Auto-start instance
      try {
        const inst = await startInstance(result.id, {});
        setToast(`Deployed & started! Def: ${result.id}, Instance: ${inst.id.substring(0,8)}`);
      } catch {
        setToast(`Deployed: ${result.id} (auto-start failed)`);
      }
    } catch (e: any) { setToast('Deploy failed: ' + e.message); }
  };

  // ── Variables ─────────────────────────
  const allVars = useMemo<VarInfo[]>(() => {
    const vars: VarInfo[] = [];
    for (const node of nodes) {
      if (node.type === 'startEvent') {
        for (const v of (node.data.initialVars as string[]) || []) {
          if (v.trim()) vars.push({ name: v.trim(), source: 'Start' });
        }
      }
      if (node.type === 'serviceTask') {
        const hc = node.data.handlerClass as string;
        const label = hc ? (hc.split('.').pop() || 'Code') : ((node.data.httpMode as boolean) ? 'HTTP' : 'Code');
        const nodeName = (node.data.name as string) || node.id;
        for (const rv of (node.data.returnValues as Array<{key:string;type:string}>) || []) {
          if (rv.key) vars.push({ name: `${node.id}_${rv.key}`, source: `「${nodeName}」${label} → ${rv.type}` });
        }
      }
      if (node.type === 'userTask') {
        const m = ((node.data.assignee as string) || '').match(/\$\{(\w+)\}/);
        if (m && m[1]) vars.push({ name: m[1], source: 'Assignee' });
      }
    }
    return vars;
  }, [nodes]);

  return (
    <div className="flex flex-col h-full">
      {/* Toolbar */}
      <div className="bg-gray-800 border-b border-gray-700 px-4 py-1 flex justify-between items-center">
        <div className="flex items-center gap-3">
          <span className="text-sm text-gray-400">
            {activeDraft ? activeDraft.name : 'No draft'}
          </span>
          <span className={`text-[10px] ${dirty ? 'text-yellow-500' : 'text-gray-600'}`}>
            {saving ? 'saving...' : dirty ? 'unsaved' : 'saved'}
          </span>
          {dirty && (
            <button onClick={doSave}
              className="bg-blue-600 hover:bg-blue-500 text-white text-xs px-2 py-0.5 rounded">
              Save
            </button>
          )}
          {selectedNode && (
            <button onClick={handleDeleteNode}
              className="bg-red-600 hover:bg-red-500 text-white text-xs px-2 py-0.5 rounded">
              Delete Node
            </button>
          )}
          <button onClick={() => setShowVars(!showVars)}
            className={`text-xs px-2 py-1 rounded ${showVars ? 'bg-blue-600 text-white' : 'bg-gray-700 text-gray-400'}`}>
            Variables ({allVars.length})
          </button>
        </div>
        <button onClick={handleDeploy}
          className="bg-green-600 hover:bg-green-500 text-white text-sm px-4 py-1 rounded">
          Deploy
        </button>
      </div>

      {/* Toast + YAML preview */}
      {toast && (
        <div className="bg-green-900 border-b border-green-700 px-4 py-2 text-sm">
          <div className="flex items-center justify-between">
            <span className="text-green-300">{toast}</span>
            <div className="flex gap-2">
              {deployedYaml && (
                <button onClick={() => setDeployedYaml(deployedYaml ? null : deployedYaml)}
                  className="bg-gray-600 hover:bg-gray-500 text-white text-xs px-2 py-0.5 rounded">
                  {deployedYaml ? 'Hide YAML' : 'Show YAML'}
                </button>
              )}
              <button onClick={() => { setToast(null); setDeployedYaml(null); }}
                className="text-gray-400 hover:text-white text-xs">✕</button>
            </div>
          </div>
          {deployedYaml && (
            <pre className="mt-2 bg-gray-900 rounded p-2 text-xs text-green-400 overflow-auto max-h-48 font-mono">
              {deployedYaml}
            </pre>
          )}
        </div>
      )}

      {/* Variables Panel */}
      {showVars && (
        <div className="bg-gray-800 border-b border-gray-700 px-4 py-2">
          <div className="flex flex-wrap gap-1.5">
            {allVars.length === 0 ? (
              <span className="text-xs text-gray-600 italic">No variables defined</span>
            ) : allVars.map((v, i) => (
              <span key={i} className="inline-flex items-center gap-1 bg-gray-750 border border-gray-600
                       rounded px-2 py-0.5 text-xs text-gray-300" title={v.source}>
                <code className="text-blue-400">{v.name}</code>
                <span className="text-[10px] text-gray-600">← {v.source}</span>
              </span>
            ))}
          </div>
        </div>
      )}

      {/* Main */}
      <div className="flex flex-1 overflow-hidden">
        {/* Draft List Sidebar */}
        <div className="w-40 bg-gray-850 border-r border-gray-700 flex flex-col">
          <div className="p-2 border-b border-gray-700 flex justify-between items-center">
            <span className="text-xs text-gray-500">草稿列表</span>
            <button onClick={newDraft}
              className="text-xs bg-blue-600 hover:bg-blue-500 text-white px-1.5 py-0.5 rounded">
              + New
            </button>
          </div>
          <div className="flex-1 overflow-y-auto">
            {drafts.map(d => (
              <div key={d.id}
                onClick={() => switchDraft(d.id)}
                className={`px-2 py-1.5 cursor-pointer border-b border-gray-800 text-xs flex justify-between items-center group
                  ${d.id === activeId ? 'bg-blue-600/30 border-l-2 border-l-blue-500' : 'hover:bg-gray-700'}`}>
                <div className="truncate flex-1">
                  <div className="text-gray-300 truncate">{d.name}</div>
                  <div className="text-[10px] text-gray-600">{d.nodes.length} nodes</div>
                </div>
                <div className="hidden group-hover:flex gap-0.5 ml-1">
                  <button onClick={(e) => { e.stopPropagation(); renameDraft(d.id); }}
                    className="text-gray-500 hover:text-gray-300 text-[10px]" title="重命名">✎</button>
                  <button onClick={(e) => { e.stopPropagation(); delDraft(d.id); }}
                    className="text-gray-500 hover:text-red-400 text-[10px]" title="删除">✕</button>
                </div>
              </div>
            ))}
            {drafts.length === 0 && (
              <div className="p-3 text-xs text-gray-600 text-center">
                Click + New to create a draft
              </div>
            )}
          </div>
        </div>

        <NodePalette />
        <FlowCanvas
          nodes={nodes} edges={edges}
          onNodesChange={onNodesChange} onEdgesChange={onEdgesChange}
          setNodes={setNodes} setEdges={setEdges}
          onNodeSelect={handleNodeSelect}
        />
        <PropertyPanel node={selectedNode} onChange={handleNodeChange} />
      </div>
    </div>
  );
}
