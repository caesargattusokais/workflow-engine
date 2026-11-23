import { Handle, Position, type NodeProps } from '@xyflow/react';

/**
 * Shared node shell — a dark surface with a subtle tinted icon badge.
 * Keeps the semantic accent colour but drops the heavy solid fills + emoji
 * that made the canvas look like a prototype.
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
      <Handle type="target" position={Position.Top} className="!w-1.5 !h-1.5 !border-0" style={{ background: tint }} />
      <div className="min-w-[132px] px-3 py-2.5 rounded-lg bg-[#1c2029]/95 border shadow-panel
                      flex items-center gap-2.5 backdrop-blur-sm"
        style={{ borderColor: tint + '55' }}>
        <span className="w-7 h-7 rounded-md flex items-center justify-center shrink-0"
          style={{ backgroundColor: tint + '1f', color: tint }}>
          {icon}
        </span>
        <div className="min-w-0">
          <div className="text-xs text-gray-100 font-medium truncate max-w-[110px]">{data.name as string || kind}</div>
          <div className="text-[10px] mt-0.5 font-mono truncate max-w-[110px]" style={{ color: tint + 'cc' }}>
            {sub || kind}
          </div>
        </div>
      </div>
      <Handle type="source" position={Position.Bottom} className="!w-1.5 !h-1.5 !border-0" style={{ background: tint }} />
    </div>
  );
}
