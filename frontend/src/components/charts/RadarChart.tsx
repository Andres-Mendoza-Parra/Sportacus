import Svg, { Circle, Line, Polygon, Text as SvgText } from "react-native-svg";

import { useTheme } from "../../theme";
import { useAnimatedNumber } from "../ui/motion";

interface RadarPointInput {
  label: string;
  value: number; // 0-100
}

interface RadarChartProps {
  points: RadarPointInput[];
  size?: number;
}

const RINGS = [0.33, 0.66, 1];

/**
 * Gráfico de araña genérico de N ejes, dibujado en SVG puro (sin librería de
 * gráficos externa). Los valores de "points" son 0-100 y vienen del backend
 * (widget radar del mundo). El polígono "crece" al aparecer y cuando cambian los datos.
 */
export default function RadarChart({ points, size = 240 }: RadarChartProps) {
  const { colors } = useTheme();
  const grow = useAnimatedNumber(1, 900);
  const center = size / 2;
  const radius = size * 0.34;
  const labelRadius = radius + 24;
  const n = points.length;

  function coordAt(index: number, fraction: number) {
    const angle = (Math.PI * 2 * index) / n - Math.PI / 2;
    return {
      x: center + radius * fraction * Math.cos(angle),
      y: center + radius * fraction * Math.sin(angle),
    };
  }

  function labelCoordAt(index: number) {
    const angle = (Math.PI * 2 * index) / n - Math.PI / 2;
    return {
      x: center + labelRadius * Math.cos(angle),
      y: center + labelRadius * Math.sin(angle) + 3,
    };
  }

  const dataCoords = points.map((p, i) => coordAt(i, (Math.max(0, Math.min(100, p.value)) / 100) * grow));
  const dataPolygon = dataCoords.map(({ x, y }) => `${x},${y}`).join(" ");
  const height = size + 28;

  return (
    <Svg width={size} height={height} viewBox={`0 0 ${size} ${height}`}>
      {RINGS.map((fraction, ringIdx) => {
        const ringPoints = points
          .map((_, i) => {
            const { x, y } = coordAt(i, fraction);
            return `${x},${y}`;
          })
          .join(" ");
        return <Polygon key={`ring-${ringIdx}`} points={ringPoints} fill="none" stroke={colors.border} strokeWidth={1} />;
      })}

      {points.map((_, i) => {
        const outer = coordAt(i, 1);
        return <Line key={`axis-${i}`} x1={center} y1={center} x2={outer.x} y2={outer.y} stroke={colors.border} strokeWidth={1} />;
      })}

      <Polygon points={dataPolygon} fill={colors.primary} fillOpacity={0.25} stroke={colors.primary} strokeWidth={2.5} strokeLinejoin="round" />
      {dataCoords.map(({ x, y }, i) => (
        <Circle key={`dot-${i}`} cx={x} cy={y} r={3.5} fill={colors.primary} />
      ))}

      {points.map((p, i) => {
        const { x, y } = labelCoordAt(i);
        return (
          <SvgText key={`label-${i}`} x={x} y={y} fontSize="11" fill={colors.textMuted} textAnchor="middle">
            {p.label}
          </SvgText>
        );
      })}
    </Svg>
  );
}
