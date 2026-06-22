import { useState, useEffect, useCallback, useRef } from 'react';
import type { Node, Edge } from '@xyflow/react';
import InstanceList from './InstanceList';
import InstanceFlow from './InstanceFlow';
import TaskPanel from './TaskPanel';
import { listInstances, listDefinitions, queryTasks, completeTask, getDefinitionGraph, resumeInstance, terminateInstance, deleteInstance, startInstance, apiGet, apiPost, getInstance } from '../api/client';
import { useT } from '../i18n';

interface DefGroup {
  defId: string;
  defName: string;
  instances: any[];
  instPage: number;
  instHasMore: boolean;
  instLoading: boolean;
}

export default function MonitorPage() {
  const { t } = useT();
  const [defGroups, setDefGroups] = useState<DefGroup[]>([]);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [nodes, setNodes] = useState<Node[]>([]);
  const [edges, setEdges] = useState<Edge[]>([]);
  const [tasks, setTasks] = useState<any[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [history, setHistory] = useState<any[]>([]);
  const [nodeNames, setNodeNames] = useState<Record<string,string>>({});
  const [defPage, setDefPage] = useState(1);
  const [defHasMore, setDefHasMore] = useState(true);
  const [defLoadingState, setDefLoadingState] = useState(false);
  const defLoading = useRef(false);

  // For start dropdown — load all definitions (lightweight, no nodes)
  const [allDefs, setAllDefs] = useState<any[]>([]);

  // ── Load definitions (paginated) ──
  const loadDefinitions = useCallback(async (page: number) => {
    if (defLoading.current) return;
    defLoading.current = true;
    setDefLoadingState(true);
    try {
      const r: any = await listDefinitions(page, 10);
      const list = r.items || r;
      setDefHasMore(list.length >= 10);
      if (page === 1) {
        setDefGroups(list.map((d: any) => ({
          defId: d.id, defName: d.name || d.id,
          instances: [], instPage: 0, instHasMore: true, instLoading: false
        })));
      } else {
        setDefGroups(prev => [...prev, ...list.map((d: any) => ({
          defId: d.id, defName: d.name || d.id,
          instances: [], instPage: 0, instHasMore: true, instLoading: false
        }))]);
      }
      setDefPage(page);
    } catch {} finally {
      defLoading.current = false;
      setDefLoadingState(false);
    }
  }, []);

  // ── Load instances for a specific definition ──
  const loadInstancesForDef = useCallback(async (defId: string, page: number) => {
    setDefGroups(prev => prev.map(g => {
      if (g.defId !== defId || g.instLoading) return g;
      return { ...g, instLoading: true };
    }));
    try {
      const r: any = await listInstances(page, 10, defId);
      const list = r.items || r;
      setDefGroups(prev => prev.map(g => {
        if (g.defId !== defId) return g;
        const merged = page === 1 ? list : [...g.instances, ...list];
        return { ...g, instances: merged, instPage: page, instHasMore: list.length >= 10, instLoading: false };
      }));
    } catch {
      setDefGroups(prev => prev.map(g => g.defId === defId ? { ...g, instLoading: false } : g));
    }
  }, []);

  // Initial load
  useEffect(() => { loadDefinitions(1); }, [loadDefinitions]);

  // Load instances for each newly added definition group
  useEffect(() => {
    for (const g of defGroups) {
      if (g.instPage === 0 && g.instHasMore && !g.instLoading) {
        loadInstancesForDef(g.defId, 1);
      }
    }
  }, [defGroups, loadInstancesForDef]);

  // Load all definitions for the start dropdown (once)
  useEffect(() => {
    listDefinitions(1, 10).then((r: any) => setAllDefs(r.items || r)).catch(() => {});
  }, []);

  // Poll: refresh first page of instances for each definition every 5s
  useEffect(() => {
    const poll = () => {
      for (const g of defGroups) {
        if (g.instPage > 0) {
          listInstances(1, 10, g.defId).then((r: any) => {
            const fresh = r.items || r;
            setDefGroups(prev => prev.map(pg => {
              if (pg.defId !== g.defId) return pg;
              const map = new Map(pg.instances.map((i: any) => [i.id, i]));
              for (const item of fresh) map.set(item.id, item);
              return { ...pg, instances: [...map.values()] };
            }));
          }).catch(() => {});
        }
      }
    };
    const interval = setInterval(poll, 5000);
    return () => clearInterval(interval);
  }, [defGroups]);

  // ── Auto-refresh selected instance detail every 3s ──
  useEffect(() => {
    if (!selectedId) return;
    const refresh = async () => {
      try {
        const inst = await getInstance(selectedId);
        if (!inst) return;
        // Update the instance in all groups
        setDefGroups(prev => prev.map(g => ({
          ...g, instances: g.instances.map(i => i.id === inst.id ? inst : i)
        })));
        try {
          const graph = await getDefinitionGraph(inst.definitionId, inst.definitionVersion);
          const activeIds: string[] = inst.activeNodeIds || [];
          setNodes((graph.nodes || []).map((n: any) => ({
            ...n, data: { ...n.data, active: activeIds.includes(n.id), status: activeIds.includes(n.id) ? 'active' : 'done' }
          })));
          setEdges(graph.edges || []);
        } catch {}
        try {
          const ts = await queryTasks({ instanceId: selectedId });
          setTasks(ts.filter((t: any) => t.status === 'PENDING'));
        } catch {}
        try {
          const h = await apiGet(`/instances/${selectedId}/history`);
          setHistory(h || []);
        } catch {}
      } catch {}
    };
    refresh();
    const interval = setInterval(refresh, 3000);
    return () => clearInterval(interval);
  }, [selectedId]);

  // Flatten all instances for lookup
  const allInstances = defGroups.flatMap(g => g.instances);

  const loadInstance = useCallback(async (id: string) => {
    setSelectedId(id);
    const inst = allInstances.find(i => i.id === id);
    if (!inst) return;

    setError(null);
    try {
      const graph = await getDefinitionGraph(inst.definitionId, inst.definitionVersion);
      const activeIds: string[] = inst.activeNodeIds || [];
      setNodes((graph.nodes || []).map((n: any) => ({
        ...n,
        data: { ...n.data, active: activeIds.includes(n.id), status: activeIds.includes(n.id) ? 'active' : 'done' }
      })));
      setEdges(graph.edges || []);
    } catch (e: any) { setError(e.message); setNodes([]); setEdges([]); }

    try {
      const ts = await queryTasks({ instanceId: id });
      setTasks(ts.filter((t: any) => t.status === 'PENDING'));
    } catch { setTasks([]); }
    try {
      const h = await apiGet(`/instances/${id}/history`);
      setHistory(h || []);
    } catch { setHistory([]); }
    try {
      const graph = await getDefinitionGraph(inst.definitionId, inst.definitionVersion);
      const names: Record<string,string> = {};
      (graph.nodes||[]).forEach((n:any) => { names[n.id] = n.data?.name || n.id; });
      setNodeNames(names);
    } catch {}
  }, [allInstances]);

  const handleComplete = async (taskId: string) => {
    await completeTask(taskId);
    if (selectedId) loadInstance(selectedId);
  };

  const handleReject = async (taskId: string) => {
    try { await apiPost(`/tasks/${taskId}/reject`, { comment: 'rejected' }); } catch {}
    if (selectedId) loadInstance(selectedId);
  };

  const handleResume = async () => {
    if (!selectedId) return;
    await resumeInstance(selectedId);
    const inst = allInstances.find(i => i.id === selectedId);
    if (inst) loadInstancesForDef(inst.definitionId, 1);
    loadInstance(selectedId);
  };

  const handleTerminate = async () => {
    if (!selectedId) return;
    await terminateInstance(selectedId);
    const inst = allInstances.find(i => i.id === selectedId);
    if (inst) loadInstancesForDef(inst.definitionId, 1);
  };

  const refreshDef = async (defId: string) => {
    loadInstancesForDef(defId, 1);
  };

  const [startDefId, setStartDefId] = useState('');
  const [startVars, setStartVars] = useState('');

  const handleStart = async () => {
    if (!startDefId) return;
    try {
      const vars = startVars ? JSON.parse(startVars) : {};
      await startInstance(startDefId, vars);
      setStartVars('');
      refreshDef(startDefId);
    } catch (e: any) { alert('Start failed: ' + e.message); }
  };

  const statusLabel = (s: string) => {
    const m: Record<string, string> = {
      RUNNING: t.monitor.running,
      COMPLETED: t.monitor.completed,
      TERMINATED: t.monitor.terminated,
      SUSPENDED: t.monitor.suspended,
    };
    return m[s] || s;
  };

  const selectedInst = allInstances.find(i => i.id === selectedId);

  return (
    <div className="flex flex-col h-full">
      {/* Start panel */}
      <div className="bg-gray-800 border-b border-gray-700 px-4 py-2 flex items-center gap-3 text-sm">
        <span className="text-gray-400">Start:</span>
        <select value={startDefId} onChange={e => setStartDefId(e.target.value)}
          className="bg-gray-700 rounded px-2 py-1 text-white text-xs">
          <option value="">-- pick definition --</option>
          {allDefs.map((d: any) => (
            <option key={d.id} value={d.id}>{d.name || d.id} (v{d.version})</option>
          ))}
        </select>
        <input value={startVars} onChange={e => setStartVars(e.target.value)}
          placeholder='{"var":"value"}' className="bg-gray-700 rounded px-2 py-1 text-white text-xs w-32" />
        <button onClick={handleStart}
          className="bg-green-600 hover:bg-green-500 text-white text-xs px-3 py-1 rounded">
          Start Instance
        </button>
        <span className="text-[10px] text-gray-600">Variables: JSON, e.g. {"{}"}"applicant":"张三"{"}"}</span>
      </div>

      <div className="flex flex-1 overflow-hidden">
        <InstanceList
          groups={defGroups}
          selectedId={selectedId}
          onSelect={loadInstance}
          onTerminate={async (id) => {
            await terminateInstance(id);
            const inst = allInstances.find(i => i.id === id);
            if (inst) refreshDef(inst.definitionId);
          }}
          onResume={async (id) => {
            await resumeInstance(id);
            const inst = allInstances.find(i => i.id === id);
            if (inst) refreshDef(inst.definitionId);
            loadInstance(id);
          }}
          onDelete={async (id) => {
            await deleteInstance(id);
            const inst = allInstances.find(i => i.id === id);
            if (inst) refreshDef(inst.definitionId);
            setSelectedId(null);
          }}
          onRestart={async (id) => {
            const inst = allInstances.find(i => i.id === id);
            if (inst) { await startInstance(inst.definitionId, inst.variables || {}); refreshDef(inst.definitionId); }
          }}
          onLoadInstances={(defId) => {
            const g = defGroups.find(d => d.defId === defId);
            if (g && g.instHasMore && !g.instLoading) loadInstancesForDef(defId, g.instPage + 1);
          }}
          defHasMore={defHasMore}
          defLoading={defLoadingState}
          onLoadMoreDefs={() => { if (defHasMore && !defLoading.current) loadDefinitions(defPage + 1); }}
        />
        <div className="flex-1 flex flex-col">
          <InstanceFlow nodes={nodes} edges={edges} error={error || undefined} />
          {selectedInst && (
            <div className={`p-2 flex gap-2 border-t text-xs
              ${selectedInst.status === 'SUSPENDED' ? 'bg-yellow-900 border-yellow-700' :
                selectedInst.status === 'RUNNING' ? 'bg-gray-800 border-gray-700' : 'bg-gray-850 border-gray-700'}`}>
              <span className="text-gray-400 self-center">{selectedInst.id.substring(0,8)} — {statusLabel(selectedInst.status)}</span>
              <div className="flex-1" />
              {(selectedInst.status === 'RUNNING' || selectedInst.status === 'SUSPENDED') && (
                <button onClick={handleTerminate} className="bg-red-600 hover:bg-red-500 text-white px-3 py-1 rounded">{t.monitor.terminate}</button>
              )}
              {selectedInst.status === 'SUSPENDED' && (
                <button onClick={handleResume} className="bg-yellow-600 hover:bg-yellow-500 text-white px-3 py-1 rounded">{t.monitor.resume}</button>
              )}
            </div>
          )}
          <TaskPanel tasks={tasks} onComplete={handleComplete} onReject={handleReject} />
        </div>
        {/* Detail sidebar */}
        {selectedInst && (
          <div className="w-56 bg-gray-800 border-l border-gray-700 p-3 text-xs overflow-y-auto">
            <div className="text-gray-400 font-semibold mb-2">Instance Detail</div>
            <div className="text-gray-500 mb-1">ID: <span className="text-gray-300">{selectedInst.id.substring(0,8)}</span></div>
            <div className="text-gray-500 mb-1">Def: <span className="text-gray-300">{selectedInst.definitionId}</span> <span className="text-gray-600">v{selectedInst.definitionVersion||0}</span></div>
            <div className="mb-2">
              <span className={`px-1.5 py-0.5 rounded text-[10px] font-semibold
                ${selectedInst.status==='RUNNING'?'bg-green-900 text-green-400':
                  selectedInst.status==='COMPLETED'?'bg-blue-900 text-blue-400':
                  selectedInst.status==='SUSPENDED'?'bg-yellow-900 text-yellow-400':'bg-red-900 text-red-400'}`}>
                {statusLabel(selectedInst.status)}
              </span>
            </div>
            <div className="text-gray-400 font-semibold mt-3 mb-1">Active Nodes</div>
            {selectedInst.activeNodeIds?.length > 0 ? selectedInst.activeNodeIds.map((nid: string) => (
              <div key={nid} className="text-blue-400 mb-0.5">● {nodeNames[nid] || nid}</div>
            )) : <div className="text-gray-600">none</div>}
            <div className="text-gray-400 font-semibold mt-3 mb-1">{t.designer.variables}</div>
            {Object.keys(selectedInst.variables || {}).length > 0
              ? Object.entries(selectedInst.variables||{}).map(([k,v]) => (
                <div key={k} className="text-gray-500 mb-0.5">
                  <code className="text-blue-400">{k}</code> = <span className="text-gray-300">{String(v)}</span>
                </div>
              ))
              : <div className="text-gray-600">none</div>}
            <div className="text-gray-400 font-semibold mt-3 mb-1">{t.monitor.history} ({history.length})</div>
            {history.slice(-10).reverse().map((h:any,i:number) => (
              <div key={i} className="mb-1 pl-2 border-l border-gray-700">
                <div className="text-gray-300">{nodeNames[h.nodeId] || h.nodeId}</div>
                <div className="text-[10px] text-gray-500">{h.action} · {h.executor}</div>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
