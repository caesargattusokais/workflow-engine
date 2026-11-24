import { useState, type FormEvent } from 'react';
import { LogIn, UserRound, LockKeyhole, Eye, EyeOff, Loader2, AlertCircle, Terminal } from 'lucide-react';
import AuthShell, { AuthField } from './AuthShell';
import { login, type LoginUser } from '../api/client';

/** Full-screen login gate. Uses the shared split "Grid Auth Form" shell. */
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

  const fillDemo = (u: string, p: string) => {
    setUsername(u);
    setPassword(p);
    setError('');
  };

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
    <AuthShell
      lang={lang}
      mode="login"
      title={zh ? '登录工作区' : 'Sign in to your workspace'}
      subtitle={zh
        ? '使用团队账号继续，会话将在本机安全保存'
        : 'Use your team account — your session is kept securely on this device'}
      footer={
        <>
          <div className="auth-demo">
            <div className="auth-demo-head">
              <Terminal size={12} strokeWidth={1.9} />
              {zh ? '演示账号 · 点击填入' : 'Demo accounts · click to fill'}
            </div>
            <div className="auth-demo-grid">
              <button type="button" className="auth-demo-chip" onClick={() => fillDemo('admin', 'admin123')}>
                admin / admin123
              </button>
              <button type="button" className="auth-demo-chip" onClick={() => fillDemo('testuser', 'test123')}>
                testuser / test123
              </button>
            </div>
          </div>
          {registrationEnabled && onRegister && (
            <p className="auth-switch" style={{ marginTop: 18 }}>
              {zh ? '还没有账号？' : 'No account yet?'}{' '}
              <button type="button" onClick={onRegister} className="auth-link">
                {zh ? '立即注册' : 'Sign up'}
              </button>
            </p>
          )}
        </>
      }>
      <form onSubmit={submit}>
        <AuthField icon={UserRound} label={zh ? '用户名' : 'Username'} htmlFor="login-username">
          <input
            id="login-username"
            value={username}
            onChange={e => setUsername(e.target.value)}
            autoComplete="username"
            autoFocus
            placeholder={zh ? '请输入用户名' : 'Enter username'}
            className="auth-input"
          />
        </AuthField>

        <AuthField icon={LockKeyhole} label={zh ? '密码' : 'Password'} htmlFor="login-password">
          <input
            id="login-password"
            type={showPw ? 'text' : 'password'}
            value={password}
            onChange={e => setPassword(e.target.value)}
            autoComplete="current-password"
            placeholder={zh ? '请输入密码' : 'Enter password'}
            className="auth-input auth-input-has-toggle"
          />
          <button
            type="button"
            onClick={() => setShowPw(v => !v)}
            className="auth-toggle"
            title={showPw ? 'Hide' : 'Show'}
            aria-label={showPw ? 'Hide password' : 'Show password'}>
            {showPw ? <EyeOff size={15} /> : <Eye size={15} />}
          </button>
        </AuthField>

        {error && (
          <div className="auth-error" role="alert">
            <AlertCircle size={14} strokeWidth={1.9} />
            <span>{error}</span>
          </div>
        )}

        <button type="submit" disabled={busy} className="auth-submit">
          {busy ? <Loader2 size={15} className="animate-spin" /> : <LogIn size={15} strokeWidth={2} />}
          {busy ? (zh ? '登录中...' : 'Signing in...') : (zh ? '登录' : 'Sign in')}
        </button>
      </form>
    </AuthShell>
  );
}
