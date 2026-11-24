import { useEffect, useState } from 'react';
import { GitBranch, Activity, BarChart3, Settings2, Sparkles, LogOut } from 'lucide-react';
import LanguageSwitcher, { useT } from './i18n';
import DesignerPage from './designer/DesignerPage';
import MonitorPage from './monitor/MonitorPage';
import Dashboard from './monitor/Dashboard';
import AiConfigModal from './designer/AiConfigModal';
import LoginPage from './auth/LoginPage';
import RegisterPage from './auth/RegisterPage';
import { useToasts, ToastContainer, showToast } from './api/toast';
import { getAiConfig, getAuthMe, getAuthStatus, logout, onUnauthorized, type LoginUser } from './api/client';

export default function App() {
  const [tab, setTab] = useState<'designer' | 'monitor' | 'dashboard'>('designer');
  const [monitorDefId, setMonitorDefId] = useState<string | null>(null);
  const [monitorInstId, setMonitorInstId] = useState<string | null>(null);
  const [showAiConfig, setShowAiConfig] = useState(false);
  const [aiConfigured, setAiConfigured] = useState<boolean | null>(null);
  // ── Auth gate ──────────────────────────────────────────────────────────
  // undefined = checking session; null = show login; AuthUser = signed in.
  const [currentUser, setCurrentUser] = useState<LoginUser | null | undefined>(undefined);
  const [authView, setAuthView] = useState<'login' | 'register'>('login');
  const [registrationEnabled, setRegistrationEnabled] = useState(false);
  const { t, lang } = useT();
  const { toasts, removeToast } = useToasts();

  // A preset X-User-Id (E2E fixtures, MCP-style service callers) is a valid
  // legacy identity — skip the login screen for those flows.
  const hasLegacyIdentity = () => !!localStorage.getItem('userId');

  useEffect(() => {
    getAuthStatus().then(s => setRegistrationEnabled(!!s.registrationEnabled)).catch(() => {});
  }, []);

  useEffect(() => {
    getAuthMe().then(me => {
      if (me.authenticated && me.user) setCurrentUser(me.user);
      else if (hasLegacyIdentity()) setCurrentUser({ username: localStorage.getItem('userId')!, displayName: localStorage.getItem('userId')!, roles: [] });
      else setCurrentUser(null);
    }).catch(() => {
      setCurrentUser(hasLegacyIdentity()
        ? { username: localStorage.getItem('userId')!, displayName: localStorage.getItem('userId')!, roles: [] }
        : null);
    });
  }, []);

  // Any 401 from the API drops us back to the login screen.
  useEffect(() => {
    onUnauthorized(() => setCurrentUser(null));
    return () => onUnauthorized(null);
  }, []);

  const handleLogout = async () => {
    await logout();
    setCurrentUser(null);
  };

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

  // Session still being resolved — avoid flashing the login form.
  if (currentUser === undefined) {
    return <div className="h-screen w-screen" style={{ background: '#141821' }} />;
  }
  if (currentUser === null) {
    if (authView === 'register' && registrationEnabled) {
      return <RegisterPage lang={lang}
        onBack={() => setAuthView('login')}
        onSuccess={u => { setAuthView('login'); setCurrentUser(u); }} />;
    }
    return <LoginPage lang={lang}
      registrationEnabled={registrationEnabled}
      onRegister={() => setAuthView('register')}
      onSuccess={u => setCurrentUser(u)} />;
  }

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
        {/* Current user + logout */}
        <div className="flex items-center gap-1.5 mr-1 px-2 py-1 rounded-md bg-gray-800/70 border border-gray-700/60">
          <span className="w-5 h-5 rounded-md bg-gradient-to-br from-accent to-blue-500 flex items-center justify-center text-[10px] font-semibold text-white">
            {currentUser.displayName.slice(0, 1).toUpperCase()}
          </span>
          <span className="text-[11px] text-slate-200 font-medium max-w-[100px] overflow-hidden text-ellipsis whitespace-nowrap" title={currentUser.username}>
            {currentUser.displayName}
          </span>
          <button onClick={handleLogout} className="btn-icon !w-5 !h-5" title={lang === 'zh' ? '退出登录' : 'Sign out'}>
            <LogOut size={13} />
          </button>
        </div>
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
