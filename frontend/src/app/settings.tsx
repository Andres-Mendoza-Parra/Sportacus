import Constants from "expo-constants";
import { router } from "expo-router";
import { useEffect, useState } from "react";
import { Platform, ScrollView, Share, Switch, Text, View } from "react-native";
import Animated, { FadeInDown, LinearTransition } from "react-native-reanimated";

import { useConfirm, useToast } from "../components/ui/Feedback";
import GradientButton from "../components/ui/GradientButton";
import GradientHeader from "../components/ui/GradientHeader";
import PressableScale from "../components/ui/PressableScale";
import TextField from "../components/ui/TextField";
import { useAuth } from "../context/AuthContext";
import { extractErrorMessage } from "../services/api";
import { CARE_MODES, CareModeKey, loadCareMode, saveCareMode } from "../services/careMode";
import {
  changePassword,
  deleteAccount,
  exportProfileData,
  fetchProfile,
  updateProfile,
} from "../services/profileService";
import {
  DEFAULT_REMINDERS,
  IS_EXPO_GO,
  REMINDERS_SUPPORTED,
  ReminderPrefs,
  applyReminders,
  ensureNotificationPermission,
  loadReminderPrefs,
  saveReminderPrefs,
} from "../services/reminders";
import { ACCENTS, AccentKey, Theme, ThemePreference, useThemedStyles, useTheme } from "../theme";

const WEEKDAYS = [
  { value: 2, label: "L" },
  { value: 3, label: "M" },
  { value: 4, label: "X" },
  { value: 5, label: "J" },
  { value: 6, label: "V" },
  { value: 7, label: "S" },
  { value: 1, label: "D" },
];

const THEME_OPTIONS: { value: ThemePreference; label: string }[] = [
  { value: "system", label: "🌗 Sistema" },
  { value: "light", label: "☀️ Claro" },
  { value: "dark", label: "🌙 Oscuro" },
];

function pad(n: number) {
  return String(n).padStart(2, "0");
}

export default function SettingsScreen() {
  const styles = useThemedStyles(createStyles);
  const theme = useTheme();
  const { colors } = theme;
  const { logout, updateUser } = useAuth();
  const toast = useToast();
  const confirm = useConfirm();

  // Cuenta
  const [name, setName] = useState("");
  const [country, setCountry] = useState("");
  const [city, setCity] = useState("");
  const [savingProfile, setSavingProfile] = useState(false);
  const [nameError, setNameError] = useState<string | null>(null);

  // Contraseña
  const [currentPassword, setCurrentPassword] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [passwordErrors, setPasswordErrors] = useState<{ current?: string; next?: string; confirm?: string }>({});
  const [savingPassword, setSavingPassword] = useState(false);

  // Recordatorios
  const [reminders, setReminders] = useState<ReminderPrefs>(DEFAULT_REMINDERS);
  const [careMode, setCareMode] = useState<CareModeKey>("standard");

  // Datos
  const [exporting, setExporting] = useState(false);
  const [showDelete, setShowDelete] = useState(false);
  const [deletePassword, setDeletePassword] = useState("");
  const [deleting, setDeleting] = useState(false);

  useEffect(() => {
    loadReminderPrefs().then(setReminders);
    (async () => {
      try {
        const p = await fetchProfile();
        setName(p.name);
        setCountry(p.country ?? "");
        setCity(p.city ?? "");
      } catch (error) {
        toast.error(extractErrorMessage(error));
      }
    })();
  }, [toast]);

  // ───────────── Recordatorios ─────────────

  async function updateReminders(next: ReminderPrefs) {
    setReminders(next);
    await saveReminderPrefs(next);
    try {
      await applyReminders(next);
    } catch (error) {
      toast.error(`No se pudieron programar los recordatorios: ${extractErrorMessage(error)}`);
    }
  }

  async function toggleReminders(enabled: boolean) {
    if (!REMINDERS_SUPPORTED) {
      toast.info("Los recordatorios funcionan en la app móvil. Aquí solo guardamos tu preferencia.");
      await updateReminders({ ...reminders, enabled });
      return;
    }
    if (enabled) {
      const granted = await ensureNotificationPermission();
      if (!granted) {
        toast.error("Activa los permisos de notificaciones en los ajustes del sistema para recibir recordatorios.");
        return;
      }
    }
    await updateReminders({ ...reminders, enabled });
    toast.success(enabled ? "Recordatorios activados 🔔" : "Recordatorios desactivados");
  }

  function toggleWeekday(day: number) {
    const has = reminders.weekdays.includes(day);
    const weekdays = has ? reminders.weekdays.filter((d) => d !== day) : [...reminders.weekdays, day];
    updateReminders({ ...reminders, weekdays });
  }

  useEffect(() => {
    loadCareMode().then((c) => setCareMode(c.key));
  }, []);

  async function chooseCareMode(key: CareModeKey) {
    setCareMode(key);
    await saveCareMode(key).catch(() => {});
    toast.success(key === "standard" ? "Modo estándar" : `Modo ${CARE_MODES[key].label} activado`);
  }

  function shiftTime(deltaMinutes: number) {
    const total = (reminders.hour * 60 + reminders.minute + deltaMinutes + 1440) % 1440;
    updateReminders({ ...reminders, hour: Math.floor(total / 60), minute: total % 60 });
  }

  // ───────────── Cuenta ─────────────

  async function saveAccount() {
    if (!name.trim()) {
      setNameError("El nombre no puede estar vacío");
      return;
    }
    setNameError(null);
    setSavingProfile(true);
    try {
      const updated = await updateProfile({ name: name.trim(), country: country.trim(), city: city.trim() });
      await updateUser({ name: updated.name || name.trim() });
      toast.success("Datos de la cuenta actualizados");
    } catch (error) {
      toast.error(extractErrorMessage(error));
    } finally {
      setSavingProfile(false);
    }
  }

  async function savePassword() {
    const errors: typeof passwordErrors = {};
    if (!currentPassword) errors.current = "Ingresa tu contraseña actual";
    if (newPassword.length < 6) errors.next = "Debe tener al menos 6 caracteres";
    if (confirmPassword !== newPassword) errors.confirm = "Las contraseñas no coinciden";
    setPasswordErrors(errors);
    if (Object.keys(errors).length > 0) return;

    setSavingPassword(true);
    try {
      await changePassword(currentPassword, newPassword);
      setCurrentPassword("");
      setNewPassword("");
      setConfirmPassword("");
      toast.success("Contraseña actualizada");
    } catch (error) {
      toast.error(extractErrorMessage(error));
    } finally {
      setSavingPassword(false);
    }
  }

  // ───────────── Datos ─────────────

  async function handleExport() {
    setExporting(true);
    try {
      const data = await exportProfileData();
      const json = JSON.stringify(data, null, 2);
      if (Platform.OS === "web") {
        const g = globalThis as unknown as {
          Blob: new (parts: string[], options: { type: string }) => unknown;
          URL: { createObjectURL: (b: unknown) => string; revokeObjectURL: (u: string) => void };
          document: { createElement: (t: string) => { href: string; download: string; click: () => void } };
        };
        const url = g.URL.createObjectURL(new g.Blob([json], { type: "application/json" }));
        const link = g.document.createElement("a");
        link.href = url;
        link.download = "sportacus-mis-datos.json";
        link.click();
        g.URL.revokeObjectURL(url);
        toast.success("Descargamos tus datos en un archivo JSON");
      } else {
        await Share.share({ title: "Mis datos de Sportacus", message: json });
      }
    } catch (error) {
      toast.error(extractErrorMessage(error));
    } finally {
      setExporting(false);
    }
  }

  async function handleDelete() {
    if (!deletePassword) {
      toast.info("Escribe tu contraseña para confirmar.");
      return;
    }
    const ok = await confirm({
      title: "¿Eliminar tu cuenta?",
      message: "Se borrarán de forma permanente tu perfil, tus respuestas y tus rutinas. No se puede deshacer.",
      confirmText: "Sí, eliminar",
      destructive: true,
    });
    if (!ok) return;
    setDeleting(true);
    try {
      await deleteAccount(deletePassword);
      await logout();
      router.replace("/login");
    } catch (error) {
      toast.error(extractErrorMessage(error));
    } finally {
      setDeleting(false);
    }
  }

  const version = Constants.expoConfig?.version ?? "1.0.0";

  return (
    <View style={styles.bg}>
      <GradientHeader style={{ paddingBottom: 18 }}>
        <View style={styles.headRow}>
          <PressableScale style={styles.backBtn} onPress={() => (router.canGoBack() ? router.back() : router.replace("/profile"))}>
            <Text style={styles.backText}>←</Text>
          </PressableScale>
          <View>
            <Text style={styles.title}>Ajustes</Text>
            <Text style={styles.subtitle}>Personaliza la app a tu gusto</Text>
          </View>
        </View>
      </GradientHeader>

      <ScrollView contentContainerStyle={{ paddingBottom: 40 }} keyboardShouldPersistTaps="handled" showsVerticalScrollIndicator={false}>
        {/* ───── Apariencia ───── */}
        <Section title="Apariencia" delay={0}>
          <Text style={styles.label}>Tema</Text>
          <View style={styles.segment}>
            {THEME_OPTIONS.map((opt) => (
              <PressableScale
                key={opt.value}
                style={[styles.segmentItem, theme.preference === opt.value && styles.segmentItemActive]}
                onPress={() => theme.setPreference(opt.value)}
                scaleTo={0.95}
              >
                <Text style={[styles.segmentText, theme.preference === opt.value && styles.segmentTextActive]}>{opt.label}</Text>
              </PressableScale>
            ))}
          </View>

          <Text style={[styles.label, { marginTop: 16 }]}>Color de acento</Text>
          <View style={styles.swatches}>
            {(Object.keys(ACCENTS) as AccentKey[]).map((key) => (
              <PressableScale key={key} onPress={() => theme.setAccent(key)} style={{ alignItems: "center", gap: 6 }} scaleTo={0.9}>
                <View
                  style={[
                    styles.swatch,
                    { backgroundColor: ACCENTS[key].main },
                    theme.accent === key && { borderColor: colors.text, borderWidth: 3 },
                  ]}
                >
                  {theme.accent === key ? <Text style={{ color: "#fff", fontWeight: "700" }}>✓</Text> : null}
                </View>
                <Text style={styles.swatchLabel}>{ACCENTS[key].label}</Text>
              </PressableScale>
            ))}
          </View>

          <View style={styles.preview}>
            <Text style={styles.previewTitle}>Vista previa</Text>
            <GradientButton title="Botón principal" onPress={() => toast.info("¡Así se verá tu app!")} />
          </View>
        </Section>

        {/* ───── Notificaciones ───── */}
        <Section title="Notificaciones" delay={60}>
          <View style={styles.switchRow}>
            <View style={{ flex: 1 }}>
              <Text style={styles.rowTitle}>Recordatorios de entrenamiento</Text>
              <Text style={styles.rowSub}>
                {REMINDERS_SUPPORTED ? "Te avisamos los días y la hora que elijas." : IS_EXPO_GO ? "No disponible en Expo Go: requiere un development build (aquí se guarda tu preferencia)." : "Disponible en la app móvil (aquí se guarda tu preferencia)."}
              </Text>
            </View>
            <Switch
              value={reminders.enabled}
              onValueChange={toggleReminders}
              trackColor={{ false: colors.track, true: colors.primary }}
              thumbColor="#fff"
            />
          </View>

          {reminders.enabled ? (
            <Animated.View entering={FadeInDown.duration(300)} layout={LinearTransition}>
              <Text style={[styles.label, { marginTop: 14 }]}>Días</Text>
              <View style={styles.daysRow}>
                {WEEKDAYS.map((d) => {
                  const active = reminders.weekdays.includes(d.value);
                  return (
                    <PressableScale key={d.value} style={[styles.dayChip, active && styles.dayChipActive]} onPress={() => toggleWeekday(d.value)} scaleTo={0.9}>
                      <Text style={[styles.dayChipText, active && { color: "#fff" }]}>{d.label}</Text>
                    </PressableScale>
                  );
                })}
              </View>

              <Text style={[styles.label, { marginTop: 14 }]}>Hora</Text>
              <View style={styles.timeRow}>
                <PressableScale style={styles.stepBtn} onPress={() => shiftTime(-60)}>
                  <Text style={styles.stepBtnText}>−</Text>
                </PressableScale>
                <Text style={styles.timeText}>{pad(reminders.hour)}</Text>
                <PressableScale style={styles.stepBtn} onPress={() => shiftTime(60)}>
                  <Text style={styles.stepBtnText}>+</Text>
                </PressableScale>
                <Text style={styles.timeText}>:</Text>
                <PressableScale style={styles.stepBtn} onPress={() => shiftTime(-5)}>
                  <Text style={styles.stepBtnText}>−</Text>
                </PressableScale>
                <Text style={styles.timeText}>{pad(reminders.minute)}</Text>
                <PressableScale style={styles.stepBtn} onPress={() => shiftTime(5)}>
                  <Text style={styles.stepBtnText}>+</Text>
                </PressableScale>
              </View>
              <Text style={styles.rowSub}>Horas a la izquierda, minutos (de 5 en 5) a la derecha.</Text>
            </Animated.View>
          ) : null}
        </Section>

        {/* ───── Modo de cuidado ───── */}
        <Section title="Modo de cuidado" delay={90}>
          <Text style={styles.rowSub}>Adapta las sesiones a tu situación: menos series, más descanso, texto grande y avisos de seguridad. No sustituye la valoración de un profesional de la salud.</Text>
          <View style={{ gap: 8, marginTop: 10 }}>
            {(Object.keys(CARE_MODES) as CareModeKey[]).map((key) => {
              const c = CARE_MODES[key];
              const active = careMode === key;
              return (
                <PressableScale key={key} style={[styles.segmentItem, active && styles.segmentItemActive, { flexDirection: "row", alignItems: "center", gap: 10, justifyContent: "flex-start", paddingHorizontal: 12 }]} onPress={() => chooseCareMode(key)} scaleTo={0.97}>
                  <Text style={{ fontSize: 22 }}>{c.icon}</Text>
                  <View style={{ flex: 1 }}>
                    <Text style={[styles.segmentText, active && styles.segmentTextActive, { textAlign: "left" }]}>{c.label}</Text>
                    <Text style={[styles.rowSub, active && { color: "rgba(255,255,255,0.85)" }]}>{c.description}</Text>
                  </View>
                </PressableScale>
              );
            })}
          </View>
        </Section>

        {/* ───── Unidades ───── */}
        <Section title="Unidades" delay={120}>
          <Text style={styles.label}>Peso</Text>
          <View style={styles.segment}>
            {(["kg", "lb"] as const).map((u) => (
              <PressableScale key={u} style={[styles.segmentItem, theme.weightUnit === u && styles.segmentItemActive]} onPress={() => theme.setWeightUnit(u)} scaleTo={0.95}>
                <Text style={[styles.segmentText, theme.weightUnit === u && styles.segmentTextActive]}>{u === "kg" ? "Kilogramos (kg)" : "Libras (lb)"}</Text>
              </PressableScale>
            ))}
          </View>
          <Text style={[styles.label, { marginTop: 14 }]}>Altura</Text>
          <View style={styles.segment}>
            {(["cm", "ft"] as const).map((u) => (
              <PressableScale key={u} style={[styles.segmentItem, theme.heightUnit === u && styles.segmentItemActive]} onPress={() => theme.setHeightUnit(u)} scaleTo={0.95}>
                <Text style={[styles.segmentText, theme.heightUnit === u && styles.segmentTextActive]}>{u === "cm" ? "Centímetros (cm)" : "Pies y pulgadas"}</Text>
              </PressableScale>
            ))}
          </View>
          <Text style={[styles.rowSub, { marginTop: 8 }]}>Solo cambia cómo se muestran tus medidas; los cuestionarios siguen en kg y cm.</Text>
        </Section>

        {/* ───── Cuenta ───── */}
        <Section title="Cuenta" delay={180}>
          <TextField label="Nombre" value={name} onChangeText={(t) => { setName(t); setNameError(null); }} autoCapitalize="words" maxLength={80} error={nameError} />
          <TextField label="País" value={country} onChangeText={setCountry} placeholder="Colombia" maxLength={60} />
          <TextField label="Ciudad" value={city} onChangeText={setCity} placeholder="Bogotá" maxLength={80} />
          <GradientButton title="Guardar cambios" onPress={saveAccount} loading={savingProfile} />

          <View style={styles.divider} />
          <Text style={styles.rowTitle}>Cambiar contraseña</Text>
          <View style={{ height: 10 }} />
          <TextField label="Contraseña actual" passwordToggle value={currentPassword} onChangeText={(t) => { setCurrentPassword(t); setPasswordErrors((e) => ({ ...e, current: undefined })); }} error={passwordErrors.current} />
          <TextField label="Nueva contraseña" passwordToggle value={newPassword} onChangeText={(t) => { setNewPassword(t); setPasswordErrors((e) => ({ ...e, next: undefined })); }} error={passwordErrors.next} />
          <TextField label="Confirmar nueva contraseña" passwordToggle value={confirmPassword} onChangeText={(t) => { setConfirmPassword(t); setPasswordErrors((e) => ({ ...e, confirm: undefined })); }} error={passwordErrors.confirm} />
          <GradientButton title="Actualizar contraseña" variant="ghost" onPress={savePassword} loading={savingPassword} />
        </Section>

        {/* ───── Privacidad y datos ───── */}
        <Section title="Privacidad y datos" delay={240}>
          <Text style={styles.paragraph}>
            Tus datos de salud se usan solo para personalizar tus recomendaciones y no se comparten con terceros. Puedes descargar una copia
            o eliminar tu cuenta cuando quieras.
          </Text>
          <View style={{ height: 12 }} />
          <GradientButton title="📥 Exportar mis datos" variant="ghost" onPress={handleExport} loading={exporting} />

          <PressableScale style={styles.dangerLink} onPress={() => setShowDelete((s) => !s)}>
            <Text style={styles.dangerLinkText}>{showDelete ? "Cancelar" : "Eliminar mi cuenta…"}</Text>
          </PressableScale>

          {showDelete ? (
            <Animated.View entering={FadeInDown.duration(300)} style={styles.dangerBox}>
              <Text style={styles.dangerText}>Esta acción es permanente. Escribe tu contraseña para continuar.</Text>
              <TextField label="Contraseña" passwordToggle value={deletePassword} onChangeText={setDeletePassword} containerStyle={{ marginTop: 10 }} />
              <GradientButton title="Eliminar cuenta definitivamente" variant="danger" onPress={handleDelete} loading={deleting} />
            </Animated.View>
          ) : null}
        </Section>

        {/* ───── Acerca de ───── */}
        <Section title="Acerca de" delay={300}>
          <View style={styles.aboutRow}>
            <Text style={styles.rowTitle}>Sportacus</Text>
            <Text style={styles.rowSub}>Versión {version}</Text>
          </View>
          <Text style={[styles.rowSub, { marginTop: 8 }]}>
            Tu punto de entrada al mundo del ejercicio. Las recomendaciones son orientativas y no sustituyen la consulta con un profesional de la salud.
          </Text>
        </Section>
      </ScrollView>
    </View>
  );
}

function Section({ title, delay, children }: { title: string; delay: number; children: React.ReactNode }) {
  const styles = useThemedStyles(createStyles);
  return (
    <Animated.View entering={FadeInDown.delay(delay).duration(400)} style={styles.section}>
      <Text style={styles.sectionTitle}>{title}</Text>
      <View style={styles.card}>{children}</View>
    </Animated.View>
  );
}

const createStyles = ({ colors, shadow }: Theme) => ({
  bg: { flex: 1, backgroundColor: colors.bg },
  headRow: { flexDirection: "row" as const, alignItems: "center" as const, gap: 14 },
  backBtn: { width: 38, height: 38, borderRadius: 13, backgroundColor: "rgba(255,255,255,0.2)", alignItems: "center" as const, justifyContent: "center" as const },
  backText: { color: "#fff", fontSize: 18 },
  title: { color: "#fff", fontSize: 21, fontWeight: "700" as const },
  subtitle: { color: "rgba(255,255,255,0.8)", fontSize: 12, marginTop: 1 },
  section: { marginHorizontal: 16, marginTop: 20 },
  sectionTitle: { fontSize: 11, color: colors.textFaint, fontWeight: "700" as const, textTransform: "uppercase" as const, letterSpacing: 0.8, marginBottom: 8 },
  card: { backgroundColor: colors.card, borderRadius: 18, padding: 16, ...shadow },
  label: { fontSize: 12, color: colors.textMuted, fontWeight: "600" as const, marginBottom: 8 },
  segment: { flexDirection: "row" as const, backgroundColor: colors.cardAlt, borderRadius: 14, padding: 3 },
  segmentItem: { flex: 1, paddingVertical: 9, borderRadius: 11, alignItems: "center" as const },
  segmentItemActive: { backgroundColor: colors.primary },
  segmentText: { fontSize: 12, fontWeight: "600" as const, color: colors.textMuted },
  segmentTextActive: { color: "#fff" },
  swatches: { flexDirection: "row" as const, gap: 18, flexWrap: "wrap" as const },
  swatch: { width: 46, height: 46, borderRadius: 23, alignItems: "center" as const, justifyContent: "center" as const, borderWidth: 3, borderColor: "transparent" },
  swatchLabel: { fontSize: 11, color: colors.textMuted },
  preview: { marginTop: 16, padding: 14, borderRadius: 16, backgroundColor: colors.cardAlt, gap: 10 },
  previewTitle: { fontSize: 12, color: colors.textMuted, fontWeight: "600" as const },
  switchRow: { flexDirection: "row" as const, alignItems: "center" as const, gap: 12 },
  rowTitle: { fontSize: 14, fontWeight: "700" as const, color: colors.text },
  rowSub: { fontSize: 12, color: colors.textMuted, marginTop: 2, lineHeight: 17 },
  daysRow: { flexDirection: "row" as const, gap: 8 },
  dayChip: { width: 38, height: 38, borderRadius: 19, backgroundColor: colors.cardAlt, alignItems: "center" as const, justifyContent: "center" as const },
  dayChipActive: { backgroundColor: colors.primary },
  dayChipText: { fontSize: 13, fontWeight: "700" as const, color: colors.textMuted },
  timeRow: { flexDirection: "row" as const, alignItems: "center" as const, gap: 18, marginBottom: 6 },
  stepBtn: { width: 40, height: 40, borderRadius: 20, backgroundColor: colors.primarySoft, alignItems: "center" as const, justifyContent: "center" as const },
  stepBtnText: { fontSize: 22, color: colors.primary, fontWeight: "700" as const },
  timeText: { fontSize: 30, fontWeight: "700" as const, color: colors.text, minWidth: 100, textAlign: "center" as const },
  divider: { height: 1, backgroundColor: colors.border, marginVertical: 18 },
  paragraph: { fontSize: 13, color: colors.textMuted, lineHeight: 20 },
  dangerLink: { alignSelf: "center" as const, marginTop: 18, paddingVertical: 6, paddingHorizontal: 10 },
  dangerLinkText: { color: colors.danger, fontWeight: "700" as const, fontSize: 13 },
  dangerBox: { marginTop: 10, padding: 14, borderRadius: 14, backgroundColor: colors.dangerSoft },
  dangerText: { fontSize: 13, color: colors.danger, lineHeight: 18 },
  aboutRow: { flexDirection: "row" as const, justifyContent: "space-between" as const, alignItems: "center" as const },
});
