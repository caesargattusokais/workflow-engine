import { useT } from '../i18n';

/** Right-hand detail sidebar for the selected instance. */
export default function InstanceDetailSidebar({ inst, nodeNames, history }: {
  inst: any;
  nodeNames: Record<string, string>;
  history: any[];
}) {
  const { t } = useT();
  const statusLabel = (s: string) => {
    const m: Record<string, string> = {
      RUNNING: t.monitor.running,
      COMPLETED: t.monitor.completed,
      TERMINATED: t.monitor.terminated,
      SUSPENDED: t.monitor.suspended,
    };
    return m[s] || s;
  };

  return (
    <div className="w-56 bg-gray-800 border-l border-gray-700 p-3 text-xs overflow-y-auto">
      <div className="text-gray-400 font-semibold mb-2">Instance Detail</div>
      <div className="text-gray-500 mb-1">ID: <span className="text-gray-300">{inst.id.substring(0,8)}</span></div>
      <div className="text-gray-500 mb-1">Def: <span className="text-gray-300">{inst.definitionId}</span> <span className="text-gray-600">v{inst.definitionVersion||0}</span></div>
      <div className="mb-2">
        <span className={`px-1.5 py-0.5 rounded text-[10px] font-semibold
          ${inst.status==='RUNNING'?'bg-green-900 text-green-400':
            inst.status==='COMPLETED'?'bg-blue-900 text-blue-400':
            inst.status==='SUSPENDED'?'bg-yellow-900 text-yellow-400':'bg-red-900 text-red-400'}`}>
          {statusLabel(inst.status)}
        </span>
      </div>
      <div className="text-gray-400 font-semibold mt-3 mb-1">Active Nodes</div>
      {inst.activeNodeIds?.length > 0 ? inst.activeNodeIds.map((nid: string) => (
        <div key={nid} className="text-blue-400 mb-0.5">● {nodeNames[nid] || nid}</div>
      )) : <div className="text-gray-600">none</div>}
      <div className="text-gray-400 font-semibold mt-3 mb-1">{t.designer.variables}</div>
      {Object.keys(inst.variables || {}).length > 0
        ? Object.entries(inst.variables||{}).map(([k,v]) => (
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
  );
}
