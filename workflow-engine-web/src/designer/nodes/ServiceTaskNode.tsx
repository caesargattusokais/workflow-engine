import { Handle, Position, type NodeProps } from '@xyflow/react';

export default function ServiceTaskNode({ data }: NodeProps) {
  return (
    <div className="relative">
      <Handle type="target" position={Position.Top} className="!bg-purple-400" />
      <div className="min-w-[120px] px-4 py-2 rounded-lg bg-purple-700 border border-purple-500
                      flex items-center gap-2 text-sm text-white shadow-lg">
        <span className="text-lg">&#9881;</span>
        <span className="truncate max-w-[100px]">{data.name as string}</span>
      </div>
      <Handle type="source" position={Position.Bottom} className="!bg-purple-400" />
    </div>
  );
}
