import { useEffect, useRef, useState } from "react";
import { FileJson, Upload, ShieldCheck, History } from "lucide-react";
import { api, errorMessage, contextPath } from "../lib/api";
import { previewTotals } from "../lib/planning";
import type { ImportPreview, PlannerData } from "../lib/types";
import { Card } from "./ui/card";
import { Button } from "./ui/button";
import { Input, Textarea } from "./ui/input";
import { ErrorNotice, Empty } from "./Feedback";
export function Imports({
  data,
  onSaved,
}: {
  data: PlannerData;
  onSaved: () => Promise<void>;
}) {
  const active = useRef(true);
  useEffect(() => {
    active.current = true;
    return () => {
      active.current = false;
    };
  }, []);
  const contextId = useRef(data.plan.id).current;
  const request = useRef(0);
  const [json, setJson] = useState("");
  const [preview, setPreview] = useState<ImportPreview | null>(null);
  const [accepted, setAccepted] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");
  function change(text: string) {
    ++request.current;
    setJson(text);
    setPreview(null);
    setAccepted(false);
    setError("");
    setSuccess("");
  }
  async function file(file?: File) {
    if (!file) return;
    if (file.size > 8000000) {
      setError(
        "El archivo supera el tamaño permitido. El contenido JSON admite hasta 2 000 000 de caracteres.",
      );
      return;
    }
    const text = await file.text();
    if (active.current) change(text);
  }
  async function check() {
    const attempt = ++request.current;
    setBusy(true);
    setError("");
    setSuccess("");
    setPreview(null);
    setAccepted(false);
    try {
      const result = await api.mutate<ImportPreview>(
        contextPath("/imports/preview", contextId),
        "POST",
        { json },
      );
      if (active.current && attempt === request.current) setPreview(result);
    } catch (e) {
      if (active.current) setError(errorMessage(e));
    } finally {
      if (active.current) setBusy(false);
    }
  }
  async function confirm() {
    if (!preview || !accepted) return;
    setBusy(true);
    setError("");
    try {
      await api.mutate(contextPath("/imports/confirm", contextId), "POST", {
        json,
        revision: preview.revision,
        acknowledgeIncomingWins: true,
      });
      if (!active.current) return;
      setPreview(null);
      setAccepted(false);
      setJson("");
      setSuccess(
        "Importación confirmada. Los datos omitidos se conservaron y el historial fue actualizado.",
      );
      await onSaved();
    } catch (e) {
      if (active.current) setError(errorMessage(e));
      setPreview(null);
      setAccepted(false);
    } finally {
      if (active.current) setBusy(false);
    }
  }
  if (data.plan.level !== "PRIMARY" || data.plan.stageValue !== 6)
    return (
      <Card className="p-6">
        <h2 className="text-lg font-bold">
          Importación heredada no compatible
        </h2>
        <p className="mt-2 text-sm text-slate-500">
          {data.plan.displayLabel}: los respaldos heredados solo identifican
          sexto de Primaria. Selecciona un plan compatible; los temas
          personalizados se pueden editar en todos los planes.
        </p>
      </Card>
    );
  const totals = preview ? previewTotals(preview.result.preview) : null;
  return (
    <div className="grid gap-6 xl:grid-cols-[1.5fr_1fr]">
      <Card className="p-6">
        <div className="mb-5 flex gap-3">
          <span className="rounded-xl bg-indigo-50 p-3 text-indigo-600">
            <FileJson size={23} />
          </span>
          <div>
            <h2 className="text-lg font-bold">Importar un respaldo JSON</h2>
            <p className="mt-1 text-sm text-slate-500">
              Combina campos sin reemplazar todo el plan.
            </p>
          </div>
        </div>
        <div className="space-y-4">
          {error && <ErrorNotice message={error} />}
          <label className="block rounded-xl border-2 border-dashed border-slate-200 p-5">
            <span className="mb-3 block text-sm font-semibold">
              Seleccionar archivo .json
            </span>
            <Input
              type="file"
              accept=".json,application/json"
              disabled={busy}
              onChange={(e) => void file(e.target.files?.[0])}
            />
            <span className="mt-2 block text-xs text-slate-500">
              El archivo se procesa para esta operación; no se almacena en el
              navegador.
            </span>
          </label>
          <label className="block text-sm font-semibold">
            O pega el contenido
            <Textarea
              className="mt-2 min-h-44 font-mono text-xs"
              aria-label="Contenido JSON del respaldo"
              value={json}
              maxLength={2000000}
              onChange={(e) => change(e.target.value)}
              disabled={busy}
              placeholder={'{"version":2,"year":2026,"grade":6,"state":{…}}'}
            />
          </label>
          <Button onClick={check} disabled={busy || !json.trim()}>
            <Upload size={16} />
            {busy ? "Procesando…" : "Generar vista previa"}
          </Button>
          {success && (
            <p
              role="status"
              className="rounded-xl bg-teal-50 p-4 text-sm text-teal-900"
            >
              {success}
            </p>
          )}
          {preview && totals && (
            <section className="space-y-4 rounded-xl border border-teal-200 bg-teal-50/50 p-5">
              <h3 className="flex items-center gap-2 font-semibold">
                <ShieldCheck size={18} />
                Vista previa · no guardada
              </h3>
              <div className="grid grid-cols-3 gap-3 text-center">
                {[
                  [preview.result.preview.affectedTopics, "Temas"],
                  [totals.added, "Campos nuevos"],
                  [totals.updated, "Reemplazos"],
                  [totals.unchanged, "Sin cambios"],
                  [totals.explicitEmpty, "Vacíos explícitos"],
                  [totals.incoming, "Campos entrantes"],
                ].map(([n, l]) => (
                  <div key={l} className="rounded-lg bg-white p-3">
                    <strong className="block text-xl">{n}</strong>
                    <span className="text-[11px] text-slate-500">{l}</span>
                  </div>
                ))}
              </div>
              <details>
                <summary className="cursor-pointer text-sm font-semibold">
                  Detalle por campo
                </summary>
                <div className="table-scroll">
                  <table>
                    <thead>
                      <tr>
                        <th>Campo</th>
                        <th>Nuevos</th>
                        <th>Actualizados</th>
                        <th>Iguales</th>
                        <th>Vacíos</th>
                      </tr>
                    </thead>
                    <tbody>
                      {Object.entries(preview.result.preview.fields).map(
                        ([key, v]) => (
                          <tr key={key}>
                            <td>{key}</td>
                            <td>{v.added}</td>
                            <td>{v.updated}</td>
                            <td>{v.unchanged}</td>
                            <td>{v.explicitEmpty}</td>
                          </tr>
                        ),
                      )}
                    </tbody>
                  </table>
                </div>
              </details>
              {preview.result.warnings.length > 0 && (
                <div className="rounded-lg bg-amber-50 p-3 text-sm text-amber-900">
                  <strong>
                    Advertencias ({preview.result.warnings.length})
                  </strong>
                  <ul className="mt-2 space-y-1">
                    {preview.result.warnings.map((w, i) => (
                      <li key={i}>
                        {w.code} · {w.topicId}: {w.message}
                      </li>
                    ))}
                  </ul>
                </div>
              )}
              <p className="text-sm leading-relaxed">
                Los campos entrantes reemplazan los campos existentes del mismo
                tema, incluidos valores vacíos. Los campos omitidos se
                conservan. Las fechas exactas no se reprograman.
              </p>
              <label className="flex gap-3 text-sm font-semibold">
                <input
                  type="checkbox"
                  checked={accepted}
                  onChange={(e) => setAccepted(e.target.checked)}
                  disabled={busy}
                  className="mt-1"
                />
                <span>
                  Confirmo que los valores entrantes tienen prioridad y acepto
                  estos reemplazos.
                </span>
              </label>
              <Button onClick={confirm} disabled={!accepted || busy}>
                Confirmar importación
              </Button>
            </section>
          )}
        </div>
      </Card>
      <Card className="self-start">
        <div className="flex items-center gap-2 border-b p-5 font-semibold">
          <History size={18} />
          Historial de importaciones
        </div>
        {!data.history.length ? (
          <Empty
            title="Todavía no hay importaciones"
            detail="Aquí aparecerán solo las importaciones confirmadas."
          />
        ) : (
          <ol className="divide-y">
            {[...data.history]
              .sort((a, b) => b.revision - a.revision)
              .map((h) => (
                <li key={h.id} className="p-5">
                  <p className="text-sm font-semibold">
                    {h.affectedTopics} temas · revisión {h.revision}
                  </p>
                  <p className="mt-1 text-xs text-slate-500">
                    {new Date(h.importedAt).toLocaleString("es-PE")} ·{" "}
                    {data.teachers.find((t) => t.id === h.userId)?.username ||
                      "Docente de la escuela"}
                  </p>
                </li>
              ))}
          </ol>
        )}
        <p className="border-t p-5 text-xs leading-relaxed text-slate-500">
          El historial no almacena el archivo privado ni el texto del respaldo.
        </p>
      </Card>
    </div>
  );
}
