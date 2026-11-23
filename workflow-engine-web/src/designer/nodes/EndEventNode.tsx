import { Handle, Position, type NodeProps } from '@xyflow/react';
import NodeShell from './NodeShell';
import { CircleStop } from 'lucide-react';

export default function EndEventNode({ data }: NodeProps) {
  return (
    <NodeShell
      data={data}
      icon={<CircleStop size={15} strokeWidth={2} />}
      tint={'#f87171'}
      kind="End"
      sub={data.name as string || "End"}
    />
  );
}
