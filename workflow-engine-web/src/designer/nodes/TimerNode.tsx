import { Handle, Position, type NodeProps } from '@xyflow/react';

export default function TimerNode({ data }: NodeProps) {
  const label = data.duration ? `⏱ ${data.duration}` : data.deadline ? `⏰ ${(data.deadline as string).substring(0,10)}` : 'Timer';
  return (
    <div className="relative">
      <Handle type="target" position={Position.Top} className="!bg-amber-400" />
      <div className="min-w-[100px] px-3 py-2 rounded-lg bg-amber-600 border border-amber-500
                      flex items-center gap-2 text-sm text-white shadow-lg">
        <span className="text-lg">⏱</span>
        <span className="truncate max-w-[100px]">{data.name as string || 'Timer'}</span>
      </div>
      <Handle type="source" position={Position.Bottom} className="!bg-amber-400" />
    </div>
  );
}
