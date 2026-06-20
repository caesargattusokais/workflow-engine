import { useState, useEffect, useCallback } from 'react';
import type { Node, Edge } from '@xyflow/react';
import InstanceList from './InstanceList';
import InstanceFlow from './InstanceFlow';
import TaskPanel from './TaskPanel';
import { listInstances, queryTasks, completeTask, getDefinitionGraph, resumeInstance, terminateInstance, deleteInstance, startInstance, apiGet, apiPost, authHeaders, getInstance } from '../api/client';
import { useT } from '../i18n';

export default function MonitorPage() {
  const { t } = useT();
  const [instances, setInstances] = useState<any[]>([]);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [nodes, setNodes] = useState<Node[]>([]);
  const [edges, setEdges] = useState<Edge[]>([]);
  const [tasks, setTasks] = useState<any[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [history, setHistory] = useState<any[]>([]);
  const [nodeNames, setNodeNames] = useState<Record<string,string>>({});

  useEffect(() => {
    const poll = () => listInstances().then(setInstances).catch(() => {});
    poll();
    const interval = setInterval(poll, 5000);
    return () => clearInterval(interval);
  }, []);

  // Auto-refresh selected instance detail every 3s
  useEffect(() => {
    if (!selectedId) return;
    const refresh = async () => {
      try {
        const inst = await getInstance(selectedId);
        if (!inst) return;
        // Update the instance in the list
        setInstances(prev => prev.map(i => i.id === inst.id ? inst : i));
        // Refresh graph with active nodes
        try {
          const graph = await getDefinitionGraph(inst.definitionId, inst.definitionVersion);
          const activeIds: string[] = inst.activeNodeIds || [];
          setNodes((graph.nodes || []).map((n: any) => ({
            ...n, data: { ...n.data, active: activeIds.includes(n.id), status: activeIds.includes(n.id) ? 'active' : 'done' }
          })));
          setEdges(graph.edges || []);
        } catch {}
        // Refresh tasks
        try {
          const ts = await queryTasks({ instanceId: selectedId });
          setTasks(ts.filter((t: any) => t.status === 'PENDING'));
        } catch {}
        // Refresh history
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

  const loadInstance = useCallback(async (id: string) => {
    setSelectedId(id);
    const inst = instances.find(i => i.id === id);
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
    // Build node name map from graph
    try {
      const graph = await getDefinitionGraph(inst.definitionId, inst.definitionVersion);
      const names: Record<string,string> = {};
      (graph.nodes||[]).forEach((n:any) => { names[n.id] = n.data?.name || n.id; });
      setNodeNames(names);
    } catch {}
  }, [instances]);

  const handleComplete = async (taskId: string) => {
    await completeTask(taskId);
    if (selectedId) loadInstance(selectedId);
  };

  const handleReject = async (taskId: string) => {
    try {
      await apiPost(`/tasks/${taskId}/reject`, { comment: 'rejected' });
    } catch {}
    if (selectedId) loadInstance(selectedId);
  };

  const handleResume = async () => {
    if (!selectedId) return;
    await resumeInstance(selectedId);
    listInstances().then(setInstances);
    loadInstance(selectedId);
  };

  const handleTerminate = async () => {
    if (!selectedId) return;
    await terminateInstance(selectedId);
    listInstances().then(setInstances);
  };

  const [definitions, setDefinitions] = useState<any[]>([]);
  const [startDefId, setStartDefId] = useState('');
  const [startVars, setStartVars] = useState('');

  useEffect(() => {
    apiGet('/definitions').then(setDefinitions).catch(() => {});
  }, [instances]);

  const handleStart = async () => {
    if (!startDefId) return;
    try {
      const vars = startVars ? JSON.parse(startVars) : {};
      await startInstance(startDefId, vars);
      setStartVars('');
      listInstances().then(setInstances);
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

  const selectedInst = instances.find(i => i.id === selectedId);

  return (
    <div className="flex flex-col h-full">
      {/* Start panel */}
      <div className="bg-gray-800 border-b border-gray-700 px-4 py-2 flex items-center gap-3 text-sm">
        <span className="text-gray-400">Start:</span>
        <select value={startDefId} onChange={e => setStartDefId(e.target.value)}
          className="bg-gray-700 rounded px-2 py-1 text-white text-xs">
          <option value="">-- pick definition --</option>
          {definitions.map((d: any) => (
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
        <InstanceList instances={instances} selectedId={selectedId} onSelect={loadInstance}
          defNames={Object.fromEntries(definitions.map((d:any) => [d.id, d.name || d.id]))}
          onTerminate={async (id) => { await terminateInstance(id); listInstances().then(setInstances); }}
          onResume={async (id) => { await resumeInstance(id); listInstances().then(setInstances); loadInstance(id); }}
          onDelete={async (id) => { await deleteInstance(id); listInstances().then(setInstances); setSelectedId(null); }}
          onRestart={async (id) => {
            const inst = instances.find(i => i.id === id);
            if (inst) { await startInstance(inst.definitionId, inst.variables || {}); listInstances().then(setInstances); }
          }} />
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

            {/* Active nodes */}
            <div className="text-gray-400 font-semibold mt-3 mb-1">Active Nodes</div>
            {selectedInst.activeNodeIds?.length > 0 ? selectedInst.activeNodeIds.map((nid: string) => (
              <div key={nid} className="text-blue-400 mb-0.5">● {nodeNames[nid] || nid}</div>
            )) : <div className="text-gray-600">none</div>}

            {/* Variables */}
            <div className="text-gray-400 font-semibold mt-3 mb-1">{t.designer.variables}</div>
            {Object.keys(selectedInst.variables || {}).length > 0
              ? Object.entries(selectedInst.variables||{}).map(([k,v]) => (
                <div key={k} className="text-gray-500 mb-0.5">
                  <code className="text-blue-400">{k}</code> = <span className="text-gray-300">{String(v)}</span>
                </div>
              ))
              : <div className="text-gray-600">none</div>}

            {/* History */}
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
