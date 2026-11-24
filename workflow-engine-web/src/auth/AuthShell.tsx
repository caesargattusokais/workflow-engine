import type { ReactNode } from 'react';
import { Sparkles, Workflow, Activity, ShieldCheck, type LucideIcon } from 'lucide-react';

/** Shared layout for the login / registration gates.
 *  Inspired by the "Grid Auth Form" pattern: a branded grid showcase beside a
 *  glass login card, with restrained motion. Keeps the app's dark Linear look. */
export default function AuthShell({ lang, mode, title, subtitle, children, footer }: {
  lang: 'zh' | 'en';
  mode: 'login' | 'register';
  title: string;
  subtitle: string;
  children: ReactNode;
  footer?: ReactNode;
}) {
  const zh = lang === 'zh';
  const features: { icon: LucideIcon; title: string; desc: string }[] = [
    {
      icon: Workflow,
      title: zh ? '可视化编排' : 'Visual designer',
      desc: zh ? '拖拽节点，秒级生成流程定义' : 'Drag nodes, generate a flow definition in seconds',
    },
    {
      icon: Sparkles,
      title: zh ? 'AI 副驾' : 'AI copilot',
      desc: zh ? '卡住时一键诊断，给出可操作建议' : 'One-click diagnosis with actionable hints',
    },
    {
      icon: Activity,
      title: zh ? '实时监控' : 'Live monitoring',
      desc: zh ? '每一个 token 的轨迹都清晰可见' : 'Every token trace, clearly visible',
    },
  ];

  return (
    <div className="auth-page">
      {/* ── Left showcase: grid, glows, product pitch ─────────────────── */}
      <aside className="auth-showcase">
        <div className="auth-grid" />
        <div className="auth-grid auth-grid-fine" />
        <div className="auth-orb auth-orb-a" />
        <div className="auth-orb auth-orb-b" />
        <div className="auth-showcase-inner">
          <Brand />

          <div className="auth-showcase-copy">
            <div className="auth-eyebrow">
              <span className="auth-eyebrow-dot" />
              {mode === 'login'
                ? (zh ? '安全接入工作区' : 'SECURE WORKSPACE ACCESS')
                : (zh ? '开启你的工作区' : 'CREATE YOUR WORKSPACE')}
            </div>
            <h2 className="auth-headline">
              {zh ? '设计一次，' : 'Design once. '}
              <span className="auth-headline-accent">{zh ? '自动流转。' : 'Let it flow.'}</span>
            </h2>
            <p className="auth-lede">
              {zh
                ? '可视化编排、AI 辅助校验、实时监控——一个引擎，覆盖流程全生命周期。'
                : 'Visual design, AI-assisted validation, live monitoring — one engine for the entire flow lifecycle.'}
            </p>

            <div className="auth-features">
              {features.map(({ icon: Icon, title: fTitle, desc }) => (
                <div className="auth-feature" key={fTitle}>
                  <span className="auth-feature-icon"><Icon size={16} strokeWidth={1.9} /></span>
                  <div className="min-w-0">
                    <div className="auth-feature-title">{fTitle}</div>
                    <div className="auth-feature-desc">{desc}</div>
                  </div>
                </div>
              ))}
            </div>
          </div>

          <div className="auth-showcase-foot">
            <ShieldCheck size={13} strokeWidth={1.9} />
            <span>{zh ? '会话采用 HttpOnly Cookie 保护 · 数据按工作区隔离' : 'Sessions protected by HttpOnly cookies · workspace-isolated data'}</span>
          </div>
        </div>
      </aside>

      {/* ── Right panel: the form itself ──────────────────────────────── */}
      <section className="auth-panel">
        <div className="auth-grid auth-grid-panel" />
        <div className="auth-card">
          <div className="auth-card-line" />
          <div className="auth-card-body">
            <div className="auth-mobile-brand"><Brand compact /></div>
            <h1 className="auth-title">{title}</h1>
            <p className="auth-subtitle">{subtitle}</p>
            {children}
            {footer && <div className="auth-footer">{footer}</div>}
          </div>
        </div>
      </section>
    </div>
  );
}

function Brand({ compact = false }: { compact?: boolean }) {
  return (
    <div className={`auth-brand${compact ? ' auth-brand-compact' : ''}`}>
      <span className="auth-brand-mark">
        <Sparkles size={compact ? 16 : 20} strokeWidth={2.2} />
      </span>
      <span className="auth-brand-name">FlowForge</span>
    </div>
  );
}

/** Label + iconed input wrapper. The input itself is passed in so callers keep
 *  full control of type / autocomplete / toggle button. */
export function AuthField({ icon: Icon, label, htmlFor, hint, children }: {
  icon: LucideIcon;
  label: string;
  htmlFor?: string;
  hint?: string;
  children: ReactNode;
}) {
  return (
    <div className="auth-field-group">
      <div className="auth-label-row">
        <label className="auth-label" htmlFor={htmlFor}>{label}</label>
        {hint && <span className="auth-hint">{hint}</span>}
      </div>
      <div className="auth-field">
        <Icon size={15} strokeWidth={1.8} className="auth-field-icon" />
        {children}
      </div>
    </div>
  );
}
