import { Link, router } from "expo-router";
import { useEffect, useState } from "react";
import { Pressable, Text } from "react-native";

import AuthShell from "../components/AuthShell";
import GradientButton from "../components/ui/GradientButton";
import NoticeBox from "../components/ui/NoticeBox";
import TextField from "../components/ui/TextField";
import { useAuth } from "../context/AuthContext";
import { errorStatus, extractErrorMessage, retryAfterSeconds } from "../services/api";
import { usePendingCount } from "../services/pendingSessions";
import { useTheme } from "../theme";

const LOCK_MINUTES = 15;

function formatWait(seconds: number): string {
  const m = Math.floor(seconds / 60);
  const s = seconds % 60;
  return `${m}:${String(s).padStart(2, "0")}`;
}

export default function LoginScreen() {
  const { login, sessionExpired } = useAuth();
  const { colors } = useTheme();
  const pending = usePendingCount();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [errors, setErrors] = useState<{ email?: string; password?: string }>({});
  const [isSubmitting, setIsSubmitting] = useState(false);
  /** Mensaje del servidor tras un intento fallido ("Te quedan N intentos"). */
  const [failure, setFailure] = useState<string | null>(null);
  /** Hora (ms) hasta la que el inicio de sesión está bloqueado (429). */
  const [lockedUntil, setLockedUntil] = useState<number | null>(null);
  const [now, setNow] = useState(() => Date.now());

  useEffect(() => {
    if (!lockedUntil) return;
    const timer = setInterval(() => {
      const t = Date.now();
      setNow(t);
      if (t >= lockedUntil) {
        setLockedUntil(null);
        setFailure(null);
      }
    }, 1000);
    return () => clearInterval(timer);
  }, [lockedUntil]);

  const remaining = lockedUntil ? Math.max(0, Math.ceil((lockedUntil - now) / 1000)) : 0;
  const locked = remaining > 0;

  async function handleSubmit() {
    if (locked) return;
    const next: typeof errors = {};
    if (!email.trim()) next.email = "Ingresa tu correo";
    else if (!/^\S+@\S+\.\S+$/.test(email.trim())) next.email = "El correo no parece válido";
    if (!password) next.password = "Ingresa tu contraseña";
    setErrors(next);
    if (Object.keys(next).length > 0) return;

    setIsSubmitting(true);
    setFailure(null);
    try {
      await login(email.trim(), password);
      router.replace("/home");
    } catch (error) {
      const status = errorStatus(error);
      if (status === 429) {
        const wait = retryAfterSeconds(error) ?? LOCK_MINUTES * 60;
        const t = Date.now();
        setNow(t);
        setLockedUntil(t + wait * 1000);
      }
      setFailure(extractErrorMessage(error));
      if (status === 401) setPassword("");
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <AuthShell subtitle="Tu punto de entrada al mundo del ejercicio">
      <Text style={{ fontSize: 18, fontWeight: "700", color: colors.primaryDark, marginBottom: 18 }}>Iniciar sesión</Text>

      {sessionExpired ? (
        <NoticeBox
          tone="info"
          icon="🔑"
          title="Tu sesión expiró"
          body={
            pending > 0
              ? "Vuelve a entrar: guardamos tu entrenamiento y lo enviaremos apenas inicies sesión."
              : "Por seguridad, vuelve a entrar para continuar donde ibas."
          }
          style={{ marginBottom: 14 }}
        />
      ) : pending > 0 ? (
        <NoticeBox
          tone="info"
          icon="💾"
          body="Tienes entrenamientos guardados en este dispositivo. Los enviaremos cuando inicies sesión."
          style={{ marginBottom: 14 }}
        />
      ) : null}

      <TextField
        label="Correo electrónico"
        placeholder="tu@correo.com"
        autoCapitalize="none"
        keyboardType="email-address"
        value={email}
        onChangeText={(v) => {
          setEmail(v);
          if (errors.email) setErrors((e) => ({ ...e, email: undefined }));
        }}
        error={errors.email}
      />
      <TextField
        label="Contraseña"
        placeholder="••••••••"
        passwordToggle
        value={password}
        onChangeText={(v) => {
          setPassword(v);
          if (errors.password) setErrors((e) => ({ ...e, password: undefined }));
        }}
        onSubmitEditing={handleSubmit}
        error={errors.password}
      />

      {locked ? (
        <NoticeBox
          tone="warn"
          icon="⏳"
          title="Inicio de sesión en pausa"
          body={`${failure ?? "Demasiados intentos fallidos."} Podrás intentarlo de nuevo en ${formatWait(remaining)}.`}
          style={{ marginBottom: 12 }}
        />
      ) : failure ? (
        <NoticeBox tone="danger" body={failure} style={{ marginBottom: 12 }} />
      ) : null}

      <GradientButton
        title={locked ? `Espera ${formatWait(remaining)}` : "Entrar"}
        onPress={handleSubmit}
        loading={isSubmitting}
        disabled={locked}
        style={{ marginTop: 6 }}
      />

      <Link href="/register" asChild>
        <Pressable style={{ marginTop: 10, minHeight: 44, alignItems: "center", justifyContent: "center" }}>
          <Text style={{ color: colors.textMuted, fontSize: 13 }}>
            ¿No tienes cuenta? <Text style={{ color: colors.primary, fontWeight: "700" }}>Regístrate</Text>
          </Text>
        </Pressable>
      </Link>
    </AuthShell>
  );
}
