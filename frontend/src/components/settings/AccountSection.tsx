import React, { useState } from "react";
import { Text, View } from "react-native";

import { useAuth } from "../../context/AuthContext";
import { extractErrorMessage } from "../../services/api";
import { changePassword, updateProfile } from "../../services/profileService";
import { useThemedStyles } from "../../theme";
import { useToast } from "../ui/Feedback";
import GradientButton from "../ui/GradientButton";
import TextField from "../ui/TextField";
import { SettingsSection, createStyles } from "./SettingsSection";

interface PasswordErrors {
  current?: string;
  next?: string;
  confirm?: string;
}

/** Nombre visible (PATCH /profile) y cambio de contraseña. */
export default function AccountSection({ delay }: { delay: number }) {
  const s = useThemedStyles(createStyles);
  const toast = useToast();
  const { user, updateUser } = useAuth();
  const [name, setName] = useState(user?.name ?? "");
  const [nameError, setNameError] = useState<string | null>(null);
  const [savingName, setSavingName] = useState(false);
  const [currentPassword, setCurrentPassword] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [errors, setErrors] = useState<PasswordErrors>({});
  const [savingPassword, setSavingPassword] = useState(false);


  async function saveName() {
    if (!name.trim()) {
      setNameError("El nombre no puede estar vacío");
      return;
    }
    setNameError(null);
    setSavingName(true);
    try {
      const updated = await updateProfile({ name: name.trim() });
      await updateUser({ name: updated.name || name.trim() });
      toast.success("Nombre actualizado");
    } catch (error) {
      toast.error(extractErrorMessage(error));
    } finally {
      setSavingName(false);
    }
  }

  async function savePassword() {
    const next: PasswordErrors = {};
    if (!currentPassword) next.current = "Ingresa tu contraseña actual";
    if (newPassword.length < 6) next.next = "Debe tener al menos 6 caracteres";
    if (confirmPassword !== newPassword) next.confirm = "Las contraseñas no coinciden";
    setErrors(next);
    if (Object.keys(next).length > 0) return;
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

  return (
    <SettingsSection title="Cuenta" delay={delay}>
      <TextField
        label="Nombre"
        value={name}
        onChangeText={(t) => {
          setName(t);
          setNameError(null);
        }}
        autoCapitalize="words"
        maxLength={80}
        error={nameError}
      />
      <GradientButton title="Guardar nombre" onPress={saveName} loading={savingName} />
      <Text style={[s.rowSub, { marginTop: 8 }]}>Tu peso, estatura y disponibilidad se editan en Cuenta → Editar perfil.</Text>

      <View style={s.divider} />
      <Text style={s.rowTitle}>Cambiar contraseña</Text>
      <View style={{ height: 10 }} />
      <TextField
        label="Contraseña actual"
        passwordToggle
        value={currentPassword}
        onChangeText={(t) => {
          setCurrentPassword(t);
          setErrors((e) => ({ ...e, current: undefined }));
        }}
        error={errors.current}
      />
      <TextField
        label="Nueva contraseña"
        passwordToggle
        value={newPassword}
        onChangeText={(t) => {
          setNewPassword(t);
          setErrors((e) => ({ ...e, next: undefined }));
        }}
        error={errors.next}
      />
      <TextField
        label="Confirmar nueva contraseña"
        passwordToggle
        value={confirmPassword}
        onChangeText={(t) => {
          setConfirmPassword(t);
          setErrors((e) => ({ ...e, confirm: undefined }));
        }}
        error={errors.confirm}
      />
      <GradientButton title="Actualizar contraseña" variant="ghost" onPress={savePassword} loading={savingPassword} />
    </SettingsSection>
  );
}
