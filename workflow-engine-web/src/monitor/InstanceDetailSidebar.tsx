import { useState } from 'react';
import { useT } from '../i18n';
import { diagnoseInstance, AiDiagnosis } from '../api/client';

const SEVERITY_STYLE: Record<string, string> = {
  info: 'bg-blue-900 text-blue-300',
  warning: 'bg-yellow-900 text-yellow-300',
  error: 'bg-red-900 text-red-300',
};

/** Right-hand detail sidebar for the selected instance, with an AI copilot section. */
export default function InstanceDetailSidebar({ inst, nodeNames, history }: {
  inst: any;
  nodeNames: Record<string, string>;
  history: any[];
}) {
  const { t } = useT();
  const [diagnosis, setDiagnosis] = useState<AiDiagnosis | null>(null);
  const [analyzing, setAnalyzing] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const analyze = async () => {
    setAnalyzing(true);
    setError(null);
    try {
      setDiagnosis(await diagnoseInstance(inst.id));
    } catch (e: any) {
      setDiagnosis(null);
      setError(e?.message || String(e));
    } finally {
      setAnalyzing(false);
    }
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

      {/* AI copilot: explain the current state and suggest recovery steps */}
      <div className="border-t border-gray-700 mt-3 pt-2">
        <div className="flex items-center justify-between mb-1">
          <span className="text-gray-400 font-semibold">✨ {t.monitor.aiCopilot}</span>
          <button
            onClick={analyze}
            disabled={analyzing}
            className="px-2 py-0.5 rounded bg-purple-900 hover:bg-purple-800 disabled:opacity-50 text-purple-200 text-[10px]"
          >
            {analyzing ? t.monitor.aiAnalyzing : (diagnosis || error ? t.monitor.retry : t.monitor.aiCopilot)}
          </button>
        </div>
        {analyzing && <div className="text-purple-400 animate-pulse">{t.monitor.aiAnalyzing}</div>}
        {error && !analyzing && (
          <div className="text-red-400 break-words">
            <div className="font-semibold">{t.monitor.aiFailed}</div>
            <div className="text-[10px]">{error}</div>
          </div>
        )}
        {diagnosis && !analyzing && (
          <div>
            <span className={`inline-block px-1.5 py-0.5 rounded text-[10px] font-semibold mb-1 ${SEVERITY_STYLE[diagnosis.severity] || SEVERITY_STYLE.info}`}>
              {diagnosis.severity}
            </span>
            <div className="mb-1">
              <div className="text-gray-500">{t.monitor.aiSummary}</div>
              <div className="text-gray-200 whitespace-pre-wrap break-words">{diagnosis.summary}</div>
            </div>
            {diagnosis.cause && (
              <div className="mb-1">
                <div className="text-gray-500">{t.monitor.aiCause}</div>
                <div className="text-gray-300 whitespace-pre-wrap break-words">{diagnosis.cause}</div>
              </div>
            )}
            {diagnosis.suggestions?.length > 0 && (
              <div>
                <div className="text-gray-500">{t.monitor.aiSuggestions}</div>
                <ul className="list-disc list-inside text-gray-300">
                  {diagnosis.suggestions.map((s, i) => (
                    <li key={i} className="break-words">{s}</li>
                  ))}
                </ul>
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  );
}
