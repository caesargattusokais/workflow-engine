import { useState, type FormEvent } from 'react';
import { Sparkles, LogIn, UserRound, LockKeyhole, Eye, EyeOff, Loader2 } from 'lucide-react';
import { login, type LoginUser } from '../api/client';

/** Full-screen login gate. Uses the same dark, elevated visual language as the app. */
export default function LoginPage({ onSuccess, onRegister, registrationEnabled = true, lang }: {
  onSuccess: (user: LoginUser) => void;
  onRegister?: () => void;
  registrationEnabled?: boolean;
  lang: 'zh' | 'en';
}) {
  const zh = lang === 'zh';
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [showPw, setShowPw] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    if (!username.trim() || !password) {
      setError(zh ? '请输入用户名和密码' : 'Enter username and password');
      return;
    }
    setBusy(true);
    setError('');
    try {
      const result = await login(username.trim(), password);
      onSuccess(result.user);
    } catch (err: any) {
      setError(err?.message || (zh ? '登录失败' : 'Login failed'));
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="h-screen w-screen flex items-center justify-center relative overflow-hidden"
      style={{ background: 'radial-gradient(ellipse at 30% 15%, #1d2540 0%, #141821 55%, #0e1119 100%)' }}>
      {/* ambient glows */}
      <div className="absolute -top-32 -left-24 w-[420px] h-[420px] rounded-full pointer-events-none"
        style={{ background: 'radial-gradient(circle, rgba(109,116,232,0.16), transparent 65%)' }} />
      <div className="absolute -bottom-40 -right-24 w-[460px] h-[460px] rounded-full pointer-events-none"
        style={{ background: 'radial-gradient(circle, rgba(52,211,153,0.10), transparent 65%)' }} />

      <div className="relative w-full max-w-[400px] px-4">
        {/* brand */}
        <div className="flex items-center justify-center gap-2.5 mb-7 select-none">
          <div className="w-10 h-10 rounded-xl bg-blue-600 flex items-center justify-center shadow-glow">
            <Sparkles size={20} className="text-white" strokeWidth={2.2} />
          </div>
          <span className="text-[22px] font-semibold text-gray-50 tracking-tight">FlowForge</span>
        </div>

        <form onSubmit={submit}
          className="rounded-2xl overflow-hidden"
          style={{
            background: 'linear-gradient(160deg, rgba(255,255,255,0.05), rgba(255,255,255,0.01) 45%), #1b202b',
            border: '1px solid rgba(255,255,255,0.09)',
            boxShadow: 'inset 0 1px 0 rgba(255,255,255,0.07), 0 24px 70px rgba(0,0,0,0.55)',
          }}>
          <div style={{ height: 3, background: 'linear-gradient(90deg, #6d74e8, #34d399)' }} />
          <div className="px-7 py-7">
            <h1 className="title-lg mb-1">{zh ? '登录工作区' : 'Sign in to your workspace'}</h1>
            <p className="text-xs text-gray-500 mb-6">
              {zh ? '使用团队账号继续，会话将在本机安全保存' : 'Use your team account — your session is kept securely on this device'}
            </p>

            <label className="block text-[11px] font-medium text-gray-400 mb-1.5">
              {zh ? '用户名' : 'Username'}
            </label>
            <div className="relative mb-4">
              <UserRound size={15} className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-500" />
              <input
                value={username}
                onChange={e => setUsername(e.target.value)}
                autoComplete="username"
                autoFocus
                placeholder={zh ? '请输入用户名' : 'Enter username'}
                className="input-dark w-full !pl-9 !py-2.5 !text-[13px]"
              />
            </div>

            <label className="block text-[11px] font-medium text-gray-400 mb-1.5">
              {zh ? '密码' : 'Password'}
            </label>
            <div className="relative mb-5">
              <LockKeyhole size={15} className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-500" />
              <input
                type={showPw ? 'text' : 'password'}
                value={password}
                onChange={e => setPassword(e.target.value)}
                autoComplete="current-password"
                placeholder={zh ? '请输入密码' : 'Enter password'}
                className="input-dark w-full !pl-9 !pr-9 !py-2.5 !text-[13px]"
              />
              <button type="button" onClick={() => setShowPw(v => !v)}
                className="absolute right-2.5 top-1/2 -translate-y-1/2 p-1 text-gray-500 hover:text-gray-300 rounded"
                title={showPw ? 'Hide' : 'Show'}>
                {showPw ? <EyeOff size={15} /> : <Eye size={15} />}
              </button>
            </div>

            {error && (
              <div className="mb-4 px-3 py-2 rounded-md text-xs text-red-300"
                style={{ background: 'rgba(239,68,68,0.1)', border: '1px solid rgba(239,68,68,0.25)' }}>
                {error}
              </div>
            )}

            <button type="submit" disabled={busy} className="btn-primary w-full !py-2.5 !text-[13px] disabled:opacity-60">
              {busy ? <Loader2 size={15} className="animate-spin" /> : <LogIn size={15} />}
              {busy ? (zh ? '登录中...' : 'Signing in...') : (zh ? '登录' : 'Sign in')}
            </button>
          </div>
        </form>

        <p className="text-center text-[11px] text-gray-600 mt-5">
          {zh ? '演示账号：admin / admin123　·　testuser / test123' : 'Demo accounts: admin / admin123 · testuser / test123'}
        </p>
        {registrationEnabled && onRegister && (
          <p className="text-center text-[11px] text-gray-500 mt-3">
            {zh ? '还没有账号？' : 'No account yet?'}{' '}
            <button type="button" onClick={onRegister}
              className="text-indigo-400 hover:text-indigo-300 font-medium">
              {zh ? '立即注册' : 'Sign up'}
            </button>
          </p>
        )}
      </div>
    </div>
  );
}
