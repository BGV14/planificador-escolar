import { SearchX, RotateCcw } from "lucide-react";
import { Button } from "./ui/button";

export function EmptyState({
  title = "Sin temas encontrados",
  description = "No hay temas que coincidan con los filtros seleccionados.",
  onReset,
  resetLabel = "Restablecer filtros",
}: {
  title?: string;
  description?: string;
  onReset?: () => void;
  resetLabel?: string;
}) {
  return (
    <div
      role="status"
      aria-live="polite"
      className="flex flex-col items-center justify-center p-10 text-center rounded-xl border border-dashed border-slate-200 bg-slate-50/50 my-6"
    >
      <div className="flex h-12 w-12 items-center justify-center rounded-full bg-slate-100 mb-3 text-slate-500">
        <SearchX size={24} aria-hidden="true" />
      </div>
      <h3 className="font-semibold text-slate-800 text-base">{title}</h3>
      <p className="mt-1 text-sm text-slate-500 max-w-sm">{description}</p>
      {onReset && (
        <Button
          variant="outline"
          size="sm"
          className="mt-4 gap-2"
          onClick={onReset}
        >
          <RotateCcw size={14} aria-hidden="true" />
          {resetLabel}
        </Button>
      )}
    </div>
  );
}

