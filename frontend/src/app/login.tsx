import { Link, router } from "expo-router";
import { useState } from "react";
import { Pressable, Text } from "react-native";

import AuthShell from "../components/AuthShell";
import GradientButton from "../components/ui/GradientButton";
import { useToast } from "../components/ui/Feedback";
import TextField from "../components/ui/TextField";
import { useAuth } from "../context/AuthContext";
import { extractErrorMessage } from "../services/api";
import { useTheme } from "../theme";

export default function LoginScreen() {
  const { login } = useAuth();
  const { colors } = useTheme();
  const toast = useToast();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [errors, setErrors] = useState<{ email?: string; password?: string }>({});
  const [isSubmitting, setIsSubmitting] = useState(false);

  async function handleSubmit() {
    const next: typeof errors = {};
    if (!email.trim()) next.email = "Ingresa tu correo";
    else if (!/^\S+@\S+\.\S+$/.test(email.trim())) next.email = "El correo no parece válido";
    if (!password) next.password = "Ingresa tu contraseña";
    setErrors(next);
    if (Object.keys(next).length > 0) return;

    setIsSubmitting(true);
    try {
      await login(email.trim(), password);
      router.replace("/home");
    } catch (error) {
      toast.error(extractErrorMessage(error));
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <AuthShell subtitle="Tu punto de entrada al mundo del ejercicio">
      <Text style={{ fontSize: 18, fontWeight: "700", color: colors.primaryDark, marginBottom: 18 }}>Iniciar sesión</Text>

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

      <GradientButton title="Entrar" onPress={handleSubmit} loading={isSubmitting} style={{ marginTop: 6 }} />

      <Link href="/register" asChild>
        <Pressable style={{ marginTop: 18, alignItems: "center" }}>
          <Text style={{ color: colors.textMuted, fontSize: 13 }}>
            ¿No tienes cuenta? <Text style={{ color: colors.primary, fontWeight: "700" }}>Regístrate</Text>
          </Text>
        </Pressable>
      </Link>
    </AuthShell>
  );
}
