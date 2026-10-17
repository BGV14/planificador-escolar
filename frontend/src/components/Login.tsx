import { useState, type FormEvent } from "react";
import { BookOpen, ArrowRight, ShieldCheck } from "lucide-react";
import { api, errorMessage } from "../lib/api";
import type { User } from "../lib/types";
import { Button } from "./ui/button";
import { Card } from "./ui/card";
import { TextField } from "./Fields";
import { ErrorNotice } from "./Feedback";
export function Login({ onLogin }: { onLogin: (u: User) => void }) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  async function submit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    setBusy(true);
    setError("");
    const form = e.currentTarget;
    const f = new FormData(form);
    try {
      const user = await api.login(
        String(f.get("username")),
        String(f.get("password")),
      );
      form.reset();
      onLogin(user);
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  }
  return (
    <main className="login-shell">
      <div className="mb-8 flex items-center justify-center gap-3">
        <span className="brand-icon">
          <BookOpen size={25} />
        </span>
        <span className="text-3xl font-bold tracking-tight">
          aula<span className="text-teal-600">.</span>
        </span>
      </div>
      <Card className="mx-auto w-full max-w-md p-8">
        <p className="eyebrow">PLANIFICACIÓN COMPARTIDA</p>
        <h1 className="mt-3 text-2xl font-bold tracking-tight">
          Tu próxima clase empieza aquí.
        </h1>
        <p className="mb-7 mt-3 text-sm leading-relaxed text-slate-500">
          Ingresa con la cuenta que te asignó tu escuela para organizar temas,
          fechas y avances junto a tu equipo.
        </p>
        <form className="space-y-5" onSubmit={submit}>
          {error && <ErrorNotice message={error} />}
          <TextField label="Usuario" name="username" maxLength={100} />
          <TextField
            label="Contraseña"
            name="password"
            type="password"
            maxLength={100}
          />
          <Button className="w-full" disabled={busy}>
            {busy ? "Ingresando…" : "Ingresar a mi escuela"}
            <ArrowRight size={17} />
          </Button>
        </form>
        <p className="mt-6 border-t pt-5 text-xs leading-relaxed text-slate-500">
          ¿No tienes una cuenta? Solicítala a la administración de tu escuela.
          No hay registro público.
        </p>
      </Card>
      <p className="mt-6 flex items-center justify-center gap-2 text-xs text-slate-500">
        <ShieldCheck size={14} />
        Sesión privada · sin contraseñas guardadas en el navegador
      </p>
    </main>
  );
}
