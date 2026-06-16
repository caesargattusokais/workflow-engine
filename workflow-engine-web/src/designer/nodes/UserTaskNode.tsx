import { Handle, Position, type NodeProps } from '@xyflow/react';

export default function UserTaskNode({ data }: NodeProps) {
  return (
    <div className="relative">
      <Handle type="target" position={Position.Top} className="!bg-blue-400" />
      <div className="min-w-[120px] px-4 py-2 rounded-lg bg-blue-600 border border-blue-500
                      flex items-center gap-2 text-sm text-white shadow-lg">
        <span className="text-lg">&#128100;</span>
        <span className="truncate max-w-[100px]">{data.name as string}</span>
      </div>
      <Handle type="source" position={Position.Bottom} className="!bg-blue-400" />
    </div>
  );
}
