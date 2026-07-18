import { Handle, Position, type NodeProps } from '@xyflow/react';

export default function CallActivityNode({ data }: NodeProps) {
  const calledId = data.calledId as string || '';

  return (
    <div className="relative">
      <Handle type="target" position={Position.Top} className="!bg-violet-400" />
      <div className="min-w-[120px] px-4 py-2 rounded-lg bg-violet-700 border-2 border-violet-500
                      flex items-center gap-2 text-sm text-white shadow-lg shadow-violet-500/20">
        <span className="text-lg font-bold">+&crarr;</span>
        <div>
          <div className="truncate max-w-[120px]">{data.name as string || 'Call Activity'}</div>
          {calledId && (
            <div className="text-[10px] opacity-60 truncate max-w-[120px]">{calledId}</div>
          )}
        </div>
      </div>
      <Handle type="source" position={Position.Bottom} className="!bg-violet-400" />
    </div>
  );
}
