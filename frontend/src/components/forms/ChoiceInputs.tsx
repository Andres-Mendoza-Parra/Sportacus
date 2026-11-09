import React from "react";
import { Text, View } from "react-native";
import Animated, { FadeIn } from "react-native-reanimated";

import { QuestionOption } from "../../services/types";
import { Theme, useThemedStyles } from "../../theme";
import PressableScale from "../ui/PressableScale";

interface OptionRowProps {
  option: QuestionOption;
  selected: boolean;
  multi: boolean;
  index: number;
  onPress: () => void;
}

function OptionRow({ option, selected, multi, index, onPress }: OptionRowProps) {
  const styles = useThemedStyles(createStyles);
  return (
    <Animated.View entering={FadeIn.delay(index * 35).duration(240)}>
      <PressableScale
        style={[styles.option, selected && styles.optionSelected]}
        onPress={onPress}
        accessibilityRole={multi ? "checkbox" : "radio"}
        accessibilityState={{ checked: selected }}
        accessibilityLabel={option.text}
      >
        {option.icon ? (
          <View style={[styles.iconWrap, selected && styles.iconWrapSelected]}>
            <Text style={styles.icon}>{option.icon}</Text>
          </View>
        ) : null}
        <View style={{ flex: 1 }}>
          <Text style={styles.text}>{option.text}</Text>
          {option.sub ? <Text style={styles.sub}>{option.sub}</Text> : null}
        </View>
        <View style={[multi ? styles.box : styles.radio, selected && styles.markOn]}>
          {selected ? <Text style={styles.markText}>✓</Text> : null}
        </View>
      </PressableScale>
    </Animated.View>
  );
}

/** Opción única: lista de tarjetas seleccionables. */
export function OptionsInput({ options, value, onChange }: { options: QuestionOption[]; value: string | undefined; onChange: (id: string) => void }) {
  return (
    <View style={{ gap: 10 }}>
      {options.map((opt, i) => (
        <OptionRow key={opt.id} option={opt} index={i} multi={false} selected={value === opt.id} onPress={() => onChange(opt.id)} />
      ))}
    </View>
  );
}

/** Selección múltiple; `max` limita las elecciones (el padre valida y avisa). */
export function MultiInput({ options, value, max, onToggle }: { options: QuestionOption[]; value: string[]; max?: number | null; onToggle: (id: string) => void }) {
  const styles = useThemedStyles(createStyles);
  return (
    <View style={{ gap: 10 }}>
      {max ? <Text style={styles.counter}>{`${value.length} de ${max} elegidas`}</Text> : null}
      {options.map((opt, i) => (
        <OptionRow key={opt.id} option={opt} index={i} multi selected={value.includes(opt.id)} onPress={() => onToggle(opt.id)} />
      ))}
    </View>
  );
}

const createStyles = ({ colors }: Theme) => ({
  option: {
    padding: 13,
    borderWidth: 1.5,
    borderColor: colors.border,
    borderRadius: 16,
    backgroundColor: colors.card,
    flexDirection: "row" as const,
    alignItems: "center" as const,
    gap: 12,
  },
  optionSelected: { borderColor: colors.primary, backgroundColor: colors.primarySoft },
  iconWrap: { width: 40, height: 40, borderRadius: 13, backgroundColor: colors.cardAlt, alignItems: "center" as const, justifyContent: "center" as const },
  iconWrapSelected: { backgroundColor: colors.card },
  icon: { fontSize: 19 },
  text: { fontSize: 14, fontWeight: "600" as const, color: colors.text },
  sub: { fontSize: 12, color: colors.textMuted, marginTop: 1 },
  radio: { width: 22, height: 22, borderRadius: 11, borderWidth: 2, borderColor: colors.textFaint, alignItems: "center" as const, justifyContent: "center" as const },
  box: { width: 22, height: 22, borderRadius: 7, borderWidth: 2, borderColor: colors.textFaint, alignItems: "center" as const, justifyContent: "center" as const },
  markOn: { backgroundColor: colors.primary, borderColor: colors.primary },
  markText: { color: colors.onPrimary, fontSize: 12, fontWeight: "800" as const },
  counter: { fontSize: 12, color: colors.textMuted, fontWeight: "600" as const },
});
