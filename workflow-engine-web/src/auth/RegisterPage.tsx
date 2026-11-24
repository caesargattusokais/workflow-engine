import { useState, type FormEvent } from 'react';
import { Sparkles, UserPlus, UserRound, LockKeyhole, BadgeCheck, Mail, Eye, EyeOff, Loader2, ArrowLeft } from 'lucide-react';
import { register, type LoginUser } from '../api/client';

/** Self-registration screen, matching the login gate's visual language. */
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
    <div className="h-screen w-screen flex items-center justify-center relative overflow-hidden"
      style={{ background: 'radial-gradient(ellipse at 30% 15%, #1d2540 0%, #141821 55%, #0e1119 100%)' }}>
      <div className="absolute -top-32 -left-24 w-[420px] h-[420px] rounded-full pointer-events-none"
        style={{ background: 'radial-gradient(circle, rgba(109,116,232,0.16), transparent 65%)' }} />
      <div className="absolute -bottom-40 -right-24 w-[460px] h-[460px] rounded-full pointer-events-none"
        style={{ background: 'radial-gradient(circle, rgba(52,211,153,0.10), transparent 65%)' }} />

      <div className="relative w-full max-w-[400px] px-4">
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
          <div style={{ height: 3, background: 'linear-gradient(90deg, #34d399, #6d74e8)' }} />
          <div className="px-7 py-7">
            <h1 className="title-lg mb-1">{zh ? '创建你的账号' : 'Create your account'}</h1>
            <p className="text-xs text-gray-500 mb-5">
              {zh ? '注册后即可拥有独立的工作区' : 'Get your own isolated workspace after signing up'}
            </p>

            <label className="block text-[11px] font-medium text-gray-400 mb-1.5">{zh ? '用户名' : 'Username'}</label>
            <div className="relative mb-3">
              <UserRound size={15} className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-500" />
              <input value={username} onChange={e => setUsername(e.target.value)}
                autoComplete="username" autoFocus placeholder={zh ? '3-32 位字母数字' : '3-32 letters/digits'}
                className="input-dark w-full !pl-9 !py-2 !text-[13px]" />
            </div>

            <label className="block text-[11px] font-medium text-gray-400 mb-1.5">
              {zh ? '显示名称（可选）' : 'Display name (optional)'}
            </label>
            <div className="relative mb-3">
              <BadgeCheck size={15} className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-500" />
              <input value={displayName} onChange={e => setDisplayName(e.target.value)}
                placeholder={zh ? '你的名字' : 'Your name'}
                className="input-dark w-full !pl-9 !py-2 !text-[13px]" />
            </div>

            <label className="block text-[11px] font-medium text-gray-400 mb-1.5">
              {zh ? '邮箱（可选）' : 'Email (optional)'}
            </label>
            <div className="relative mb-3">
              <Mail size={15} className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-500" />
              <input type="email" value={email} onChange={e => setEmail(e.target.value)}
                autoComplete="email" placeholder="you@example.com"
                className="input-dark w-full !pl-9 !py-2 !text-[13px]" />
            </div>

            <label className="block text-[11px] font-medium text-gray-400 mb-1.5">{zh ? '密码' : 'Password'}</label>
            <div className="relative mb-3">
              <LockKeyhole size={15} className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-500" />
              <input type={showPw ? 'text' : 'password'} value={password}
                onChange={e => setPassword(e.target.value)}
                autoComplete="new-password" placeholder={zh ? '至少 6 位' : 'At least 6 characters'}
                className="input-dark w-full !pl-9 !pr-9 !py-2 !text-[13px]" />
              <button type="button" onClick={() => setShowPw(v => !v)}
                className="absolute right-2.5 top-1/2 -translate-y-1/2 p-1 text-gray-500 hover:text-gray-300 rounded"
                title={showPw ? 'Hide' : 'Show'}>
                {showPw ? <EyeOff size={15} /> : <Eye size={15} />}
              </button>
            </div>

            <label className="block text-[11px] font-medium text-gray-400 mb-1.5">
              {zh ? '确认密码' : 'Confirm password'}
            </label>
            <div className="relative mb-5">
              <LockKeyhole size={15} className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-500" />
              <input type={showPw ? 'text' : 'password'} value={confirm}
                onChange={e => setConfirm(e.target.value)}
                autoComplete="new-password" placeholder={zh ? '再次输入密码' : 'Repeat password'}
                className="input-dark w-full !pl-9 !py-2 !text-[13px]" />
            </div>

            {error && (
              <div className="mb-4 px-3 py-2 rounded-md text-xs text-red-300"
                style={{ background: 'rgba(239,68,68,0.1)', border: '1px solid rgba(239,68,68,0.25)' }}>
                {error}
              </div>
            )}

            <button type="submit" disabled={busy} className="btn-primary w-full !py-2.5 !text-[13px] disabled:opacity-60">
              {busy ? <Loader2 size={15} className="animate-spin" /> : <UserPlus size={15} />}
              {busy ? (zh ? '创建中...' : 'Creating...') : (zh ? '创建账号' : 'Create account')}
            </button>

            <button type="button" onClick={onBack}
              className="mt-3 w-full flex items-center justify-center gap-1.5 text-[11px] text-gray-500 hover:text-gray-300 py-1">
              <ArrowLeft size={13} />{zh ? '返回登录' : 'Back to sign in'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
