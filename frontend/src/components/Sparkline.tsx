export function Sparkline({
  values,
  width = 120,
  height = 32,
  isPositive,
}: {
  values: number[];
  width?: number;
  height?: number;
  isPositive?: boolean;
}) {
  if (values.length < 2) {
    return <svg width={width} height={height} role="img" aria-hidden="true" />;
  }

  const min = Math.min(...values);
  const max = Math.max(...values);
  const range = max - min || 1;
  const stepX = width / (values.length - 1);
  const points = values
    .map((value, index) => `${(index * stepX).toFixed(2)},${(height - ((value - min) / range) * height).toFixed(2)}`)
    .join(' ');
  const up = isPositive ?? values[values.length - 1] >= values[0];
  const stroke = up ? 'var(--color-success)' : 'var(--color-error)';

  return (
    <svg width={width} height={height} viewBox={`0 0 ${width} ${height}`} role="img" aria-hidden="true">
      <polyline points={points} fill="none" stroke={stroke} strokeWidth={1.5} strokeLinejoin="round" strokeLinecap="round" />
    </svg>
  );
}
