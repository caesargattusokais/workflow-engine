import { useState, useEffect, useCallback, useRef } from 'react';
import type { Node, Edge } from '@xyflow/react';
import { Play, RefreshCw, Square, PlayCircle, XCircle } from 'lucide-react';
import InstanceList from './InstanceList';
import InstanceFlow from './InstanceFlow';
import TaskPanel from './TaskPanel';
import InstanceDetailSidebar from './InstanceDetailSidebar';
import { useInstanceSocket, useMonitorSocket } from './monitorSockets';
import { listInstances, listDefinitions, queryTasks, completeTask, getDefinitionGraph, resumeInstance, terminateInstance, deleteInstance, startInstance, apiGet, apiPost, getInstance } from '../api/client';
import { showToast } from '../api/toast';
import { useT } from '../i18n';

/** Normalize backend graph nodes (flat x/y) to ReactFlow format (position: {x,y}). */
function normalizeNodes(nodes: any[], activeIds: string[]): Node[] {
  return (nodes || []).map((n: any) => ({
    id: n.id,
    type: n.type,
    position: { x: n.x ?? n.position?.x ?? 200, y: n.y ?? n.position?.y ?? 50 },
    data: { ...n.data, active: activeIds.includes(n.id), status: activeIds.includes(n.id) ? 'active' : 'done' },
  }));
}

interface DefGroup {
  defId: string;
  defName: string;
  instances: any[];
  instPage: number;
  instHasMore: boolean;
  instLoading: boolean;
}

export default function MonitorPage({ initialDefId, initialInstId }:
    { initialDefId?: string | null; initialInstId?: string | null }) {
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
  const [statusFilter, setStatusFilter] = useState('');

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
    } catch (e: any) { showToast(e.message || 'Failed to load definitions', 'error'); } finally {
      defLoading.current = false;
      setDefLoadingState(false);
    }
  }, []);

  // ── Load instances for a specific definition ──
  // Per-definition load counters — stale responses never overwrite fresh ones
  const defLoadIdRef = useRef<Record<string, number>>({});
  const loadInstancesForDef = useCallback(async (defId: string, page: number) => {
    const loadId = (defLoadIdRef.current[defId] = (defLoadIdRef.current[defId] || 0) + 1);
    setDefGroups(prev => prev.map(g => {
      if (g.defId !== defId || g.instLoading) return g;
      return { ...g, instLoading: true };
    }));
    try {
      const r: any = await listInstances(page, 10, defId, statusFilter || undefined);
      if (defLoadIdRef.current[defId] !== loadId) return; // stale response
      const list = r.items || r;
      setDefGroups(prev => prev.map(g => {
        if (g.defId !== defId) return g;
        const merged = page === 1 ? list : [...g.instances, ...list];
        return { ...g, instances: merged, instPage: page, instHasMore: list.length >= 10, instLoading: false };
      }));
    } catch (e: any) {
      if (defLoadIdRef.current[defId] !== loadId) return; // stale error
      showToast(e.message || 'Failed to load instances', 'error');
      setDefGroups(prev => prev.map(g => g.defId === defId ? { ...g, instLoading: false } : g));
    }
  }, [statusFilter]);

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

  // Load ALL definitions for the start dropdown — paginate until exhausted
  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const collected: any[] = [];
        for (let page = 1; ; page++) {
          const r: any = await listDefinitions(page, 50);
          const list = r.items || r;
          collected.push(...list);
          if (list.length < 50 || cancelled) break;
        }
        if (!cancelled) setAllDefs(collected);
      } catch (e: any) { if (!cancelled) showToast(e.message, 'error'); }
    })();
    return () => { cancelled = true; };
  }, []);

  // Manual refresh: reload definitions page 1, then refresh instances for all loaded defs
  const refreshIdRef = useRef(0);
  const refreshAll = useCallback(() => {
    const refreshId = ++refreshIdRef.current;
    // Reload first page of definitions to catch new ones
    loadDefinitions(1);
    // Refresh instances for all currently loaded definitions (including empty ones)
    for (const g of defGroups) {
      listInstances(1, 10, g.defId, statusFilter || undefined).then((r: any) => {
        if (refreshIdRef.current !== refreshId) return; // stale refresh — drop
        const fresh = r.items || r;
        setDefGroups(prev => prev.map(pg => {
          if (pg.defId !== g.defId) return pg;
          const map = new Map(pg.instances.map((i: any) => [i.id, i]));
          for (const item of fresh) map.set(item.id, item);
          return { ...pg, instances: [...map.values()] };
        }));
      }).catch(e => showToast(e.message, 'error'));
    }
  }, [defGroups, statusFilter, loadDefinitions]);

  // ── WebSocket real-time updates (per-instance + global list) ──

  // Track latest load request to suppress stale responses
  const loadIdRef = useRef(0);
  const allInstancesRef = useRef<any[]>([]);
  const loadInstanceRef = useRef<(id: string) => void>(() => {});
  const loadInstancesForDefRef = useRef<(defId: string, page: number) => void>(() => {});

  const loadInstance = useCallback(async (id: string) => {
    const loadId = ++loadIdRef.current;
    setSelectedId(id);
    setError(null);
    try {
      // Fetch fresh instance from server, NOT stale allInstances from closure
      const inst = await getInstance(id);
      if (!inst) return;
      // Guard: stale request — another loadInstance call has started since
      if (loadId !== loadIdRef.current) return;
      const graph = await getDefinitionGraph(inst.definitionId, inst.definitionVersion);
      if (loadId !== loadIdRef.current) return;
      const activeIds: string[] = inst.activeNodeIds || [];
      setNodes(normalizeNodes(graph.nodes, activeIds));
      setEdges(graph.edges || []);
      // Build node name map for sidebar
      const names: Record<string,string> = {};
      (graph.nodes||[]).forEach((n:any) => { names[n.id] = n.data?.name || n.id; });
      setNodeNames(names);
    } catch (e: any) {
      // Only update error/nodes if this is still the latest request
      if (loadId === loadIdRef.current) {
        setError(e.message);
        // Don't clear nodes — WS snapshot may have already set correct ones
      }
    }
    try {
      const ts = await queryTasks({ instanceId: id });
      if (loadId !== loadIdRef.current) return;
      setTasks(ts.filter((t: any) => t.status === 'PENDING'));
    } catch (e: any) {
      if (loadId === loadIdRef.current) { setTasks([]); showToast(e.message, 'error'); }
    }
    try {
      const h = await apiGet(`/instances/${id}/history`);
      if (loadId === loadIdRef.current) setHistory(h || []);
    } catch {
      if (loadId === loadIdRef.current) setHistory([]);
    }
  }, []);
  loadInstanceRef.current = loadInstance;
  loadInstancesForDefRef.current = loadInstancesForDef;

  useInstanceSocket(selectedId, (msg) => {
    const inst = msg.instance;
    setDefGroups(prev => prev.map(g => ({
      ...g, instances: g.instances.map(i => i.id === inst.id ? { ...i, ...inst, status: inst.status, activeNodeIds: inst.activeNodeIds, variables: inst.variables } : i)
    })));
    if (msg.graph) {
      const activeIds: string[] = inst.activeNodeIds || [];
      setNodes(normalizeNodes(msg.graph.nodes, activeIds));
      setEdges(msg.graph.edges || []);
    } else {
      const activeIds: string[] = inst.activeNodeIds || [];
      setNodes(prev => {
        // Only update styling if nodes belong to this instance (idempotent guard)
        if (prev.length === 0) return prev;
        return prev.map(n => ({
          ...n,
          data: { ...n.data, active: activeIds.includes(n.id), status: activeIds.includes(n.id) ? 'active' : 'done' }
        }));
      });
    }
    setTasks((msg.tasks || []).filter((t: any) => t.status === 'PENDING'));
    if (msg.history) setHistory(msg.history);
  });

  useMonitorSocket((msg) => {
    setDefGroups(prev => {
      let found = false;
      const next = prev.map(g => ({
        ...g,
        instances: g.instances.map(i => i.id === msg.instanceId
          ? (found = true, { ...i, status: msg.status, activeNodeIds: msg.activeNodeIds || [] })
          : i)
      }));
      if (!found && msg.definitionId) {
        getInstance(msg.instanceId).then(inst => {
          if (inst) {
            setDefGroups(prev2 => {
              const g = prev2.find(x => x.defId === msg.definitionId);
              if (g) {
                if (g.instances.some(i => i.id === inst.id)) return prev2; // already exists
                return prev2.map(x => x.defId === msg.definitionId
                  ? { ...x, instances: [inst, ...x.instances] } : x);
              }
              return [...prev2, {
                defId: msg.definitionId, defName: inst.definitionId,
                instances: [inst], instPage: 1, instHasMore: false, instLoading: false
              }];
            });
          }
        }).catch(e => console.error('[WS monitor] fetch error', e));
      }
      return found ? next : prev;
    });
  });

  // Auto-select deployed instance when navigating from designer
  const autoSelectConsumed = useRef(false);
  useEffect(() => {
    if (!initialDefId || !initialInstId || autoSelectConsumed.current) return;

    const group = defGroups.find(g => g.defId === initialDefId);
    if (!group) {
      // Definition not loaded yet — add a placeholder so load-instances picks it up
      setDefGroups(prev => {
        if (prev.some(g => g.defId === initialDefId)) return prev;
        return [...prev, {
          defId: initialDefId, defName: initialDefId,
          instances: [], instPage: 0, instHasMore: true, instLoading: false
        }];
      });
      return;
    }
    // Wait for instances to be loaded
    const inst = group.instances.find(i => i.id === initialInstId);
    if (!inst) return;

    autoSelectConsumed.current = true;
    loadInstance(initialInstId);
  }, [initialDefId, initialInstId, defGroups, loadInstance]);

  // ── Shared instance actions (used by list + detail bar) ──
  const refreshDef = useCallback((defId: string) => {
    loadInstancesForDefRef.current(defId, 1);
  }, []);

  const doTerminate = useCallback(async (id: string) => {
    try {
      await terminateInstance(id);
      const inst = allInstancesRef.current.find(i => i.id === id);
      if (inst) refreshDef(inst.definitionId);
    } catch (e: any) { showToast(e.message || 'Failed to terminate instance', 'error'); }
    loadInstanceRef.current(id);
  }, [refreshDef]);

  const doResume = useCallback(async (id: string) => {
    try {
      await resumeInstance(id);
      const inst = allInstancesRef.current.find(i => i.id === id);
      if (inst) refreshDef(inst.definitionId);
    } catch (e: any) { showToast(e.message || 'Failed to resume instance', 'error'); }
    loadInstanceRef.current(id);
  }, [refreshDef]);

  const doDelete = useCallback(async (id: string) => {
    try {
      await deleteInstance(id);
      const inst = allInstancesRef.current.find(i => i.id === id);
      if (inst) refreshDef(inst.definitionId);
    } catch (e: any) { showToast(e.message || 'Failed to delete instance', 'error'); }
    setSelectedId(null);
  }, [refreshDef]);

  const doRestart = useCallback(async (id: string) => {
    const inst = allInstancesRef.current.find(i => i.id === id);
    if (!inst) return;
    try {
      await startInstance(inst.definitionId, inst.variables || {});
      refreshDef(inst.definitionId);
    } catch (e: any) { showToast(e.message || 'Failed to restart instance', 'error'); }
  }, [refreshDef]);

  // Keep a ref to the flattened instance list for the async action callbacks
  const allInstances = defGroups.flatMap(g => g.instances);
  allInstancesRef.current = allInstances;

  const handleComplete = async (taskId: string, vars: Record<string, unknown>, comment: string) => {
    try {
      await completeTask(taskId, vars, comment);
    } catch (e: any) { showToast(e.message || 'Failed to complete task', 'error'); }
    if (selectedId) loadInstance(selectedId);
  };

  const handleReject = async (taskId: string) => {
    try { await apiPost(`/tasks/${taskId}/reject`, { comment: 'rejected' }); } catch (e: any) { showToast(e.message, 'error'); }
    if (selectedId) loadInstance(selectedId);
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
    } catch (e: any) { showToast('Start failed: ' + e.message, 'error'); }
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
      <div className="bg-gray-850 border-b border-gray-700/70 px-4 h-11 flex items-center gap-2.5">
        <span className="text-[11px] uppercase tracking-wider text-gray-500 font-medium">Start</span>
        <select value={startDefId} onChange={e => setStartDefId(e.target.value)}
          className="bg-gray-800 border border-gray-700 rounded-md px-2.5 py-1.5 text-gray-200 text-xs
                     hover:border-gray-600 focus:border-blue-500 transition-colors max-w-[240px]">
          <option value="">-- pick definition --</option>
          {allDefs.map((d: any) => (
            <option key={d.id} value={d.id}>{d.name || d.id} (v{d.version})</option>
          ))}
        </select>
        <input value={startVars} onChange={e => setStartVars(e.target.value)}
          placeholder='{"var":"value"}'
          className="bg-gray-800 border border-gray-700 rounded-md px-2.5 py-1.5 text-gray-200 text-xs font-mono w-40
                     placeholder:text-gray-600 hover:border-gray-600 focus:border-blue-500 transition-colors" />
        <button onClick={handleStart} className="btn-success">
          <Play size={12} fill="currentColor" strokeWidth={2} />
          Start Instance
        </button>
        <span className="text-[10px] text-gray-600 ml-1">Variables: JSON, e.g. {"{}"}"applicant":"user1"{"}"}</span>
      </div>

      <div className="flex flex-1 overflow-hidden">
        <InstanceList
          groups={defGroups}
          selectedId={selectedId}
          onSelect={loadInstance}
          onTerminate={doTerminate}
          onResume={doResume}
          onDelete={doDelete}
          onRestart={doRestart}
          onLoadInstances={(defId) => {
            const g = defGroups.find(d => d.defId === defId);
            if (g && g.instHasMore && !g.instLoading) loadInstancesForDef(defId, g.instPage + 1);
          }}
          defHasMore={defHasMore}
          defLoading={defLoadingState}
          onLoadMoreDefs={() => { if (defHasMore && !defLoading.current) loadDefinitions(defPage + 1); }}
          onRefresh={refreshAll}
          statusFilter={statusFilter}
          onStatusChange={(s) => {
            const statusId = ++refreshIdRef.current;
            setStatusFilter(s);
            for (const g of defGroups) {
              listInstances(1, 10, g.defId, s || undefined).then((r: any) => {
                if (refreshIdRef.current !== statusId) return; // stale — a newer filter/refresh won
                const list = r.items || r;
                setDefGroups(prev => prev.map(pg => pg.defId === g.defId
                  ? { ...pg, instances: list, instPage: 1, instHasMore: list.length >= 10 }
                  : pg));
              }).catch(e => showToast(e.message, 'error'));
            }
          }}
        />
        <div className="flex-1 flex flex-col">
          <InstanceFlow nodes={nodes} edges={edges} error={error || undefined} />
          {selectedInst && (
            <div className={`px-4 py-2 flex gap-2.5 items-center border-t text-xs
              ${selectedInst.status === 'SUSPENDED' ? 'bg-amber-950/40 border-amber-800/40' :
                'bg-gray-850 border-gray-700/70'}`}>
              <span className="text-gray-400 font-mono text-[11px]">{selectedInst.id.substring(0,8)}</span>
              <span className="text-gray-700">—</span>
              <span className={`text-[11px] font-medium ${
                selectedInst.status === 'RUNNING' ? 'text-emerald-400' :
                selectedInst.status === 'COMPLETED' ? 'text-blue-400' :
                selectedInst.status === 'SUSPENDED' ? 'text-amber-400' : 'text-red-400'}`}>
                {statusLabel(selectedInst.status)}
              </span>
              <div className="flex-1" />
              {(selectedInst.status === 'RUNNING' || selectedInst.status === 'SUSPENDED') && (
                <button onClick={() => doTerminate(selectedId!)} className="btn-ghost hover:!bg-red-600 hover:!text-white">
                  <Square size={12} fill="currentColor" strokeWidth={1} />
                  {t.monitor.terminate}
                </button>
              )}
              {selectedInst.status === 'SUSPENDED' && (
                <button onClick={() => doResume(selectedId!)} className="btn-ghost !bg-amber-600/20 !text-amber-300 hover:!bg-amber-600 hover:!text-white">
                  <PlayCircle size={12} strokeWidth={2} />
                  {t.monitor.resume}
                </button>
              )}
            </div>
          )}
          <TaskPanel tasks={tasks} onComplete={handleComplete} onReject={handleReject} />
        </div>
        {/* Detail sidebar */}
        {selectedInst && (
          <InstanceDetailSidebar inst={selectedInst} nodeNames={nodeNames} history={history} />
        )}
      </div>
    </div>
  );
}
