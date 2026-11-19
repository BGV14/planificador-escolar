import {
  useCallback,
  useEffect,
  useRef,
  useState,
  type FormEvent,
} from "react";
import { api, ApiError, errorMessage } from "../lib/api";
import type { AcademicLevel, PlatformSchool } from "../lib/types";
import { Field, Input, Select, TextField } from "./Fields";
import { ErrorNotice, Loading } from "./Feedback";
import { Button } from "./ui/button";
import { Card } from "./ui/card";

export function PlatformAdministration({
  onAccessLost,
}: {
  onAccessLost: (expired: boolean) => void;
}) {
  const [schools, setSchools] = useState<PlatformSchool[]>([]);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");
  const [level, setLevel] = useState<AcademicLevel>("PRIMARY");
  const [stage, setStage] = useState(1);
  const active = useRef(false);
  const version = useRef(0);
  const current = useCallback(
    (epoch: number) => active.current && epoch === version.current,
    [],
  );
  const rejected = useCallback(
    (cause: unknown) => {
      if (
        cause instanceof ApiError &&
        (cause.status === 401 || cause.status === 403)
      ) {
        setSchools([]);
        setSuccess("");
        onAccessLost(cause.status === 401);
        return;
      }
      setError(
        cause instanceof ApiError && cause.status === 409
          ? "El nombre de usuario ya está registrado. Usa otro; no se creó un colegio ni un administrador duplicado."
          : errorMessage(cause),
      );
    },
    [onAccessLost],
  );
  const reload = useCallback(async () => {
    const epoch = ++version.current;
    setLoading(true);
    setError("");
    try {
      const result = await api.get<PlatformSchool[]>("/platform/schools");
      if (current(epoch)) setSchools(result);
    } catch (cause) {
      if (current(epoch)) {
        setSchools([]);
        rejected(cause);
      }
    } finally {
      if (current(epoch)) setLoading(false);
    }
  }, [current, rejected]);
  useEffect(() => {
    active.current = true;
    void reload();
    return () => {
      active.current = false;
      ++version.current;
    };
  }, [reload]);

  async function submit(
    event: FormEvent<HTMLFormElement>,
    createSchool: boolean,
  ) {
    event.preventDefault();
    const form = event.currentTarget;
    const fields = new FormData(form);
    const value = (name: string) => String(fields.get(name) ?? "");
    const path = createSchool
      ? "/platform/schools"
      : `/platform/schools/${encodeURIComponent(value("schoolId"))}/administrators`;
    const payload = createSchool
      ? {
          name: value("name"),
          year: Number(value("year")),
          level: value("level"),
          stageValue: Number(value("stageValue")),
          section: value("section"),
          adminUsername: value("username"),
          adminPassword: value("password"),
        }
      : { username: value("username"), password: value("password") };
    const epoch = ++version.current;
    setBusy(true);
    setError("");
    setSuccess("");
    try {
      await api.mutate(path, "POST", payload);
      if (!current(epoch)) return;
      form.reset();
      if (createSchool) {
        setLevel("PRIMARY");
        setStage(1);
      }
      setSuccess(
        createSchool
          ? "Colegio creado sin contenido precargado."
          : "Administrador creado.",
      );
      setBusy(false);
      await reload();
    } catch (cause) {
      if (current(epoch)) rejected(cause);
    } finally {
      if (current(epoch)) setBusy(false);
    }
  }

  return (
    <section aria-label="Administración global" className="space-y-6">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h1 className="page-title">Administración de colegios</h1>
          <p className="mt-2 text-sm text-slate-600">
            Gestiona colegios y sus administradores. Tu colegio y planificación
            no cambian al usar esta pestaña.
          </p>
        </div>
        <Button
          variant="outline"
          disabled={busy || loading}
          onClick={() => void reload()}
        >
          Actualizar colegios
        </Button>
      </div>
      {error && <ErrorNotice message={error} />}
      {success && (
        <p role="status" className="rounded-lg bg-teal-50 p-3 text-teal-900">
          {success}
        </p>
      )}
      {loading ? (
        <Loading />
      ) : (
        <Card className="p-5">
          <h2 className="mb-3 text-lg font-semibold">Colegios registrados</h2>
          {schools.length === 0 ? (
            <p>No hay colegios registrados.</p>
          ) : (
            <ul className="space-y-3">
              {schools.map((school) => (
                <li key={school.id} className="rounded-lg border p-3">
                  <h3 className="font-semibold">{school.name}</h3>
                  <p>Año: {school.year}</p>
                  <p>
                    Administradores:{" "}
                    {school.administrators
                      .map((admin) => admin.username)
                      .join(", ") || "Sin administradores"}
                  </p>
                </li>
              ))}
            </ul>
          )}
        </Card>
      )}
      <div className="grid gap-6 lg:grid-cols-2">
        <Card className="p-5">
          <h2 className="mb-2 text-lg font-semibold">Crear colegio</h2>
          <p className="mb-4 text-sm text-slate-600">
            El colegio empieza vacío. Después podrás importar áreas, temas y
            planificación.
          </p>
          <form onSubmit={(event) => void submit(event, true)}>
            <fieldset disabled={busy || loading} className="space-y-4">
              <TextField
                label="Nombre del colegio"
                name="name"
                maxLength={200}
              />
              <TextField
                label="Año"
                name="year"
                type="number"
                min={1900}
                max={2200}
                value={new Date().getFullYear()}
              />
              <Field label="Nivel">
                {(id) => (
                  <Select
                    id={id}
                    name="level"
                    value={level}
                    onChange={(event) => {
                      const next = event.target.value as AcademicLevel;
                      setLevel(next);
                      setStage(next === "INITIAL" ? 0 : 1);
                    }}
                  >
                    <option value="INITIAL">Inicial</option>
                    <option value="PRIMARY">Primaria</option>
                    <option value="SECONDARY">Secundaria</option>
                  </Select>
                )}
              </Field>
              <Field label={level === "INITIAL" ? "Edad" : "Grado"}>
                {(id) => (
                  <Input
                    id={id}
                    name="stageValue"
                    type="number"
                    required
                    min={level === "INITIAL" ? 0 : 1}
                    max={level === "PRIMARY" ? 6 : 5}
                    value={stage}
                    onChange={(event) => setStage(Number(event.target.value))}
                  />
                )}
              </Field>
              <TextField
                label="Sección"
                name="section"
                value="UNICA"
                maxLength={16}
              />
              <TextField
                label="Usuario del administrador"
                name="username"
                maxLength={100}
              />
              <Field
                label="Contraseña del administrador"
                hint="Mínimo 12 caracteres y máximo 72 bytes UTF-8. No se guarda en este navegador."
              >
                {(id) => (
                  <Input
                    id={id}
                    name="password"
                    type="password"
                    autoComplete="new-password"
                    minLength={12}
                    maxLength={72}
                    required
                  />
                )}
              </Field>
              <Button type="submit">Crear colegio vacío</Button>
            </fieldset>
          </form>
        </Card>
        <Card className="p-5">
          <h2 className="mb-2 text-lg font-semibold">
            Designar otro administrador
          </h2>
          <p className="mb-4 text-sm text-slate-600">
            Crea una cuenta nueva para un colegio existente. No concede acceso
            global.
          </p>
          <form onSubmit={(event) => void submit(event, false)}>
            <fieldset
              disabled={busy || loading || schools.length === 0}
              className="space-y-4"
            >
              <Field label="Colegio de destino">
                {(id) => (
                  <Select id={id} name="schoolId" defaultValue="" required>
                    <option value="" disabled>
                      Selecciona un colegio
                    </option>
                    {schools.map((school) => (
                      <option key={school.id} value={school.id}>
                        {school.name}
                      </option>
                    ))}
                  </Select>
                )}
              </Field>
              <TextField
                label="Usuario del nuevo administrador"
                name="username"
                maxLength={100}
              />
              <Field
                label="Contraseña del nuevo administrador"
                hint="Mínimo 12 caracteres y máximo 72 bytes UTF-8."
              >
                {(id) => (
                  <Input
                    id={id}
                    name="password"
                    type="password"
                    autoComplete="new-password"
                    minLength={12}
                    maxLength={72}
                    required
                  />
                )}
              </Field>
              <Button type="submit">Crear administrador</Button>
            </fieldset>
          </form>
        </Card>
      </div>
    </section>
  );
}
