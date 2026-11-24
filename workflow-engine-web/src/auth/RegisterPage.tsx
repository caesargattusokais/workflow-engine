import { useState, type FormEvent } from 'react';
import { UserPlus, UserRound, LockKeyhole, BadgeCheck, Mail, Eye, EyeOff, Loader2, AlertCircle, ArrowLeft } from 'lucide-react';
import AuthShell, { AuthField } from './AuthShell';
import { register, type LoginUser } from '../api/client';

/** Self-registration screen, sharing the login gate's visual shell. */
export default function RegisterPage({ onSuccess, onBack, lang }: {
  onSuccess: (user: LoginUser) => void;
  onBack: () => void;
  lang: 'zh' | 'en';
}) {
  const zh = lang === 'zh';
  const [username, setUsername] = useState('');
  const [displayName, setDisplayName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [confirm, setConfirm] = useState('');
  const [showPw, setShowPw] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    if (!username.trim() || !password) {
      setError(zh ? '请输入用户名和密码' : 'Enter username and password');
      return;
    }
    if (password !== confirm) {
      setError(zh ? '两次输入的密码不一致' : 'Passwords do not match');
      return;
    }
    if (password.length < 6) {
      setError(zh ? '密码至少 6 位' : 'Password must be at least 6 characters');
      return;
    }
    setBusy(true);
    setError('');
    try {
      const result = await register(username.trim(), password, displayName.trim() || undefined, email.trim() || undefined);
      onSuccess(result.user);
    } catch (err: any) {
      setError(err?.message || (zh ? '注册失败' : 'Registration failed'));
    } finally {
      setBusy(false);
    }
  };

  return (
    <AuthShell
      lang={lang}
      mode="register"
      title={zh ? '创建你的账号' : 'Create your account'}
      subtitle={zh
        ? '注册后即可拥有独立的工作区'
        : 'Get your own isolated workspace after signing up'}
      footer={
        <button type="button" onClick={onBack} className="auth-back">
          <ArrowLeft size={13} strokeWidth={1.9} />
          {zh ? '返回登录' : 'Back to sign in'}
        </button>
      }>
      <form onSubmit={submit}>
        <AuthField icon={UserRound} label={zh ? '用户名' : 'Username'} htmlFor="reg-username">
          <input
            id="reg-username"
            value={username}
            onChange={e => setUsername(e.target.value)}
            autoComplete="username"
            autoFocus
            placeholder={zh ? '3-32 位字母数字' : '3-32 letters/digits'}
            className="auth-input"
          />
        </AuthField>

        <AuthField icon={BadgeCheck} label={zh ? '显示名称（可选）' : 'Display name (optional)'} htmlFor="reg-display">
          <input
            id="reg-display"
            value={displayName}
            onChange={e => setDisplayName(e.target.value)}
            placeholder={zh ? '你的名字' : 'Your name'}
            className="auth-input"
          />
        </AuthField>

        <AuthField icon={Mail} label={zh ? '邮箱（可选）' : 'Email (optional)'} htmlFor="reg-email">
          <input
            id="reg-email"
            type="email"
            value={email}
            onChange={e => setEmail(e.target.value)}
            autoComplete="email"
            placeholder="you@example.com"
            className="auth-input"
          />
        </AuthField>

        <AuthField
          icon={LockKeyhole}
          label={zh ? '密码' : 'Password'}
          htmlFor="reg-password"
          hint={zh ? '至少 6 位' : '6+ characters'}>
          <input
            id="reg-password"
            type={showPw ? 'text' : 'password'}
            value={password}
            onChange={e => setPassword(e.target.value)}
            autoComplete="new-password"
            placeholder={zh ? '至少 6 位' : 'At least 6 characters'}
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

        <AuthField icon={LockKeyhole} label={zh ? '确认密码' : 'Confirm password'} htmlFor="reg-confirm">
          <input
            id="reg-confirm"
            type={showPw ? 'text' : 'password'}
            value={confirm}
            onChange={e => setConfirm(e.target.value)}
            autoComplete="new-password"
            placeholder={zh ? '再次输入密码' : 'Repeat password'}
            className="auth-input"
          />
        </AuthField>

        {error && (
          <div className="auth-error" role="alert">
            <AlertCircle size={14} strokeWidth={1.9} />
            <span>{error}</span>
          </div>
        )}

        <button type="submit" disabled={busy} className="auth-submit">
          {busy ? <Loader2 size={15} className="animate-spin" /> : <UserPlus size={15} strokeWidth={2} />}
          {busy ? (zh ? '创建中...' : 'Creating...') : (zh ? '创建账号' : 'Create account')}
        </button>
      </form>
    </AuthShell>
  );
}
