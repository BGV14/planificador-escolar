import { AlertCircle, Inbox } from "lucide-react";
import { Button } from "./ui/button";
export function ErrorNotice({
  message,
  onReload,
}: {
  message: string;
  onReload?: () => void;
}) {
  return (
    <div
      role="alert"
      className="flex flex-wrap items-start gap-3 rounded-xl border border-red-200 bg-red-50 p-4 text-sm text-red-900"
    >
      <AlertCircle className="shrink-0" size={18} />
      <p className="flex-1">{message}</p>
      {onReload && (
        <Button variant="outline" size="sm" onClick={onReload}>
          Recargar datos
        </Button>
      )}
    </div>
  );
}
export function Empty({ title, detail }: { title: string; detail: string }) {
  return (
    <div className="p-10 text-center">
      <Inbox size={28} className="mx-auto mb-3 text-slate-400" />
      <h3 className="font-semibold">{title}</h3>
      <p className="mt-1 text-sm text-slate-500">{detail}</p>
    </div>
  );
}
export function Loading() {
  return (
    <div
      role="status"
      aria-label="Cargando planificación"
      className="space-y-4 p-6"
    >
      <span className="sr-only">Cargando planificación…</span>
      <div className="h-10 w-1/3 animate-pulse rounded-lg bg-slate-200" />
      <div className="grid grid-cols-3 gap-4">
        {[1, 2, 3].map((i) => (
          <div key={i} className="h-24 animate-pulse rounded-xl bg-slate-200" />
        ))}
      </div>
      <div className="h-96 animate-pulse rounded-xl bg-slate-200" />
    </div>
  );
}
