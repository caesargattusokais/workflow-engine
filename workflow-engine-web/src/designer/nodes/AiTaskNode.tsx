import { Handle, Position, type NodeProps } from '@xyflow/react';

const MODE_ICON: Record<string, string> = {
  summary: '≡',
  extract: '{}',
  classify: '◇',
};

export default function AiTaskNode({ data }: NodeProps) {
  const mode = (data.mode as string) || 'summary';
  return (
    <div className="relative">
      <Handle type="target" position={Position.Top} className="!bg-pink-400" />
      <div className="min-w-[120px] px-4 py-2 rounded-lg bg-pink-700 border border-pink-500
                      flex items-center gap-2 text-sm text-white shadow-lg">
        <span className="text-lg font-bold">{MODE_ICON[mode] || '✦'}</span>
        <div>
          <div className="truncate max-w-[100px]">{data.name as string}</div>
          <div className="text-[10px] opacity-60">AI · {mode}</div>
        </div>
      </div>
      <Handle type="source" position={Position.Bottom} className="!bg-pink-400" />
    </div>
  );
}
