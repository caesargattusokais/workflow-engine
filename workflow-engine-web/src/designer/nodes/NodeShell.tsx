import { Handle, Position } from '@xyflow/react';

/**
 * Shared node shell.
 *
 * The node is intentionally LIGHTER than the canvas (canvas ~#171c27,
 * node ~#262c3a) so it reads as a card floating above the surface instead
 * of merging into a single black field. A tinted sheen plus a top accent
 * bar keeps each node type recognisable without loud solid fills.
 */
export default function NodeShell({ data, icon, tint, kind, sub }: {
  data: any;
  icon: React.ReactNode;
  tint: string; // e.g. '#60a5fa'
  kind?: string;
  sub?: React.ReactNode;
  shape?: 'card' | 'round';
}) {
  return (
    <div className="relative">
      <Handle
        type="target"
        position={Position.Top}
        className="!w-2 !h-2 !border-0"
        style={{ background: tint, boxShadow: `0 0 0 2px #171c27` }}
      />
      <div
        className="node-card min-w-[148px] rounded-xl overflow-hidden"
        style={{
          background: `linear-gradient(135deg, ${tint}1c 0%, rgba(255,255,255,0.03) 45%, rgba(255,255,255,0) 100%), #262c3a`,
          border: `1px solid ${tint}66`,
          boxShadow: 'inset 0 1px 0 rgba(255,255,255,0.09), 0 10px 26px rgba(0,0,0,0.45)',
        }}
      >
        {/* top accent — a thin, type-coloured bar */}
        <div style={{ height: 2, background: `linear-gradient(90deg, ${tint} 0%, ${tint}22 100%)` }} />
        <div className="px-3 py-2.5 flex items-center gap-2.5">
          <span
            className="w-8 h-8 rounded-lg flex items-center justify-center shrink-0"
            style={{ backgroundColor: `${tint}22`, color: tint, boxShadow: `inset 0 1px 0 ${tint}33` }}
          >
            {icon}
          </span>
          <div className="min-w-0">
            <div className="text-[13px] text-gray-50 font-semibold truncate max-w-[128px]">
              {data.name as string || kind}
            </div>
            <div className="text-[10px] mt-0.5 font-mono truncate max-w-[128px]" style={{ color: `${tint}cc` }}>
              {sub || kind}
            </div>
          </div>
        </div>
      </div>
      <Handle
        type="source"
        position={Position.Bottom}
        className="!w-2 !h-2 !border-0"
        style={{ background: tint, boxShadow: `0 0 0 2px #171c27` }}
      />
    </div>
  );
}
