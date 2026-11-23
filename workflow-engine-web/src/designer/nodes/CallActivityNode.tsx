import { Handle, Position, type NodeProps } from '@xyflow/react';
import NodeShell from './NodeShell';
import { Boxes } from 'lucide-react';

export default function CallActivityNode({ data }: NodeProps) {
  const calledId = (data.calledId as string) || '';
  return (
    <NodeShell
      data={data}
      icon={<Boxes size={15} strokeWidth={2} />}
      tint={'#a78bfa'}
      kind="Call activity"
      sub={calledId || "Call activity"}
    />
  );
}
