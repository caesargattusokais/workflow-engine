import { Handle, Position, type NodeProps } from '@xyflow/react';
import NodeShell from './NodeShell';
import { GitMerge } from 'lucide-react';
import { useT } from '../../i18n';

export default function InclusiveGatewayNode({ data }: NodeProps) {
  const { t } = useT();
  return (
    <NodeShell
      data={data}
      icon={<GitMerge size={15} strokeWidth={2} />}
      tint={'#c084fc'}
      kind="OR gateway"
      sub={t.nodes.or}
    />
  );
}
