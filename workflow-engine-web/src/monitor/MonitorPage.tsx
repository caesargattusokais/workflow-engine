import { useState, useEffect, useCallback } from 'react';
import type { Node, Edge } from '@xyflow/react';
import InstanceList from './InstanceList';
import InstanceFlow from './InstanceFlow';
import TaskPanel from './TaskPanel';
import { listInstances, queryTasks, completeTask, getDefinitionGraph, resumeInstance, terminateInstance } from '../api/client';

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

  const selectedInst = instances.find(i => i.id === selectedId);

  return (
    <div className="flex flex-col h-full">
      <div className="bg-gray-800 border-b border-gray-700 px-4 py-1">
        <span className="text-sm text-gray-400">Monitor</span>
      </div>
      <div className="flex flex-1 overflow-hidden">
        <InstanceList instances={instances} selectedId={selectedId} onSelect={loadInstance} />
        <div className="flex-1 flex flex-col">
          <InstanceFlow nodes={nodes} edges={edges} error={error || undefined} />
          {selectedInst && selectedInst.status === 'SUSPENDED' && (
            <div className="bg-yellow-900 border-t border-yellow-700 p-2 flex gap-2">
              <button onClick={handleResume}
                className="bg-yellow-600 hover:bg-yellow-500 text-white text-xs px-3 py-1 rounded">Resume</button>
              <button onClick={handleTerminate}
                className="bg-red-600 hover:bg-red-500 text-white text-xs px-3 py-1 rounded">Terminate</button>
            </div>
          )}
          <TaskPanel tasks={tasks} onComplete={handleComplete} onReject={handleReject} />
        </div>
      </div>
    </div>
  );
}
