import React from "react";
import { Modal, Pressable, StyleSheet, Text, View } from "react-native";
import Animated, { BounceIn, FadeIn, FadeInUp, ZoomIn } from "react-native-reanimated";

import { GoalView } from "../../services/types";
import { Theme, useThemedStyles, useTheme } from "../../theme";
import GradientButton from "../ui/GradientButton";

const CONFETTI = ["🎉", "✨", "🏆", "🎊", "⭐", "💪"];

/** Celebración al marcar una meta como lograda. */
export default function GoalCelebration({ goal, onClose }: { goal: GoalView | null; onClose: () => void }) {
  const styles = useThemedStyles(createStyles);
  const { colors } = useTheme();
  return (
    <Modal visible={goal != null} transparent animationType="none" onRequestClose={onClose} statusBarTranslucent>
      {goal ? (
        <View style={styles.root}>
          <Animated.View entering={FadeIn.duration(180)} style={StyleSheet.absoluteFill}>
            <Pressable style={[StyleSheet.absoluteFill, { backgroundColor: colors.overlay }]} onPress={onClose} accessibilityLabel="Cerrar celebración" />
          </Animated.View>
          <Animated.View entering={ZoomIn.springify().damping(14)} style={styles.card} accessibilityViewIsModal accessibilityLiveRegion="assertive">
            <View style={styles.confetti}>
              {CONFETTI.map((c, i) => (
                <Animated.Text key={c} entering={FadeInUp.delay(120 + i * 70).springify()} style={{ fontSize: 22 }}>
                  {c}
                </Animated.Text>
              ))}
            </View>
            <Animated.Text entering={BounceIn.delay(150)} style={{ fontSize: 64, textAlign: "center" }}>
              🏆
            </Animated.Text>
            <Text style={styles.title} accessibilityRole="header">
              ¡Meta lograda!
            </Text>
            <Text style={styles.goal}>{goal.label}</Text>
            <Text style={styles.body}>Lo conseguiste con constancia. Guardamos este logro en tu progreso: ¿vamos por la siguiente?</Text>
            <GradientButton title="¡Seguimos!" onPress={onClose} style={{ alignSelf: "stretch", marginTop: 6 }} />
          </Animated.View>
        </View>
      ) : null}
    </Modal>
  );
}

const createStyles = ({ colors }: Theme) => ({
  root: { flex: 1, alignItems: "center" as const, justifyContent: "center" as const, padding: 24 },
  card: { width: "100%" as const, maxWidth: 380, backgroundColor: colors.card, borderRadius: 24, padding: 22, alignItems: "center" as const, gap: 8 },
  confetti: { flexDirection: "row" as const, gap: 8 },
  title: { fontSize: 22, fontWeight: "800" as const, color: colors.text, textAlign: "center" as const },
  goal: { fontSize: 15, fontWeight: "700" as const, color: colors.primary, textAlign: "center" as const },
  body: { fontSize: 13, color: colors.textMuted, textAlign: "center" as const, lineHeight: 19 },
});
