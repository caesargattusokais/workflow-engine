import type { ReactNode } from 'react';

type Art = 'flow' | 'instance' | 'task' | 'chart' | 'inbox';

/** Hand-drawn inline SVG illustrations — no emoji, consistent indigo/teal palette. */
function Illustration({ art }: { art: Art }) {
  if (art === 'flow') {
    return (
      <svg viewBox="0 0 160 110" className="w-full h-full" fill="none">
        <rect x="10" y="42" width="34" height="26" rx="6" fill="#6d74e8" fillOpacity="0.18" stroke="#6d74e8" strokeOpacity="0.55" strokeWidth="1.5" />
        <circle cx="27" cy="55" r="4" fill="#6d74e8" />
        <rect x="63" y="42" width="34" height="26" rx="6" fill="#4ade80" fillOpacity="0.12" stroke="#4ade80" strokeOpacity="0.45" strokeWidth="1.5" />
        <path d="M74 55h12m-6-6v12" stroke="#4ade80" strokeOpacity="0.8" strokeWidth="1.6" strokeLinecap="round" />
        <rect x="116" y="42" width="34" height="26" rx="6" fill="#38bdf8" fillOpacity="0.12" stroke="#38bdf8" strokeOpacity="0.45" strokeWidth="1.5" />
        <circle cx="133" cy="55" r="4" stroke="#38bdf8" strokeOpacity="0.8" strokeWidth="1.6" />
        <path d="M44 55h19M97 55h19" stroke="#6b7688" strokeWidth="1.5" strokeDasharray="3 3" />
        <path d="M60 52l4 3-4 3" stroke="#6b7688" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" />
        <path d="M113 52l4 3-4 3" stroke="#6b7688" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" />
      </svg>
    );
  }
  if (art === 'instance') {
    return (
      <svg viewBox="0 0 160 110" className="w-full h-full" fill="none">
        <rect x="34" y="20" width="92" height="62" rx="8" fill="#6d74e8" fillOpacity="0.09" stroke="#6d74e8" strokeOpacity="0.4" strokeWidth="1.5" />
        <path d="M34 36h92" stroke="#6d74e8" strokeOpacity="0.3" strokeWidth="1.5" />
        <circle cx="44" cy="28" r="2.5" fill="#6d74e8" fillOpacity="0.7" />
        <circle cx="53" cy="28" r="2.5" fill="#6d74e8" fillOpacity="0.4" />
        <rect x="46" y="46" width="40" height="5" rx="2.5" fill="#8a94a6" fillOpacity="0.5" />
        <rect x="46" y="57" width="62" height="4" rx="2" fill="#8a94a6" fillOpacity="0.3" />
        <rect x="46" y="66" width="30" height="4" rx="2" fill="#8a94a6" fillOpacity="0.3" />
        <circle cx="112" cy="70" r="11" fill="#4ade80" fillOpacity="0.12" stroke="#4ade80" strokeOpacity="0.5" strokeWidth="1.5" />
        <path d="M107 70l3.5 3.5L117 67" stroke="#4ade80" strokeOpacity="0.9" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
      </svg>
    );
  }
  if (art === 'task') {
    return (
      <svg viewBox="0 0 160 110" className="w-full h-full" fill="none">
        <rect x="44" y="22" width="72" height="70" rx="8" fill="#6d74e8" fillOpacity="0.08" stroke="#6d74e8" strokeOpacity="0.35" strokeWidth="1.5" />
        <rect x="56" y="38" width="12" height="12" rx="3" stroke="#8a94a6" strokeOpacity="0.6" strokeWidth="1.5" />
        <rect x="76" y="42" width="30" height="4" rx="2" fill="#8a94a6" fillOpacity="0.4" />
        <rect x="56" y="58" width="12" height="12" rx="3" stroke="#8a94a6" strokeOpacity="0.6" strokeWidth="1.5" />
        <rect x="76" y="62" width="22" height="4" rx="2" fill="#8a94a6" fillOpacity="0.4" />
        <circle cx="112" cy="82" r="14" fill="#4ade80" fillOpacity="0.14" stroke="#4ade80" strokeOpacity="0.6" strokeWidth="1.5" />
        <path d="M105 82l5 5 9-10" stroke="#4ade80" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
      </svg>
    );
  }
  if (art === 'chart') {
    return (
      <svg viewBox="0 0 160 110" className="w-full h-full" fill="none">
        <path d="M30 88h100" stroke="#6b7688" strokeOpacity="0.5" strokeWidth="1.5" />
        <path d="M30 88V24" stroke="#6b7688" strokeOpacity="0.5" strokeWidth="1.5" />
        <rect x="44" y="62" width="16" height="26" rx="3" fill="#6d74e8" fillOpacity="0.5" />
        <rect x="68" y="48" width="16" height="40" rx="3" fill="#6d74e8" fillOpacity="0.7" />
        <rect x="92" y="36" width="16" height="52" rx="3" fill="#6d74e8" fillOpacity="0.55" />
        <rect x="116" y="52" width="16" height="36" rx="3" fill="#4ade80" fillOpacity="0.5" />
      </svg>
    );
  }
  return (
    <svg viewBox="0 0 160 110" className="w-full h-full" fill="none">
      <path d="M42 40h76l-8 50H50L42 40Z" fill="#6d74e8" fillOpacity="0.1" stroke="#6d74e8" strokeOpacity="0.4" strokeWidth="1.5" strokeLinejoin="round" />
      <path d="M58 40V32a22 22 0 0 1 44 0v8" stroke="#6d74e8" strokeOpacity="0.5" strokeWidth="1.5" />
      <path d="M56 58h48M56 70h34" stroke="#8a94a6" strokeOpacity="0.35" strokeWidth="3" strokeLinecap="round" />
    </svg>
  );
}

export default function EmptyStateArt({
  art = 'flow',
  title,
  hint,
  action,
}: {
  art?: Art;
  title: string;
  hint?: string;
  action?: { label: string; onClick: () => void };
}) {
  return (
    <div className="flex-1 flex flex-col items-center justify-center text-center px-6 py-10"
      style={{ background: 'radial-gradient(circle at 50% 45%, rgba(109,116,232,0.06), transparent 60%)' }}>
      <div className="w-52 h-36 mb-5 opacity-95"><Illustration art={art} /></div>
      <div className="text-[13px] font-medium text-gray-300 mb-1.5">{title}</div>
      {hint && <div className="text-[11px] text-gray-500 max-w-[260px] leading-relaxed mb-4">{hint}</div>}
      {action && (
        <button onClick={action.onClick}
          className="inline-flex items-center gap-1.5 px-3.5 py-2 rounded-lg bg-accent/15 hover:bg-accent/25
            text-accent-hover text-xs font-medium ring-1 ring-inset ring-accent/30 transition-all duration-150 ease-swift
            hover:shadow-glow">
          {action.label}
        </button>
      )}
    </div>
  );
}
