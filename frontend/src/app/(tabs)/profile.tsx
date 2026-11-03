import { router, useFocusEffect } from "expo-router";
import { useCallback, useState } from "react";
import { RefreshControl, ScrollView, Text, View } from "react-native";
import Animated, { FadeInDown } from "react-native-reanimated";

import { useConfirm, useToast } from "../../components/ui/Feedback";
import GradientHeader from "../../components/ui/GradientHeader";
import PressableScale from "../../components/ui/PressableScale";
import ProgressRing from "../../components/ui/ProgressRing";
import { ScreenSkeleton } from "../../components/ui/Skeleton";
import { useAuth } from "../../context/AuthContext";
import { extractErrorMessage } from "../../services/api";
import { ProfileData, fetchProfile } from "../../services/profileService";
import { Theme, useThemedStyles, useTheme } from "../../theme";

export default function ProfileScreen() {
  const styles = useThemedStyles(createStyles);
  const { colors, formatWeight, formatHeight } = useTheme();
  const { logout, updateUser } = useAuth();
  const toast = useToast();
  const confirm = useConfirm();
  const [profile, setProfile] = useState<ProfileData | null>(null);
  const [isRefreshing, setIsRefreshing] = useState(false);

  const load = useCallback(async () => {
    try {
      const data = await fetchProfile();
      setProfile(data);
      if (data.name) updateUser({ name: data.name }).catch(() => {});
    } catch (error) {
      toast.error(extractErrorMessage(error));
    } finally {
      setIsRefreshing(false);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [toast]);

  useFocusEffect(
    useCallback(() => {
      load();
    }, [load]),
  );

  async function handleLogout() {
    const ok = await confirm({
      title: "Cerrar sesión",
      message: "Tendrás que ingresar tu correo y contraseña de nuevo.",
      confirmText: "Cerrar sesión",
      destructive: true,
    });
    if (!ok) return;
    await logout();
    router.replace("/login");
  }

  if (!profile) return <ScreenSkeleton cards={4} />;

  const memberSince = profile.memberSince
    ? new Date(profile.memberSince).toLocaleDateString("es", { month: "long", year: "numeric" })
    : null;

  return (
    <ScrollView
      style={styles.bg}
      showsVerticalScrollIndicator={false}
      refreshControl={
        <RefreshControl
          refreshing={isRefreshing}
          onRefresh={() => {
            setIsRefreshing(true);
            load();
          }}
          tintColor={colors.primary}
        />
      }
    >
      <GradientHeader rounded style={{ alignItems: "center", paddingBottom: 30 }}>
        <Animated.View entering={FadeInDown.duration(400)} style={{ alignItems: "center", width: "100%" }}>
          <View style={styles.avatarWrap}>
            <ProgressRing pct={profile.profileStrengthPct} size={96} stroke={5} color="#fff" trackColor="rgba(255,255,255,0.25)" label="" />
            <View style={styles.avatar}>
              <Text style={styles.avatarLetter}>{(profile.name || profile.email).charAt(0).toUpperCase()}</Text>
            </View>
          </View>
          <Text style={styles.name}>{profile.name}</Text>
          <Text style={styles.sub}>
            {profile.activeModule ? `Ruta activa · ${profile.activeModule}` : profile.email}
          </Text>
          <Text style={styles.strengthLabel}>Perfil afinado al {profile.profileStrengthPct}%</Text>

          <View style={styles.badges}>
            <Badge value={profile.age === null ? "—" : String(profile.age)} label="Años" />
            <Badge value={formatWeight(profile.weightKg)} label="Peso" />
            <Badge value={formatHeight(profile.heightCm)} label="Altura" />
          </View>
        </Animated.View>
      </GradientHeader>

      {profile.suggestions.length > 0 ? (
        <Animated.View entering={FadeInDown.delay(100).duration(400)} style={styles.section}>
          <Text style={styles.sectionTitle}>Para afinar tu perfil</Text>
          <View style={styles.card}>
            {profile.suggestions.slice(0, 3).map((s, i) => (
              <Row
                key={s.questionnaireKey}
                icon="✨"
                tint={colors.primarySoft}
                label={s.title}
                sub={s.reason}
                trailing={`+${s.impactPct}%`}
                last={i === Math.min(2, profile.suggestions.length - 1)}
                onPress={() => router.push(`/questionnaires/${s.questionnaireKey}`)}
              />
            ))}
          </View>
        </Animated.View>
      ) : null}

      <Animated.View entering={FadeInDown.delay(160).duration(400)} style={styles.section}>
        <Text style={styles.sectionTitle}>Mi perfil</Text>
        <View style={styles.card}>
          <Row icon="📋" tint={colors.primarySoft} label="Mis datos y cuestionarios" onPress={() => router.push("/questionnaires")} />
          <Row icon="🎯" tint={colors.infoSoft} label="Objetivos y preferencias" onPress={() => router.push("/questionnaires/objetivo")} />
          <Row icon="🏠" tint={colors.warnSoft} label="Entorno y recursos" last onPress={() => router.push("/questionnaires/entorno")} />
        </View>
      </Animated.View>

      <Animated.View entering={FadeInDown.delay(190).duration(400)} style={styles.section}>
        <Text style={styles.sectionTitle}>Mi seguimiento</Text>
        <View style={styles.card}>
          <Row icon="🔥" tint={colors.warnSoft} label="Mi progreso" sub="Racha, logros y evolución" onPress={() => router.push("/progress")} />
          <Row icon="📏" tint={colors.infoSoft} label="Evaluación física" sub="Pruebas guiadas de fuerza y movilidad" onPress={() => router.push("/tests")} />
          <Row icon="📅" tint={colors.primarySoft} label="Plan semanal" sub="Días, horas y calendario" onPress={() => router.push("/plan")} />
          <Row icon="📚" tint={colors.cardAlt} label="Biblioteca de ejercicios" sub="Explora y añade a tu rutina" onPress={() => router.push("/library")} />
          <Row icon="🛠" tint={colors.infoSoft} label="Taller de rutina" sub="Crea tu rutina día por día" last onPress={() => router.push("/workshop")} />
        </View>
      </Animated.View>

      <Animated.View entering={FadeInDown.delay(220).duration(400)} style={styles.section}>
        <Text style={styles.sectionTitle}>Módulos</Text>
        <View style={styles.card}>
          {profile.modules.length === 0 ? (
            <Row
              icon="🏃"
              tint={colors.cardAlt}
              label="Elige tu módulo"
              sub="Así armamos tu rutina y tu biblioteca"
              last
              onPress={() => router.push("/onboarding/module?mode=switch")}
            />
          ) : (
            profile.modules.map((m, i) => (
              <Row
                key={m.name}
                icon={m.status === "activo" ? "✅" : "🏃"}
                tint={m.status === "activo" ? colors.successSoft : colors.cardAlt}
                label={m.name}
                sub={m.status === "activo" ? "Tu módulo actual · tu biblioteca se adapta a él" : undefined}
                muted={m.status !== "activo"}
                trailing={m.status === "activo" ? "Activo" : "Cambiar"}
                last={i === profile.modules.length - 1}
                onPress={() => router.push("/onboarding/module?mode=switch")}
              />
            ))
          )}
        </View>
      </Animated.View>

      <Animated.View entering={FadeInDown.delay(280).duration(400)} style={styles.section}>
        <Text style={styles.sectionTitle}>Configuración</Text>
        <View style={styles.card}>
          <Row icon="🎨" tint={colors.infoSoft} label="Apariencia" sub="Tema y color de acento" onPress={() => router.push("/settings")} />
          <Row icon="🔔" tint={colors.infoSoft} label="Notificaciones" sub="Recordatorios de entrenamiento" onPress={() => router.push("/settings")} />
          <Row icon="⚙️" tint={colors.infoSoft} label="Ajustes y privacidad" sub="Cuenta, unidades y datos" onPress={() => router.push("/settings")} />
          <Row icon="🚪" tint={colors.dangerSoft} label="Cerrar sesión" danger last onPress={handleLogout} />
        </View>
        {memberSince ? <Text style={styles.member}>Miembro desde {memberSince}</Text> : null}
      </Animated.View>

      <View style={{ height: 28 }} />
    </ScrollView>
  );
}

function Badge({ value, label }: { value: string; label: string }) {
  const styles = useThemedStyles(createStyles);
  return (
    <View style={styles.badge}>
      <Text style={styles.badgeVal}>{value}</Text>
      <Text style={styles.badgeLbl}>{label}</Text>
    </View>
  );
}

function Row({
  icon,
  tint,
  label,
  sub,
  trailing,
  onPress,
  danger,
  muted,
  last,
}: {
  icon: string;
  tint: string;
  label: string;
  sub?: string;
  trailing?: string;
  onPress?: () => void;
  danger?: boolean;
  muted?: boolean;
  last?: boolean;
}) {
  const styles = useThemedStyles(createStyles);
  const content = (
    <>
      <View style={[styles.rowIcon, { backgroundColor: tint }]}>
        <Text>{icon}</Text>
      </View>
      <View style={{ flex: 1 }}>
        <Text style={[styles.rowLabel, danger && styles.rowDanger, muted && styles.rowMuted]} numberOfLines={1}>
          {label}
        </Text>
        {sub ? (
          <Text style={styles.rowSub} numberOfLines={2}>
            {sub}
          </Text>
        ) : null}
      </View>
      {trailing ? <Text style={styles.pill}>{trailing}</Text> : null}
      {onPress && !danger ? <Text style={styles.arrow}>›</Text> : null}
    </>
  );
  const rowStyle = [styles.row, !last && styles.rowDivider];
  return onPress ? (
    <PressableScale style={rowStyle} onPress={onPress} scaleTo={0.985}>
      {content}
    </PressableScale>
  ) : (
    <View style={rowStyle}>{content}</View>
  );
}

const createStyles = ({ colors, shadow }: Theme) => ({
  bg: { flex: 1, backgroundColor: colors.bg },
  avatarWrap: { width: 96, height: 96, alignItems: "center" as const, justifyContent: "center" as const, marginBottom: 12 },
  avatar: {
    position: "absolute" as const,
    width: 76,
    height: 76,
    borderRadius: 38,
    backgroundColor: "#fff",
    alignItems: "center" as const,
    justifyContent: "center" as const,
  },
  avatarLetter: { fontSize: 32, fontWeight: "700" as const, color: colors.primaryDark },
  name: { color: "#fff", fontSize: 21, fontWeight: "700" as const },
  sub: { color: "rgba(255,255,255,0.8)", fontSize: 13, marginTop: 3, textAlign: "center" as const },
  strengthLabel: { color: "rgba(255,255,255,0.9)", fontSize: 12, fontWeight: "600" as const, marginTop: 6 },
  badges: { flexDirection: "row" as const, gap: 10, marginTop: 16 },
  badge: { backgroundColor: "rgba(255,255,255,0.16)", borderRadius: 14, paddingVertical: 9, paddingHorizontal: 16, alignItems: "center" as const, minWidth: 80 },
  badgeVal: { color: "#fff", fontSize: 16, fontWeight: "700" as const },
  badgeLbl: { color: "rgba(255,255,255,0.75)", fontSize: 11 },
  section: { marginHorizontal: 16, marginTop: 20 },
  sectionTitle: { fontSize: 11, color: colors.textFaint, fontWeight: "700" as const, textTransform: "uppercase" as const, letterSpacing: 0.8, marginBottom: 8 },
  card: { backgroundColor: colors.card, borderRadius: 18, overflow: "hidden" as const, ...shadow },
  row: { flexDirection: "row" as const, alignItems: "center" as const, gap: 12, paddingVertical: 13, paddingHorizontal: 14 },
  rowDivider: { borderBottomWidth: 1, borderBottomColor: colors.border },
  rowIcon: { width: 34, height: 34, borderRadius: 11, alignItems: "center" as const, justifyContent: "center" as const },
  rowLabel: { fontSize: 14, color: colors.text, fontWeight: "500" as const },
  rowSub: { fontSize: 12, color: colors.textMuted, marginTop: 2 },
  rowDanger: { color: colors.danger },
  rowMuted: { color: colors.textFaint },
  emptyRow: { padding: 16 },
  arrow: { color: colors.textFaint, fontSize: 22 },
  pill: { fontSize: 11, paddingVertical: 3, paddingHorizontal: 9, borderRadius: 8, overflow: "hidden" as const, fontWeight: "700" as const, backgroundColor: colors.primarySoft, color: colors.success },
  member: { textAlign: "center" as const, color: colors.textFaint, fontSize: 12, marginTop: 14 },
});
