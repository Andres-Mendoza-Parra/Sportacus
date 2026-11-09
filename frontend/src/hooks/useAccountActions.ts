import { router } from "expo-router";
import { useState } from "react";
import { Platform, Share } from "react-native";

import { useConfirm, useToast } from "../components/ui/Feedback";
import { useAuth } from "../context/AuthContext";
import { extractErrorMessage } from "../services/api";
import { deleteAccount, exportProfileData } from "../services/profileService";

interface WebDownload {
  Blob: new (parts: string[], options: { type: string }) => unknown;
  URL: { createObjectURL: (b: unknown) => string; revokeObjectURL: (u: string) => void };
  document: { createElement: (t: string) => { href: string; download: string; click: () => void } };
}

/** Acciones de cuenta: exportar datos (JSON), borrar la cuenta y cerrar sesión. */
export function useAccountActions() {
  const toast = useToast();
  const confirm = useConfirm();
  const { logout } = useAuth();
  const [exporting, setExporting] = useState(false);
  const [deleting, setDeleting] = useState(false);

  async function exportData() {
    setExporting(true);
    try {
      const json = JSON.stringify(await exportProfileData(), null, 2);
      if (Platform.OS === "web") {
        const g = globalThis as unknown as WebDownload;
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

  async function removeAccount() {
    const ok = await confirm({
      title: "¿Eliminar tu cuenta?",
      message: "Se borrarán de forma permanente tu perfil, tu chequeo de salud, tus mundos, planes y sesiones. No se puede deshacer.",
      confirmText: "Sí, eliminar",
      destructive: true,
    });
    if (!ok) return;
    setDeleting(true);
    try {
      await deleteAccount();
      await logout();
      router.replace("/login");
    } catch (error) {
      toast.error(extractErrorMessage(error));
    } finally {
      setDeleting(false);
    }
  }

  async function signOut() {
    const ok = await confirm({ title: "¿Cerrar sesión?", confirmText: "Cerrar sesión" });
    if (!ok) return;
    await logout();
    router.replace("/login");
  }

  return { exportData, exporting, removeAccount, deleting, signOut };
}
