import { useState } from 'react';
import { useT } from '../i18n';
import { diagnoseInstance, AiDiagnosis } from '../api/client';
import { Sparkles, Hash, Layers3, Braces, History, Loader2, CircleDot, AlertCircle, RotateCcw } from 'lucide-react';

const SEVERITY_STYLE: Record<string, string> = {
  info: 'bg-blue-500/15 text-blue-300 border border-blue-500/30',
  warning: 'bg-amber-500/15 text-amber-300 border border-amber-500/30',
  error: 'bg-red-500/15 text-red-300 border border-red-500/30',
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
    <div className="w-60 bg-gray-800 border-l border-gray-700/70 p-3.5 text-xs overflow-y-auto">
      <div className="flex items-center gap-2 mb-3">
        <Layers3 size={14} className="text-gray-500" strokeWidth={2} />
        <span className="text-[11px] uppercase tracking-wider text-gray-400 font-medium">Instance Detail</span>
      </div>
      <div className="space-y-1.5 mb-3">
        <div className="flex items-center gap-2 text-[11px]">
          <Hash size={11} className="text-gray-600" />
          <span className="text-gray-600">ID:</span>
          <code className="text-gray-300 font-mono">{inst.id.substring(0,8)}</code>
        </div>
        <div className="flex items-center gap-2 text-[11px]">
          <span className="text-gray-600 w-[11px]" />
          <span className="text-gray-600">Def</span>
          <span className="text-gray-300 truncate">{inst.definitionId}</span>
          <span className="text-gray-600 font-mono">v{inst.definitionVersion||0}</span>
        </div>
      </div>
      <div className="mb-3">
        <span className={`inline-flex items-center gap-1.5 px-2 py-1 rounded-md text-[11px] font-medium border
          ${inst.status==='RUNNING' ? 'bg-emerald-500/10 text-emerald-400 border-emerald-500/25' :
            inst.status==='COMPLETED' ? 'bg-blue-500/10 text-blue-400 border-blue-500/25' :
            inst.status==='SUSPENDED' ? 'bg-amber-500/10 text-amber-400 border-amber-500/25' :
            'bg-red-500/10 text-red-400 border-red-500/25'}`}>
          <CircleDot size={10} strokeWidth={2.5} />
          {statusLabel(inst.status)}
        </span>
      </div>

      {/* AI copilot: explain the current state and suggest recovery steps */}
      <div className="bg-gradient-to-b from-purple-500/[0.07] to-transparent
                      border border-purple-500/25 rounded-lg p-3 mb-4">
        <div className="flex items-center justify-between mb-2">
          <span className="flex items-center gap-1.5 text-purple-300 font-medium text-[11px]">
            <Sparkles size={12} strokeWidth={2.2} />
            {t.monitor.aiCopilot}
          </span>
          <button
            onClick={analyze}
            disabled={analyzing}
            className="flex items-center gap-1 px-2 py-1 rounded-md bg-purple-600/20 hover:bg-purple-600/35
                       border border-purple-500/30 text-purple-200 text-[10px] font-medium
                       disabled:opacity-50 transition-colors"
          >
            {analyzing ? <Loader2 size={11} className="animate-spin" /> : <RotateCcw size={10} />}
            {analyzing ? t.monitor.aiAnalyzing : (diagnosis || error ? t.monitor.retry : t.monitor.aiCopilot)}
          </button>
        </div>
        {analyzing && (
          <div className="flex items-center gap-1.5 text-purple-400/80 text-[11px] py-1">
            <Loader2 size={11} className="animate-spin" />
            {t.monitor.aiAnalyzing}
          </div>
        )}
        {error && !analyzing && (
          <div className="text-red-300/90 text-[11px] break-words leading-relaxed">
            <div className="flex items-center gap-1.5 font-medium mb-0.5"><AlertCircle size={12} /> {t.monitor.aiFailed}</div>
            <div className="text-[10px] opacity-75">{error}</div>
          </div>
        )}
        {diagnosis && !analyzing && (
          <div className="space-y-2.5">
            <span className={`inline-block px-2 py-0.5 rounded text-[10px] font-medium uppercase tracking-wide ${SEVERITY_STYLE[diagnosis.severity] || SEVERITY_STYLE.info}`}>
              {diagnosis.severity}
            </span>
            <div>
              <div className="text-[10px] text-gray-500 uppercase tracking-wide mb-0.5">{t.monitor.aiSummary}</div>
              <div className="text-gray-200 whitespace-pre-wrap break-words leading-relaxed">{diagnosis.summary}</div>
            </div>
            {diagnosis.cause && (
              <div>
                <div className="text-[10px] text-gray-500 uppercase tracking-wide mb-0.5">{t.monitor.aiCause}</div>
                <div className="text-gray-400 whitespace-pre-wrap break-words leading-relaxed">{diagnosis.cause}</div>
              </div>
            )}
            {diagnosis.suggestions?.length > 0 && (
              <div>
                <div className="text-[10px] text-gray-500 uppercase tracking-wide mb-1">{t.monitor.aiSuggestions}</div>
                <ul className="space-y-1">
                  {diagnosis.suggestions.map((s, i) => (
                    <li key={i} className="flex gap-1.5 text-gray-400 leading-relaxed">
                      <span className="text-purple-400 mt-0.5">•</span>{s}
                    </li>
                  ))}
                </ul>
              </div>
            )}
          </div>
        )}
      </div>

      <div className="flex items-center gap-2 mb-1.5">
        <CircleDot size={11} className="text-gray-600" />
        <span className="text-[11px] uppercase tracking-wider text-gray-500 font-medium">Active Nodes</span>
      </div>
      {inst.activeNodeIds?.length > 0 ? inst.activeNodeIds.map((nid: string) => (
        <div key={nid} className="text-blue-400 mb-0.5 ml-5 flex items-center gap-1.5 text-[11px]">
          <span className="w-1 h-1 rounded-full bg-blue-400" />{nodeNames[nid] || nid}
        </div>
      )) : <div className="text-gray-600 ml-5 text-[11px]">none</div>}

      <div className="flex items-center gap-2 mb-1.5 mt-4">
        <Braces size={11} className="text-gray-600" />
        <span className="text-[11px] uppercase tracking-wider text-gray-500 font-medium">{t.designer.variables}</span>
      </div>
      {Object.keys(inst.variables || {}).length > 0
        ? Object.entries(inst.variables||{}).map(([k,v]) => (
          <div key={k} className="text-[11px] text-gray-500 mb-0.5 ml-5 flex gap-1.5">
            <code className="text-blue-400/90 font-mono truncate max-w-[80px]">{k}</code>
            <span className="text-gray-400 truncate flex-1">{String(v)}</span>
          </div>
        ))
        : <div className="text-gray-600 ml-5 text-[11px]">none</div>}

      <div className="flex items-center gap-2 mb-2 mt-4">
        <History size={11} className="text-gray-600" />
        <span className="text-[11px] uppercase tracking-wider text-gray-500 font-medium">{t.monitor.history} ({history.length})</span>
      </div>
      <div className="ml-5 space-y-2">
        {history.slice(-10).reverse().map((h:any,i:number) => (
          <div key={i} className="pl-2.5 border-l border-gray-700">
            <div className="text-gray-300 text-[11px]">{nodeNames[h.nodeId] || h.nodeId}</div>
            <div className="text-[10px] text-gray-600 mt-0.5">{h.action} · {h.executor}</div>
          </div>
        ))}
      </div>
    </div>
  );
}
