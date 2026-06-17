import { useState, useEffect, useCallback } from 'react';
import type { Node, Edge } from '@xyflow/react';
import InstanceList from './InstanceList';
import InstanceFlow from './InstanceFlow';
import TaskPanel from './TaskPanel';
import { listInstances, queryTasks, completeTask, getDefinitionGraph, resumeInstance, terminateInstance, deleteInstance, startInstance } from '../api/client';

export default function MonitorPage() {
  const [instances, setInstances] = useState<any[]>([]);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [nodes, setNodes] = useState<Node[]>([]);
  const [edges, setEdges] = useState<Edge[]>([]);
  const [tasks, setTasks] = useState<any[]>([]);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const poll = () => listInstances().then(setInstances).catch(() => {});
    poll();
    const interval = setInterval(poll, 3000);
    return () => clearInterval(interval);
  }, []);

  const loadInstance = useCallback(async (id: string) => {
    setSelectedId(id);
    const inst = instances.find(i => i.id === id);
    if (!inst) return;

    setError(null);
    try {
      const graph = await getDefinitionGraph(inst.definitionId);
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
  }, [instances]);

  const handleComplete = async (taskId: string) => {
    await completeTask(taskId);
    if (selectedId) loadInstance(selectedId);
  };

  const handleReject = async (taskId: string) => {
    try {
      await fetch(`/api/tasks/${taskId}/reject`, {
        method: 'POST', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ comment: 'rejected' })
      });
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
    fetch('/api/definitions').then(r => r.json()).then(setDefinitions).catch(() => {});
  }, [instances]);

  const handleStart = async () => {
    if (!startDefId) return;
    try {
      const vars = startVars ? JSON.parse(startVars) : {};
      await fetch('/api/instances', {
        method: 'POST', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ definitionId: startDefId, variables: vars })
      });
      setStartVars('');
      listInstances().then(setInstances);
    } catch (e: any) { alert('Start failed: ' + e.message); }
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
          onTerminate={async (id) => { await terminateInstance(id); listInstances().then(setInstances); }}
          onResume={async (id) => { await resumeInstance(id); listInstances().then(setInstances); loadInstance(id); }}
          onDelete={async (id) => { await deleteInstance(id); listInstances().then(setInstances); setSelectedId(null); }}
          onRestart={async (id) => {
            const inst = instances.find(i => i.id === id);
            if (inst) { await startInstance(inst.definitionId, inst.variables || {}); listInstances().then(setInstances); }
          }} />
        <div className="flex-1 flex flex-col">
          <InstanceFlow nodes={nodes} edges={edges} error={error || undefined} />
          {/* Action bar */}
          {selectedInst && (
            <div className={`p-2 flex gap-2 border-t text-xs
              ${selectedInst.status === 'SUSPENDED' ? 'bg-yellow-900 border-yellow-700' :
                selectedInst.status === 'RUNNING' ? 'bg-gray-800 border-gray-700' : 'bg-gray-850 border-gray-700'}`}>
              <span className="text-gray-400 self-center">
                {selectedInst.id.substring(0,8)} — {selectedInst.status}
              </span>
              <div className="flex-1" />
              {(selectedInst.status === 'RUNNING' || selectedInst.status === 'SUSPENDED') && (
                <button onClick={handleTerminate}
                  className="bg-red-600 hover:bg-red-500 text-white px-3 py-1 rounded">Terminate</button>
              )}
              {selectedInst.status === 'SUSPENDED' && (
                <button onClick={handleResume}
                  className="bg-yellow-600 hover:bg-yellow-500 text-white px-3 py-1 rounded">Resume</button>
              )}
            </div>
          )}
          <TaskPanel tasks={tasks} onComplete={handleComplete} onReject={handleReject} />
        </div>
      </div>
    </div>
  );
}
