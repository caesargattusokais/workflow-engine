interface TaskInfo { id: string; nodeId: string; assignee: string; status: string; }

export default function TaskPanel({ tasks, onComplete, onReject }:
    { tasks: TaskInfo[], onComplete: (id: string) => void, onReject: (id: string) => void }) {

  if (tasks.length === 0) return (
    <div className="bg-gray-800 border-t border-gray-700 p-3 text-xs text-gray-500">
      No pending tasks for this instance
    </div>
  );

  return (
    <div className="bg-gray-800 border-t border-gray-700 p-3">
      <div className="text-xs text-gray-400 mb-2">Pending Tasks</div>
      {tasks.map(t => (
        <div key={t.id} className="flex items-center justify-between py-1 text-sm">
          <span className="text-gray-300">{t.nodeId}</span>
          <span className="text-gray-500">&rarr; {t.assignee}</span>
          <div className="flex gap-2">
            <button onClick={() => onComplete(t.id)}
              className="bg-green-600 hover:bg-green-500 text-white text-xs px-2 py-0.5 rounded">
              Complete
            </button>
            <button onClick={() => onReject(t.id)}
              className="bg-red-600 hover:bg-red-500 text-white text-xs px-2 py-0.5 rounded">
              Reject
            </button>
          </div>
        </div>
      ))}
    </div>
  );
}
