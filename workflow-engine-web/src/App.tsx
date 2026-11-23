import { useEffect, useState } from 'react';
import { GitBranch, Activity, BarChart3, Settings2, Sparkles } from 'lucide-react';
import LanguageSwitcher, { useT } from './i18n';
import DesignerPage from './designer/DesignerPage';
import MonitorPage from './monitor/MonitorPage';
import Dashboard from './monitor/Dashboard';
import AiConfigModal from './designer/AiConfigModal';
import { useToasts, ToastContainer, showToast } from './api/toast';
import { getAiConfig } from './api/client';

export default function App() {
  const [tab, setTab] = useState<'designer' | 'monitor' | 'dashboard'>('designer');
  const [monitorDefId, setMonitorDefId] = useState<string | null>(null);
  const [monitorInstId, setMonitorInstId] = useState<string | null>(null);
  const [showAiConfig, setShowAiConfig] = useState(false);
  const [aiConfigured, setAiConfigured] = useState<boolean | null>(null);
  const { t } = useT();
  const { toasts, removeToast } = useToasts();

  // Status dot on the header gear — reflects whether AI is usable right now
  useEffect(() => {
    getAiConfig().then(v => setAiConfigured(!!v?.configured)).catch(() => setAiConfigured(false));
  }, [showAiConfig]);

  const handleNavigate = (t: 'designer' | 'monitor', defId?: string, instId?: string) => {
    if (t === 'monitor' && defId) {
      setMonitorDefId(defId);
      setMonitorInstId(instId || null);
    }
    setTab(t);
  };

  return (
    <div className="h-screen flex flex-col">
      <header className="bg-gray-850/95 border-b border-gray-700/70 px-4 h-12 flex gap-1 items-center backdrop-blur">
        <div className="flex items-center gap-2 mr-5 select-none">
          <div className="w-7 h-7 rounded-lg bg-blue-600 flex items-center justify-center shadow-sm">
            <Sparkles size={15} className="text-white" strokeWidth={2.2} />
          </div>
          <span className="text-[13px] font-semibold text-gray-100 tracking-tight">FlowForge</span>
        </div>
        <button onClick={() => setTab('designer')}
          className={`flex items-center gap-2 px-3.5 py-2 rounded-md text-xs font-medium transition-colors
            ${tab === 'designer' ? 'bg-blue-600 text-white shadow-sm' : 'text-gray-400 hover:text-gray-100 hover:bg-gray-800'}`}>
          <GitBranch size={14} strokeWidth={2} />
          {t.app.designer}
        </button>
        <button onClick={() => setTab('monitor')}
          className={`flex items-center gap-2 px-3.5 py-2 rounded-md text-xs font-medium transition-colors
            ${tab === 'monitor' ? 'bg-blue-600 text-white shadow-sm' : 'text-gray-400 hover:text-gray-100 hover:bg-gray-800'}`}>
          <Activity size={14} strokeWidth={2} />
          {t.app.monitor}
        </button>
        <button onClick={() => setTab('dashboard')}
          className={`flex items-center gap-2 px-3.5 py-2 rounded-md text-xs font-medium transition-colors
            ${tab === 'dashboard' ? 'bg-blue-600 text-white shadow-sm' : 'text-gray-400 hover:text-gray-100 hover:bg-gray-800'}`}>
          <BarChart3 size={14} strokeWidth={2} />
          {t.app.dashboard}
        </button>
        <div className="flex-1" />
        <button onClick={() => setShowAiConfig(true)}
          title={aiConfigured === false ? t.app.settingsNotConfigured : t.app.settingsConfigured}
          className="flex items-center gap-2 px-3 py-1.5 rounded-md bg-gray-800 hover:bg-gray-700
            border border-gray-700/70 text-gray-300 hover:text-white text-xs font-medium transition-colors">
          <Settings2 size={14} strokeWidth={2} />
          <span className="sr-only">⚙</span>
          <span>{t.app.settings}</span>
          <span className={`w-1.5 h-1.5 rounded-full ring-2 ring-gray-900 ${
            aiConfigured === null ? 'bg-gray-500'
              : aiConfigured ? 'bg-emerald-400' : 'bg-amber-400'}`} />
        </button>
        <LanguageSwitcher />
      </header>
      <main className="flex-1 overflow-hidden">
        {tab === 'designer' && <DesignerPage onNavigate={handleNavigate} />}
        {tab === 'monitor' && <MonitorPage initialDefId={monitorDefId} initialInstId={monitorInstId} />}
        {tab === 'dashboard' && <Dashboard />}
      </main>
      {showAiConfig && (
        <AiConfigModal
          onClose={() => setShowAiConfig(false)}
          onSaved={msg => showToast(msg, 'success')} />
      )}
      <ToastContainer toasts={toasts} removeToast={removeToast} />
    </div>
  );
}
