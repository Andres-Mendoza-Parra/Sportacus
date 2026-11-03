import { Text, View } from "react-native";

import { useThemedStyles } from "../../theme";
import PressableScale from "./PressableScale";

interface Props {
  label: string;
  value: number;
  min: number;
  max: number;
  step?: number;
  disabled?: boolean;
  onChange: (value: number) => void;
}

/** Control +/- para números pequeños (series, reps, segundos) usado en el Taller de rutina. */
export default function Stepper({ label, value, min, max, step = 1, disabled, onChange }: Props) {
  const styles = useThemedStyles(createStyles);
  return (
    <View style={styles.stepper}>
      <Text style={styles.label}>{label}</Text>
      <View style={styles.row}>
        <PressableScale style={styles.btn} disabled={disabled || value <= min} onPress={() => onChange(Math.max(min, value - step))} scaleTo={0.88}>
          <Text style={styles.btnText}>−</Text>
        </PressableScale>
        <Text style={styles.value}>{value}</Text>
        <PressableScale style={styles.btn} disabled={disabled || value >= max} onPress={() => onChange(Math.min(max, value + step))} scaleTo={0.88}>
          <Text style={styles.btnText}>+</Text>
        </PressableScale>
      </View>
    </View>
  );
}

const createStyles = ({ colors }: import("../../theme").Theme) => ({
  stepper: { flex: 1 },
  label: { color: colors.textFaint, fontSize: 11, fontWeight: "700" as const, textTransform: "uppercase" as const, marginBottom: 6 },
  row: { flexDirection: "row" as const, alignItems: "center" as const, gap: 10 },
  btn: { width: 30, height: 30, borderRadius: 15, backgroundColor: colors.cardAlt, alignItems: "center" as const, justifyContent: "center" as const },
  btnText: { color: colors.primary, fontSize: 17, fontWeight: "800" as const },
  value: { color: colors.text, fontSize: 15, fontWeight: "700" as const, minWidth: 28, textAlign: "center" as const },
});
