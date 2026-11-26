import { useState, useCallback, useMemo, useEffect, useRef } from 'react';
import { useNodesState, useEdgesState, type Node, type Edge } from '@xyflow/react';
import { Save, Trash2, Braces, Rocket, CheckCircle2, XCircle, Eye, EyeOff, X, Wand2 } from 'lucide-react';
import NodePalette from './NodePalette';
import FlowCanvas from './FlowCanvas';
import PropertyPanel from './PropertyPanel';
import DraftList from './DraftList';
import TemplateBrowser from './TemplateBrowser';
import AiGenerateModal from './AiGenerateModal';
import { deployDefinition, listDrafts, createDraft, updateDraft, deleteDraft as removeDraft, getDraft, startInstance, copyDraft, importDraft, fetchInstanceSummary, generateFlow } from '../api/client';
import { showToast } from '../api/toast';
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

export default function DesignerPage({ onNavigate }: { onNavigate?: (t: 'designer'|'monitor', defId?: string, instId?: string) => void }) {
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
  const [deployedInstanceId, setDeployedInstanceId] = useState<string | null>(null);
  const [showTemplates, setShowTemplates] = useState(false);
  const [templates, setTemplates] = useState<any[]>([]);
  const [showAiGenerate, setShowAiGenerate] = useState(false);

  const [instanceSummary, setInstanceSummary] = useState<Record<string, {running: number, total: number}>>({});
  const [draftPage, setDraftPage] = useState(1);
  const [draftHasMore, setDraftHasMore] = useState(true);
  const [draftLoadingState, setDraftLoadingState] = useState(false);
  const draftLoading = useRef(false);

  // Fetch instance summary for per-draft counts (lightweight, no pagination)
  useEffect(() => {
    if (drafts.length > 0) fetchInstanceSummary().then(setInstanceSummary).catch(e => showToast(e.message, 'error'));
  }, [drafts.length]);

  // ── Load drafts from server on mount ──
  useEffect(() => {
    loadDrafts(1);
  }, []);

  const loadDrafts = async (page: number) => {
    if (draftLoading.current) return;
    draftLoading.current = true;
    setDraftLoadingState(true);
    try {
      const r: any = await listDrafts(page);
      const list = r.items || r;
      setDraftHasMore(list.length >= 10);
      const mapped = list.map((d: any) => ({ ...d, nodes: d.nodes || [], edges: d.edges || [] }));
      if (page === 1) {
        setDrafts(mapped);
      } else {
        setDrafts(prev => [...prev, ...mapped]);
      }
      setDraftPage(page);
    } catch (e: any) { showToast(e.message || 'Failed to load drafts', 'error'); } finally {
      draftLoading.current = false;
      setDraftLoadingState(false);
      setLoaded(true);
    }
  };

  // When switching drafts, load full data
  useEffect(() => {
    if (!activeId || !loaded) return;
    getDraft(activeId).then(d => {
      setNodes(d.nodes || []);
      setEdges(d.edges || []);
      setSelectedNode(null);
    }).catch(e => showToast(e.message, 'error'));
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
      updateDraft(activeId, { nodes, edges }).catch(e => showToast(e.message, 'error'));
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
    } catch (e: any) { showToast(e.message || t.designer.saveFailed, 'error'); }
    finally { setSaving(false); }
  };

  // ── Draft actions ─────────────────────
  const newDraft = async () => {
    try {
      const d = await createDraft(`Draft ${drafts.length + 1}`);
      setDrafts(prev => [...prev, { ...d, nodes: [], edges: [] }]);
      setActiveId(d.id);
    } catch (e: any) { showToast(`${t.designer.createFailed}: ` + (e.message || 'server unavailable'), 'error'); }
  };

  const renameDraft = async (id: string) => {
    const name = prompt(t.designer.draftName);
    if (!name) return;
    try {
      await updateDraft(id, { name });
      setDrafts(prev => prev.map(d => d.id === id ? { ...d, name } : d));
    } catch (e: any) { showToast(`${t.designer.renameFailed}: ` + (e.message || 'server error'), 'error'); }
  };

  const delDraft = async (id: string) => {
    if (!confirm(t.designer.confirmDelete)) return;
    try { await removeDraft(id); } catch (e: any) { showToast(e.message, 'error'); }
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
    } catch (e: any) { showToast(e.message || t.designer.saveFailed, 'error'); }
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

  const showYamlFor = (draft: Draft) => {
    const d = draft.id === activeId ? { ...draft, nodes, edges } : draft;
    const yaml = graphToYaml(d.nodes, d.edges, d.id, d.name);
    setDeployedYaml(yaml);
    setShowYaml(true);
    setToast(`YAML for: ${draft.name}`);
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
        showToast('Import failed: ' + e.message, 'error');
      }
    };
    input.click();
  };

  const loadTemplates = async () => {
    try {
      // no-store: nginx serves static files without Cache-Control, so browsers
      // may keep a stale manifest and hide newly added templates.
      const res = await fetch('/templates/manifest.json', { cache: 'no-store' });
      setTemplates(await res.json());
      setShowTemplates(true);
    } catch { showToast(t.designer.loadTemplatesFailed, 'error'); }
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
    } catch (e: any) { showToast('Import failed: ' + e.message, 'error'); }
  };

  const handleAiGenerate = async (prompt: string) => {
    try {
      const r: any = await generateFlow(prompt);
      const { name, nodes: genNodes, edges: genEdges } = yamlToGraph(r.yaml);
      const d = await importDraft(r.name || name, genNodes, genEdges);
      setDrafts(prev => [...prev, { ...d, nodes: genNodes, edges: genEdges }]);
      setActiveId(d.id);
      setShowAiGenerate(false);
      setToast(`AI: ${r.name || name} (${r.nodeCount ?? genNodes.length} nodes)`);
    } catch (e: any) {
      showToast(e.message || t.designer.aiGenerateFailed, 'error');
    }
  };

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
        setDeployedInstanceId(inst.id);
        setToast(`${t.designer.deployStart} Def: ${result.id}, Instance: ${inst.id.substring(0,8)}`);
        fetchInstanceSummary().then(setInstanceSummary).catch(e => showToast(e.message, 'error'));
      } catch {
        setDeployedInstanceId(null);
        setToast(`${t.designer.deployOnly}${result.id} (auto-start failed)`);
      }
    } catch (e: any) { showToast(t.designer.deployFailed + e.message, 'error'); }
  };

  // ── Variables ─────────────────────────
  const allVars = useMemo<VarInfo[]>(() => {
    const vars: VarInfo[] = [];
    for (const node of nodes) {
      if (node.type === 'startEvent') {
        for (const v of (node.data.initialVars as Array<{key:string;value:string}>) || []) {
          if (v.key) vars.push({ name: v.key, source: v.value ? `Start (${t.designer.defaultVar}: ${v.value})` : 'Start' });
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
      if (node.type === 'aiTask') {
        const label = (node.data.mode as string) || 'AI';
        const nodeName = (node.data.name as string) || node.id;
        vars.push({ name: `${node.id}_text`, source: `「${nodeName}」AI · ${label} → 原始输出` });
        vars.push({ name: `${node.id}_result`, source: `「${nodeName}」AI · ${label} → 完整结果` });
        if (node.data.mode === 'classify') vars.push({ name: `${node.id}_category`, source: `「${nodeName}」AI 分类结果` });
        if (node.data.outputVar) vars.push({ name: String(node.data.outputVar), source: `「${nodeName}」AI 输出变量` });
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
      <div className="bg-gray-850 border-b border-gray-700/70 px-4 h-11 flex justify-between items-center">
        <div className="flex items-center gap-2.5">
          <span className="text-xs text-gray-300 font-medium">
            {activeDraft ? `${activeDraft.name}` : t.designer.noDraft}
          </span>
          {activeDraft && <span className="text-[10px] text-gray-600 font-mono bg-gray-800 px-1.5 py-0.5 rounded">v{activeDraft.version || 1}</span>}
          <div className="w-px h-4 bg-gray-700 mx-1" />
          <button onClick={doSave} className="btn-primary">
            <Save size={13} strokeWidth={2} />
            {saving ? t.designer.saving : t.designer.save}
          </button>
          {selectedNode && (
            <button onClick={handleDeleteNode} className="btn-ghost hover:!bg-red-600 hover:!text-white">
              <Trash2 size={13} strokeWidth={2} />
              {t.designer.deleteNode}
            </button>
          )}
          <button onClick={() => setShowVars(!showVars)}
            className={`text-xs px-2.5 py-1.5 rounded-md font-medium transition-colors flex items-center gap-1.5
              ${showVars ? 'bg-blue-600 text-white' : 'bg-gray-800 text-gray-400 hover:text-gray-200'}`}>
            <Braces size={13} strokeWidth={2} />
            Variables ({allVars.length})
          </button>
        </div>
        <button onClick={handleDeploy} className="btn-success !text-[13px] !px-4">
          <Rocket size={14} strokeWidth={2.2} />
          {t.designer.deploy}
        </button>
      </div>

      {/* Toast + YAML preview — keeps bg-green-900 for E2E toast detection */}
      {toast && (
        <div className="bg-green-900 border-b border-green-700/70 px-4 py-2.5">
          <div className="flex items-center gap-2.5">
            <CheckCircle2 size={15} className="text-green-400 shrink-0" strokeWidth={2} />
            <span className="text-xs text-green-200 flex-1">{toast}</span>
            <div className="flex gap-1.5">
              {deployedId && (
                <button onClick={() => onNavigate?.('monitor', deployedId, deployedInstanceId || undefined)}
                  className="bg-blue-600 hover:bg-blue-500 text-white text-xs px-2.5 py-1 rounded-md font-medium flex items-center gap-1.5">
                  {t.designer.viewInMonitor}
                </button>
              )}
              {deployedYaml && (
                <button onClick={() => setShowYaml(!showYaml)}
                  className="bg-gray-700 hover:bg-gray-600 text-gray-200 text-xs px-2.5 py-1 rounded-md flex items-center gap-1.5">
                  {showYaml ? <EyeOff size={12} /> : <Eye size={12} />}
                  {showYaml ? t.designer.hideYaml : t.designer.showYaml}
                </button>
              )}
              <button onClick={() => { setToast(null); setDeployedYaml(null); setShowYaml(false); }}
                className="btn-icon !w-6 !h-6 hover:!text-white" aria-label="Close">
                <X size={14} />
                <span className="sr-only">✕</span>
              </button>
            </div>
          </div>
          {deployedYaml && showYaml && (
            <pre className="mt-2.5 bg-gray-950/80 rounded-md p-3 text-[11px] text-emerald-300 overflow-auto max-h-48 font-mono border border-gray-800 leading-relaxed">
              {deployedYaml}
            </pre>
          )}
        </div>
      )}

      {/* Variables Panel */}
      {showVars && (
        <div className="bg-gray-850 border-b border-gray-700/70 px-4 py-2.5">
          <div className="flex flex-wrap gap-1.5">
            {allVars.length === 0 ? (
              <span className="text-[11px] text-gray-600 italic">{t.designer.noVariables}</span>
            ) : allVars.map((v, i) => (
              <span key={i} className="inline-flex items-center gap-1.5 bg-gray-800 border border-gray-700/70
                       rounded-md px-2.5 py-1 text-[11px] text-gray-300" title={v.source}>
                <Braces size={10} className="text-blue-400" />
                <code className="text-blue-400 font-mono">{v.name}</code>
                <span className="text-[10px] text-gray-600">← {v.source}</span>
              </span>
            ))}
          </div>
        </div>
      )}

      {/* Main */}
      {!loaded ? (
        <div className="flex-1 flex items-center justify-center text-gray-500 text-sm">
          <div className="flex items-center gap-2">
            <svg className="animate-spin h-4 w-4" viewBox="0 0 24 24" fill="none">
              <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
              <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
            </svg>
            {t.dashboard.loading}
          </div>
        </div>
      ) : (
      <div className="flex flex-1 overflow-hidden">
        <DraftList
          drafts={drafts}
          activeId={activeId}
          instanceSummary={instanceSummary}
          draftHasMore={draftHasMore}
          draftLoading={draftLoadingState}
          draftPage={draftPage}
          onSwitch={setActiveId}
          onNew={newDraft}
          onImport={importYamlAction}
          onLoadTemplates={loadTemplates}
          onAiGenerate={() => setShowAiGenerate(true)}
          onLoadMore={() => { if (draftHasMore && !draftLoading.current) loadDrafts(draftPage + 1); }}
          onLoadPage={loadDrafts}
          onViewYaml={showYamlFor}
          onDownloadYaml={downloadYaml}
          onCopy={copyDraftAction}
          onRename={renameDraft}
          onDelete={delDraft}
        />

        <NodePalette />
        <FlowCanvas
          nodes={nodes} edges={edges}
          onNodesChange={onNodesChange} onEdgesChange={onEdgesChange}
          setNodes={setNodes} setEdges={setEdges}
          onNodeSelect={handleNodeSelect}
        />
        <PropertyPanel node={selectedNode} onChange={handleNodeChange} edges={edges} onEdgesChange={setEdges} />
      </div>
      )}

      {/* Template Browser Modal */}
      {showTemplates && (
        <TemplateBrowser templates={templates} onImport={importTemplate} onClose={() => setShowTemplates(false)} />
      )}

      {/* AI Generate Modal */}
      {showAiGenerate && (
        <AiGenerateModal onGenerate={handleAiGenerate} onClose={() => setShowAiGenerate(false)} />
      )}
    </div>
  );
}
