import { Handle, Position, type NodeProps } from '@xyflow/react';
import NodeShell from './NodeShell';
import { UserRound } from 'lucide-react';

export default function UserTaskNode({ data }: NodeProps) {
  return (
    <NodeShell
      data={data}
      icon={<UserRound size={15} strokeWidth={2} />}
      tint={'#60a5fa'}
      kind="User task"
      sub={data.name as string || "User task"}
    />
  );
}
