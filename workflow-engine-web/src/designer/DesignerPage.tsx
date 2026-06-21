import { useState, useCallback, useMemo, useEffect, useRef } from 'react';
import { useNodesState, useEdgesState, type Node, type Edge } from '@xyflow/react';
import NodePalette from './NodePalette';
import FlowCanvas from './FlowCanvas';
import PropertyPanel from './PropertyPanel';
import { deployDefinition, listDrafts, createDraft, updateDraft, deleteDraft as removeDraft, getDraft, startInstance, listInstances, copyDraft, importDraft } from '../api/client';
import { graphToYaml } from './graphToYaml';
import { yamlToGraph } from './yamlToGraph';
import { useT } from '../i18n';

interface Draft {
  id: string;
  name: string;
  version: number;
  nodes: Node[];
  edges: Edge[];
  createdAt: number;
}

interface VarInfo { name: string; source: string; }

export default function DesignerPage({ onNavigate }: { onNavigate?: (t: 'designer'|'monitor') => void }) {
  const { t } = useT();
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
  const [toast, setToast] = useState<string | null>(null);
  const [deployedYaml, setDeployedYaml] = useState<string | null>(null);
  const [showYaml, setShowYaml] = useState(false);
  const [deployedId, setDeployedId] = useState<string | null>(null);
  const [draftMenu, setDraftMenu] = useState<{x:number;y:number;draft:Draft}|null>(null);
  const [showTemplates, setShowTemplates] = useState(false);
  const [templates, setTemplates] = useState<any[]>([]);

  const [instances, setInstances] = useState<any[]>([]);

  // Close draft context menu on click outside
  useEffect(() => {
    const close = () => setDraftMenu(null);
    window.addEventListener('click', close);
    return () => window.removeEventListener('click', close);
  }, []);

  // Fetch instances once when drafts change (for count display)
  useEffect(() => {
    if (drafts.length > 0) listInstances().then(setInstances).catch(() => {});
  }, [drafts.length]);

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
    }).catch(() => {});
  }, [activeId, loaded]);

  // Live-refresh YAML panel while open
  useEffect(() => {
    if (showYaml && activeDraft) {
      setDeployedYaml(graphToYaml(nodes, edges, activeDraft.id, activeDraft.name, activeDraft.version || 1));
    }
  }, [nodes, edges, showYaml]);

  // Auto-save to server (debounced 10s, no version bump)
  useEffect(() => {
    if (!activeId || !loaded) return;
    clearTimeout(saveTimer.current);
    saveTimer.current = setTimeout(() => {
      updateDraft(activeId, { nodes, edges }).catch(() => {});
    }, 10000);
    return () => clearTimeout(saveTimer.current);
  }, [nodes, edges]);

  const doSave = async () => {
    if (!activeId) return;
    setSaving(true);
    try {
      const newVersion = (activeDraft?.version || 1) + 1;
      await updateDraft(activeId, { nodes, edges, version: newVersion });
      setDrafts(prev => prev.map(d => d.id === activeId ? {...d, version: newVersion} : d));
      setToast(`${t.designer.savedAs}${newVersion}`);
    } catch (e: any) { alert(e.message || t.designer.saveFailed); }
    finally { setSaving(false); }
  };

  // ── Draft actions ─────────────────────
  const newDraft = async () => {
    try {
      const d = await createDraft(`Draft ${drafts.length + 1}`);
      setDrafts(prev => [...prev, { ...d, nodes: [], edges: [] }]);
      setActiveId(d.id);
    } catch (e: any) { alert('创建失败: ' + (e.message || 'server unavailable')); }
  };

  const renameDraft = async (id: string) => {
    const name = prompt(t.designer.draftName);
    if (!name) return;
    try {
      await updateDraft(id, { name });
      setDrafts(prev => prev.map(d => d.id === id ? { ...d, name } : d));
    } catch (e: any) { alert('重命名失败: ' + (e.message || 'server error')); }
  };

  const delDraft = async (id: string) => {
    if (!confirm(t.designer.confirmDelete)) return;
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

  const copyDraftAction = async (id: string) => {
    try {
      const copy = await copyDraft(id);
      setDrafts(prev => [...prev, { ...copy, nodes: copy.nodes || [], edges: copy.edges || [] }]);
      setToast(`${t.designer.copied}${copy.name}`);
    } catch (e: any) { alert(e.message || t.designer.saveFailed); }
  };

  const downloadYaml = (draft: Draft) => {
    const d = draft.id === activeId ? { ...draft, nodes, edges } : draft;
    const yaml = graphToYaml(d.nodes, d.edges, d.id, d.name);
    const blob = new Blob([yaml], { type: 'text/yaml' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `${d.name.replace(/\s+/g, '_')}.yaml`;
    a.click();
    URL.revokeObjectURL(url);
  };

  const importYamlAction = () => {
    const input = document.createElement('input');
    input.type = 'file';
    input.accept = '.yaml,.yml,.txt';
    input.onchange = async () => {
      const file = input.files?.[0];
      if (!file) return;
      try {
        const text = await file.text();
        const { name, nodes: importedNodes, edges: importedEdges } = yamlToGraph(text);
        const d = await importDraft(name, importedNodes, importedEdges);
        setDrafts(prev => [...prev, { ...d, nodes: importedNodes, edges: importedEdges }]);
        setActiveId(d.id);
        setToast(`Imported: ${name}`);
      } catch (e: any) {
        alert('Import failed: ' + e.message);
      }
    };
    input.click();
  };

  const loadTemplates = async () => {
    try {
      const res = await fetch('/templates/manifest.json');
      setTemplates(await res.json());
      setShowTemplates(true);
    } catch { alert('加载模板列表失败'); }
  };

  const importTemplate = async (file: string) => {
    try {
      const res = await fetch(`/templates/${file}`);
      const yaml = await res.text();
      const { name, nodes: importedNodes, edges: importedEdges } = yamlToGraph(yaml);
      const d = await importDraft(name, importedNodes, importedEdges);
      setDrafts(prev => [...prev, { ...d, nodes: importedNodes, edges: importedEdges }]);
      setActiveId(d.id);
      setShowTemplates(false);
      setToast(`Imported: ${name}`);
    } catch (e: any) { alert('Import failed: ' + e.message); }
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
    if (nodes.length === 0) { setToast(t.designer.addNodes); return; }
    try {
      const yaml = graphToYaml(nodes, edges, activeDraft?.id || 'workflow', activeDraft?.name || 'workflow', activeDraft?.version || 1);
      const positions: Record<string, {x:number;y:number}> = {};
      for (const n of nodes) positions[n.id] = n.position;
      const result = await deployDefinition(yaml, positions);
      setDeployedYaml(yaml);
      setShowYaml(true);
      setDeployedId(result.id);
      // Auto-start instance with startEvent initialVars
      try {
        const startNode = nodes.find(n => n.type === 'startEvent');
        const initVars: Record<string, string> = {};
        const vars = (startNode?.data?.initialVars as Array<{key:string;value:string}>) || [];
        vars.forEach(v => { if (v.key) initVars[v.key] = v.value || ''; });
        const inst = await startInstance(result.id, initVars);
        setToast(`${t.designer.deployStart} Def: ${result.id}, Instance: ${inst.id.substring(0,8)}`);
        listInstances().then(setInstances).catch(() => {});
      } catch {
        setToast(`${t.designer.deployOnly}${result.id} (auto-start failed)`);
      }
    } catch (e: any) { setToast(t.designer.deployFailed + e.message); }
  };

  // ── Variables ─────────────────────────
  const allVars = useMemo<VarInfo[]>(() => {
    const vars: VarInfo[] = [];
    for (const node of nodes) {
      if (node.type === 'startEvent') {
        for (const v of (node.data.initialVars as Array<{key:string;value:string}>) || []) {
          if (v.key) vars.push({ name: v.key, source: v.value ? `Start (默认: ${v.value})` : 'Start' });
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
            {activeDraft ? `${activeDraft.name} v${activeDraft.version || 1}` : t.designer.noDraft}
          </span>
          <button onClick={doSave}
            className="bg-blue-600 hover:bg-blue-500 text-white text-xs px-3 py-1 rounded">
            {saving ? t.designer.saving : t.designer.save}
          </button>
          {selectedNode && (
            <button onClick={handleDeleteNode}
              className="bg-red-600 hover:bg-red-500 text-white text-xs px-2 py-0.5 rounded">
              {t.designer.deleteNode}
            </button>
          )}
          <button onClick={() => setShowVars(!showVars)}
            className={`text-xs px-2 py-1 rounded ${showVars ? 'bg-blue-600 text-white' : 'bg-gray-700 text-gray-400'}`}>
            Variables ({allVars.length})
          </button>
        </div>
        <button onClick={handleDeploy}
          className="bg-green-600 hover:bg-green-500 text-white text-sm px-4 py-1 rounded">
          {t.designer.deploy}
        </button>
      </div>

      {/* Toast + YAML preview */}
      {toast && (
        <div className="bg-green-900 border-b border-green-700 px-4 py-2 text-sm">
          <div className="flex items-center justify-between">
            <span className="text-green-300">{toast}</span>
            <div className="flex gap-2">
              {deployedId && (
                <button onClick={() => onNavigate?.('monitor')}
                  className="bg-blue-600 hover:bg-blue-500 text-white text-xs px-2 py-0.5 rounded">
                  {t.designer.viewInMonitor}
                </button>
              )}
              {deployedYaml && (
                <button onClick={() => setShowYaml(!showYaml)}
                  className="bg-gray-600 hover:bg-gray-500 text-white text-xs px-2 py-0.5 rounded">
                  {showYaml ? t.designer.hideYaml : t.designer.showYaml}
                </button>
              )}
              <button onClick={() => { setToast(null); setDeployedYaml(null); setShowYaml(false); }}
                className="text-gray-400 hover:text-white text-xs">✕</button>
            </div>
          </div>
          {deployedYaml && showYaml && (
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
              <span className="text-xs text-gray-600 italic">{t.designer.noVariables}</span>
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
          <div className="p-2 border-b border-gray-700 flex items-center gap-1">
            <span className="text-xs text-gray-500 flex-1">{t.designer.draftList}</span>
            <button onClick={loadTemplates}
              className="text-xs bg-purple-600 hover:bg-purple-500 text-white px-1.5 py-0.5 rounded" title="模板">
              T
            </button>
            <button onClick={importYamlAction}
              className="text-xs bg-teal-600 hover:bg-teal-500 text-white px-1.5 py-0.5 rounded" title="导入 YAML">
              {t.designer.import}
            </button>
            <button onClick={newDraft}
              className="text-xs bg-blue-600 hover:bg-blue-500 text-white px-1.5 py-0.5 rounded">
              {t.designer.newDraft}
            </button>
          </div>
          <div className="flex-1 overflow-y-auto">
            {drafts.map(d => (
              <div key={d.id}
                onClick={() => switchDraft(d.id)}
                onContextMenu={(e) => { e.preventDefault(); setDraftMenu({x:e.clientX, y:e.clientY, draft:d}); }}
                className={`px-2 py-1.5 cursor-pointer border-b border-gray-800 text-xs flex justify-between items-center group
                  ${d.id === activeId ? 'bg-blue-600/30 border-l-2 border-l-blue-500' : 'hover:bg-gray-700'}`}>
                <div className="truncate flex-1">
                  <div className="text-gray-300 truncate">{d.name} <span className="text-[10px] text-gray-600">v{d.version || 1}</span></div>
                  <div className="text-[10px] text-gray-600">
                    {d.nodes.length} nodes
                    {(() => {
                      const count = instances.filter(i => i.definitionId === d.name).length;
                      if (count > 0) {
                        const running = instances.filter(i => i.definitionId === d.name && i.status === 'RUNNING').length;
                        return <span className="ml-1">| <span className="text-green-500">{running} running</span> / {count} total</span>;
                      }
                      return null;
                    })()}
                  </div>
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
                {t.designer.clickNew}
              </div>
            )}
          </div>
          {/* Draft context menu */}
          {draftMenu && (
            <div className="fixed z-50 bg-gray-800 border border-gray-600 rounded shadow-xl py-1 min-w-[130px]"
              style={{ left: draftMenu.x, top: draftMenu.y }} onClick={e => e.stopPropagation()}>
              <div className="px-3 py-1 text-xs text-gray-500 border-b border-gray-700">
                {draftMenu.draft.name}
              </div>
              <button onClick={() => {
                const d = draftMenu.draft.id === activeId ? { ...draftMenu.draft, nodes, edges } : draftMenu.draft;
                const yaml = graphToYaml(d.nodes, d.edges, d.id, d.name);
                setDeployedYaml(yaml);
                setShowYaml(true);
                setToast(`YAML for: ${draftMenu.draft.name}`);
                setDraftMenu(null);
              }}
                className="w-full text-left px-3 py-1.5 text-sm text-gray-300 hover:bg-gray-700">
                {t.designer.viewYaml}
              </button>
              <button onClick={() => { setDraftMenu(null); downloadYaml(draftMenu.draft); }}
                className="w-full text-left px-3 py-1.5 text-sm text-gray-300 hover:bg-gray-700">
                {t.designer.downloadYaml}
              </button>
              <div className="border-t border-gray-700" />
              <button onClick={() => { setDraftMenu(null); copyDraftAction(draftMenu.draft.id); }}
                className="w-full text-left px-3 py-1.5 text-sm text-gray-300 hover:bg-gray-700">
                {t.designer.copy}
              </button>
              <button onClick={() => { setDraftMenu(null); renameDraft(draftMenu.draft.id); }}
                className="w-full text-left px-3 py-1.5 text-sm text-gray-300 hover:bg-gray-700">
                {t.designer.rename}
              </button>
              <button onClick={() => { setDraftMenu(null); delDraft(draftMenu.draft.id); }}
                className="w-full text-left px-3 py-1.5 text-sm text-red-400 hover:bg-gray-700">
                {t.designer.delete}
              </button>
            </div>
          )}
        </div>

        <NodePalette />
        <FlowCanvas
          nodes={nodes} edges={edges}
          onNodesChange={onNodesChange} onEdgesChange={onEdgesChange}
          setNodes={setNodes} setEdges={setEdges}
          onNodeSelect={handleNodeSelect}
        />
        <PropertyPanel node={selectedNode} onChange={handleNodeChange} edges={edges} onEdgesChange={setEdges} />
      </div>

      {/* Template Browser Modal */}
      {showTemplates && (
        <div className="fixed inset-0 z-50 bg-black/60 flex items-center justify-center"
          onClick={() => setShowTemplates(false)}>
          <div className="bg-gray-800 rounded-lg shadow-2xl w-[600px] max-h-[80vh] overflow-y-auto p-6"
            onClick={e => e.stopPropagation()}>
            <div className="flex items-center justify-between mb-4">
              <h2 className="text-lg text-gray-200 font-bold">流程模板</h2>
              <button onClick={() => setShowTemplates(false)}
                className="text-gray-400 hover:text-white text-xl">&times;</button>
            </div>
            <div className="grid grid-cols-1 gap-3">
              {templates.map((tpl: any) => (
                <div key={tpl.file} className="bg-gray-750 border border-gray-600 rounded-lg p-4 hover:border-purple-500 transition-colors">
                  <div className="flex items-center justify-between mb-1">
                    <span className="text-gray-200 font-semibold">{tpl.name}</span>
                    <button onClick={() => importTemplate(tpl.file)}
                      className="bg-purple-600 hover:bg-purple-500 text-white text-xs px-3 py-1 rounded">
                      使用此模板
                    </button>
                  </div>
                  <div className="text-xs text-gray-500">{tpl.desc}</div>
                </div>
              ))}
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
