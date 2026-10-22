import { useCallback, useEffect, useRef, useState } from "react";
import {
  BookOpen,
  CalendarDays,
  Layers3,
  Settings2,
  Upload,
  LogOut,
  ChevronLeft,
  ChevronRight,
  Plus,
  Download,
  RefreshCw,
  CheckCheck,
  CalendarClock,
  TriangleAlert,
  Menu,
  FileJson,
} from "lucide-react";
import { api, ApiError, errorMessage, contextPath } from "./lib/api";
import { canEdit, months, monthConflicts } from "./lib/planning";
import type {
  User,
  PlannerData,
  Session,
  Topic,
  AcademicContext,
} from "./lib/types";
import { Button } from "./components/ui/button";
import { Card } from "./components/ui/card";
import { Select } from "./components/ui/input";
import { Login } from "./components/Login";
import { Calendar } from "./components/Calendar";
import { Topics } from "./components/Topics";
import { Imports } from "./components/Imports";
import { Settings } from "./components/Settings";
import { EntityEditor, type Editor } from "./components/EntityEditor";
import { ErrorNotice, Loading } from "./components/Feedback";
import { AcademicContexts } from "./components/AcademicContexts";
import { AnnualCoverageSummary } from "./components/AnnualCoverageSummary";
import { AnnualCoverageDetails } from "./components/AnnualCoverageDetails";
import { buildAnnualCoverage } from "./lib/coverage";
type View = "calendar" | "annual" | "topics" | "imports" | "settings";
const navigation = [
  { id: "calendar", label: "Mi planificación", icon: CalendarDays },
  { id: "annual", label: "Avance anual", icon: CheckCheck },
  { id: "topics", label: "Banco de temas", icon: Layers3 },
  { id: "imports", label: "Importaciones", icon: Upload },
  { id: "settings", label: "Escuela y horarios", icon: Settings2 },
] as const;
export default function App() {
  const [user, setUser] = useState<User | null>(null);
  const [checking, setChecking] = useState(true);
  const [data, setData] = useState<PlannerData | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [view, setView] = useState<View>("calendar");
  const [areaId, setAreaId] = useState("");
  const [month, setMonth] = useState(new Date().getMonth() + 1);
  const [editor, setEditor] = useState<Editor | null>(null);
  const [mobile, setMobile] = useState(false);
  const [downloading, setDownloading] = useState(false);
  const loadEpoch = useRef(0);
  const [contexts, setContexts] = useState<AcademicContext[]>([]);
  const [contextId, setContextId] = useState("");
  const identity = useRef("");
  const selectionEpoch = useRef(0);
  const context = contexts.find((c) => c.id === contextId);
  const renderSelection = selectionEpoch.current;
  function selectContext(id: string) {
    if (id === contextId) return;
    ++selectionEpoch.current;
    ++loadEpoch.current;
    identity.current = user ? `${user.id}:${id}` : "";
    setContextId(id);
    setData(null);
    setEditor(null);
    setAreaId("");
    setError("");
    setDownloading(false);
    setLoading(false);
  }
  useEffect(() => {
    if (!user) return;
    let active = true;
    setContexts([]);
    setContextId("");
    identity.current = "";
    api
      .get<AcademicContext[]>("/contexts")
      .then((items) => {
        if (!active) return;
        setContexts(items);
        const first = items.find((c) => c.legacyDefault) ?? items[0];
        if (first) {
          identity.current = `${user.id}:${first.id}`;
          setContextId(first.id);
        }
      })
      .catch((e) => {
        if (active) setError(errorMessage(e));
      });
    return () => {
      active = false;
      identity.current = "";
      ++loadEpoch.current;
    };
  }, [user]);
  useEffect(() => {
    let active = true;
    api
      .get<User>("/me")
      .then((u) => {
        if (active) setUser(u);
      })
      .catch((e) => {
        if (active && !(e instanceof ApiError && e.status === 401))
          setError(errorMessage(e));
      })
      .finally(() => {
        if (active) setChecking(false);
      });
    return () => {
      active = false;
    };
  }, []);
  const reload = useCallback(async () => {
    if (!user || !contextId || identity.current !== `${user.id}:${contextId}`)
      return;
    const epoch = ++loadEpoch.current;
    setLoading(true);
    setError("");
    try {
      const [
        areas,
        topics,
        sessions,
        schedules,
        blocks,
        plan,
        history,
        teachers,
      ] = await Promise.all([
        api.get<PlannerData["areas"]>(contextPath("/areas", contextId)),
        api.get<PlannerData["topics"]>(contextPath("/topics", contextId)),
        api.get<PlannerData["sessions"]>(contextPath("/sessions", contextId)),
        api.get<PlannerData["schedules"]>(contextPath("/schedules", contextId)),
        api.get<PlannerData["blocks"]>(contextPath("/blocks", contextId)),
        api.get<PlannerData["plan"]>(contextPath("/plan", contextId)),
        api.get<PlannerData["history"]>(contextPath("/imports", contextId)),
        user.role === "ADMIN"
          ? api.get<PlannerData["teachers"]>("/users")
          : Promise.resolve([]),
      ]);
      if (epoch !== loadEpoch.current) return;
      setData({
        areas,
        topics,
        sessions,
        schedules,
        blocks,
        plan,
        history,
        teachers,
      });
      setAreaId((current) =>
        areas.some((a) => a.id === current) ? current : "",
      );
    } catch (e) {
      if (epoch !== loadEpoch.current) return;
      if (e instanceof ApiError && e.status === 401) {
        setUser(null);
        setData(null);
        setEditor(null);
      }
      setError(errorMessage(e));
      throw e;
    } finally {
      if (epoch === loadEpoch.current) setLoading(false);
    }
  }, [user, contextId]);
  useEffect(() => {
    if (user) void reload().catch(() => {});
  }, [user, reload]);
  async function logout() {
    ++loadEpoch.current;
    identity.current = "";
    setLoading(false);
    try {
      await api.logout();
      setUser(null);
      setData(null);
      setEditor(null);
      setAreaId("");
      setView("calendar");
    } catch (e) {
      setError(errorMessage(e));
    }
  }
  async function download(format: "xlsx" | "pdf" | "json" | "full-v4") {
    if (!data || !contextId) return;
    const capturedIdentity = identity.current;
    const capturedSelection = selectionEpoch.current;
    const isCurrent = () =>
      identity.current === capturedIdentity &&
      selectionEpoch.current === capturedSelection;
    setDownloading(true);
    setError("");
    try {
      const query = new URLSearchParams({
        contextId,
        month: String(month),
        ...(areaId ? { areaId } : {}),
        format,
      });
      await api.download(
        `/exports/${format === "json" || format === "full-v4" ? "backup" : "monthly"}?${query}`,
        `plan-${contextId}-${data.plan.year}-${month}${format === "full-v4" ? "-escuela-v4" : ""}.${format === "full-v4" ? "json" : format}`,
        isCurrent,
      );
    } catch (e) {
      if (isCurrent()) setError(errorMessage(e));
    } finally {
      if (isCurrent()) setDownloading(false);
    }
  }
  if (checking) return <Loading />;
  if (!user)
    return (
      <>
        {error && (
          <div className="mx-auto max-w-xl px-6 pt-4">
            <ErrorNotice message={error} />
          </div>
        )}
        <Login
          onLogin={(u) => {
            ++loadEpoch.current;
            setData(null);
            setUser(u);
            setError("");
          }}
        />
      </>
    );
  const annualCoverage =
    view === "annual" && data ? buildAnnualCoverage(data) : null;
  const selectedTopics =
    data?.topics.filter((t) => !areaId || t.areaId === areaId) ?? [];
  const topicIds = new Set(selectedTopics.map((t) => t.id));
  const scopedSessions =
    data?.sessions.filter((s) => topicIds.has(s.topicId)) ?? [];
  const prefix = `${data?.plan.year}-${String(month).padStart(2, "0")}`;
  const currentSessions = scopedSessions.filter((s) =>
    s.exactDate?.startsWith(prefix),
  );
  const unscheduled = scopedSessions.filter(
    (s) => !s.exactDate && s.assignment === months[month - 1],
  );
  const conflicts = monthConflicts(scopedSessions);
  const editable = !!data?.areas.some(
    (a) => (!areaId || a.id === areaId) && canEdit(user, a),
  );
  function editSession(session: Session) {
    setEditor({ kind: "sessions", item: session });
  }
  function newSession(topic?: Topic, date?: string) {
    setEditor({ kind: "sessions", topicId: topic?.id, date });
  }
  return (
    <div className="app-shell">
      <a href="#main" className="skip-link">
        Ir al contenido
      </a>
      <aside className={`sidebar ${mobile ? "open" : ""}`}>
        <div className="brand">
          <span className="brand-icon">
            <BookOpen size={23} />
          </span>
          <span>
            aula<span className="text-teal-400">.</span>
          </span>
        </div>
        <div className="school-pill">
          <p className="mb-1 text-[10px] uppercase tracking-[.16em] text-teal-200">
            Mi escuela
          </p>
          <p className="text-sm font-semibold">{user.school.name}</p>
          <p className="mt-1 text-xs text-teal-100/60">
            {context?.displayLabel ?? "Selecciona un plan"}
          </p>
        </div>
        <p className="mb-3 px-3 text-[10px] font-bold uppercase tracking-[.16em] text-slate-400">
          Espacio de trabajo
        </p>
        <nav aria-label="Principal" className="space-y-1">
          {navigation.map((n) => (
            <button
              className={`nav-item ${view === n.id ? "active" : ""}`}
              key={n.id}
              onClick={() => {
                setView(n.id);
                setMobile(false);
              }}
              aria-current={view === n.id ? "page" : undefined}
            >
              <n.icon size={18} />
              {n.label}
              {view === n.id && (
                <span className="ml-auto h-1.5 w-1.5 rounded-full bg-teal-300" />
              )}
            </button>
          ))}
        </nav>
        <div className="sidebar-note">
          <BookOpen size={21} className="mb-3 text-teal-300" />
          <p className="text-sm font-semibold">
            Más claridad, mejor enseñanza.
          </p>
          <p className="mt-2 text-xs leading-relaxed text-slate-400">
            Un plan compartido para acompañar cada paso del aprendizaje.
          </p>
        </div>
        <div className="sidebar-user">
          <span className="avatar bg-teal-800 text-teal-100">
            {user.username.slice(0, 2).toUpperCase()}
          </span>
          <div className="min-w-0 flex-1">
            <p className="break-words text-xs font-semibold text-white">
              {user.username}
            </p>
            <p className="mt-1 text-[10px] text-slate-400">
              {user.role === "ADMIN" ? "Administración" : "Docente"}
            </p>
          </div>
          <button
            aria-label="Cerrar sesión"
            className="rounded-lg p-2 text-slate-400 hover:bg-slate-800 focus-visible:ring-2"
            onClick={() => void logout()}
          >
            <LogOut size={16} />
          </button>
        </div>
      </aside>
      <div className="workspace">
        <header className="topbar">
          <div className="flex items-center gap-3">
            <Button
              variant="ghost"
              size="icon"
              className="lg:hidden"
              aria-label="Abrir o cerrar navegación"
              aria-expanded={mobile}
              onClick={() => setMobile(!mobile)}
            >
              <Menu size={20} />
            </Button>
            <span className="text-xs text-slate-500">
              Espacio docente <span className="mx-2 text-slate-300">/</span>
              <strong className="font-medium text-slate-700">
                {navigation.find((n) => n.id === view)?.label}
              </strong>
            </span>
          </div>
          <span className="badge success">Escuela compartida</span>
        </header>
        <main id="main" className="main-content">
          <div className="mb-7 flex flex-wrap items-start justify-between gap-4">
            <div>
              <p className="eyebrow">
                {context?.displayLabel ?? "PLAN ACADÉMICO"}
              </p>
              <h1 className="page-title">
                {view === "calendar"
                  ? "Planifica con propósito."
                  : view === "annual"
                    ? "El aprendizaje, a lo largo del año."
                    : view === "topics"
                      ? "Cada tema, un nuevo aprendizaje."
                      : view === "imports"
                        ? "Tu trabajo, siempre conectado."
                        : "Un equipo, un mismo plan."}
              </h1>
              <p className="mt-2 text-sm text-slate-500">
                {view === "calendar"
                  ? "Organiza las sesiones de tu escuela y sigue el avance de cada área."
                  : view === "annual"
                    ? "Consulta el catálogo anual completo del plan seleccionado, sin filtros mensuales."
                    : view === "topics"
                      ? "Explora, organiza y programa los temas de tu catálogo."
                      : view === "imports"
                        ? "Revisa los cambios antes de incorporarlos al plan compartido."
                        : "Administra responsables, horarios y fechas importantes."}
              </p>
            </div>
            <div className="flex gap-2">
              <Button
                variant="outline"
                aria-label="Recargar planificación"
                onClick={() => void reload().catch(() => {})}
                disabled={loading}
              >
                <RefreshCw
                  size={16}
                  className={loading ? "animate-spin" : ""}
                />
              </Button>
              {view === "calendar" && (
                <Button disabled={!editable} onClick={() => newSession()}>
                  <Plus size={16} />
                  Nueva sesión
                </Button>
              )}
            </div>
          </div>
          <AcademicContexts
            key={user.id}
            contexts={contexts}
            selected={contextId}
            admin={user.role === "ADMIN"}
            onSelect={selectContext}
            onCreated={(created) => {
              setContexts((items) => [...items, created]);
              if (
                selectionEpoch.current === renderSelection &&
                (identity.current === `${user.id}:${contextId}` ||
                  (!contextId && !identity.current))
              )
                selectContext(created.id);
            }}
          />
          {error && (
            <div className="mb-5">
              <ErrorNotice
                message={error}
                onReload={() => void reload().catch(() => {})}
              />
            </div>
          )}
          {!data ? (
            contextId ? (
              <Loading />
            ) : (
              <p className="text-sm text-slate-500">
                Selecciona o crea un plan para comenzar.
              </p>
            )
          ) : (
            <>
              {data.areas.length === 0 && (
                <Card className="mb-5 p-5">
                  <h2 className="text-sm font-semibold">
                    Este plan empieza vacío
                  </h2>
                  <p className="mt-2 text-sm text-slate-500">
                    {user.role === "ADMIN"
                      ? "Crea la primera área y luego agrega sus temas personalizados. Ningún contenido de otro año o etapa se copia automáticamente."
                      : "La administración debe crear las áreas de este plan antes de agregar temas."}
                  </p>
                  {user.role === "ADMIN" && (
                    <Button
                      className="mt-3"
                      onClick={() => setEditor({ kind: "areas" })}
                    >
                      Crear primera área
                    </Button>
                  )}
                </Card>
              )}
              <div
                style={view === "annual" ? { display: "none" } : undefined}
                className="mb-6 flex flex-wrap items-center justify-between gap-3"
              >
                <div className="flex flex-wrap gap-3">
                  <Select
                    aria-label="Filtrar por área"
                    value={areaId}
                    onChange={(e) => setAreaId(e.target.value)}
                    className="w-auto min-w-48"
                  >
                    <option value="">Todas las áreas</option>
                    {data.areas.map((a) => (
                      <option key={a.id} value={a.id}>
                        {a.name}
                      </option>
                    ))}
                  </Select>
                  <div className="flex items-center rounded-lg border border-slate-200 bg-white">
                    <Button
                      variant="ghost"
                      size="icon"
                      aria-label="Mes anterior"
                      disabled={month === 1}
                      onClick={() => setMonth(month - 1)}
                    >
                      <ChevronLeft size={16} />
                    </Button>
                    <Select
                      aria-label="Mes"
                      className="min-w-36 border-0 text-center"
                      value={month}
                      onChange={(e) => setMonth(Number(e.target.value))}
                    >
                      {months.map((m, i) => (
                        <option key={m} value={i + 1}>
                          {m} {data.plan.year}
                        </option>
                      ))}
                    </Select>
                    <Button
                      variant="ghost"
                      size="icon"
                      aria-label="Mes siguiente"
                      disabled={month === 12}
                      onClick={() => setMonth(month + 1)}
                    >
                      <ChevronRight size={16} />
                    </Button>
                  </div>
                </div>
                <details className="relative">
                  <summary className="export-summary">
                    <Download size={15} />
                    {downloading ? "Preparando…" : "Exportar plan"}
                  </summary>
                  <div className="export-menu">
                    <p className="mb-2 text-xs text-slate-500">
                      Área y mes seleccionados
                    </p>
                    <Button
                      variant="ghost"
                      disabled={downloading}
                      onClick={() => void download("xlsx")}
                    >
                      Descargar Excel (.xlsx)
                    </Button>
                    <Button
                      variant="ghost"
                      disabled={downloading}
                      onClick={() => void download("pdf")}
                    >
                      Descargar PDF
                    </Button>
                    <Button
                      variant="ghost"
                      disabled={
                        downloading ||
                        data.plan.level !== "PRIMARY" ||
                        data.plan.stageValue !== 6
                      }
                      onClick={() => void download("json")}
                    >
                      <FileJson size={15} />
                      Catálogo heredado del plan · v2
                    </Button>
                    <p className="mt-1 text-[10px] text-amber-800">
                      Solo sexto de Primaria. Catálogo heredado del plan y
                      filtros seleccionados; no incluye{" "}
                      {
                        data.topics.filter(
                          (t) =>
                            !t.legacyId && (!areaId || t.areaId === areaId),
                        ).length
                      }{" "}
                      temas personalizados del área seleccionada; usa v4 para
                      conservarlos.
                    </p>
                    <hr className="my-2" />
                    <Button
                      variant="ghost"
                      disabled={downloading}
                      onClick={() => void download("full-v4")}
                    >
                      Toda la escuela · todos los planes · v4
                    </Button>
                    <p className="mt-1 text-[10px] text-slate-500">
                      Todos los años, niveles, edades/grados y meses, incluidas
                      áreas y temas personalizados. Su restauración automática
                      aún no está disponible; no se importa como v2.
                    </p>
                  </div>
                </details>
              </div>
              {view === "annual" && annualCoverage && context && (
                <div className="space-y-6">
                  <AnnualCoverageSummary
                    coverage={annualCoverage}
                    contextLabel={context.displayLabel}
                  />
                  <AnnualCoverageDetails
                    coverage={annualCoverage}
                    contextLabel={context.displayLabel}
                    contextKey={`${user.id}:${context.id}`}
                  />
                </div>
              )}
              {view === "calendar" && (
                <>
                  <div className="stats-grid">
                    <Card className="stat-card">
                      <span className="stat-icon bg-indigo-50 text-indigo-600">
                        <CalendarDays size={21} />
                      </span>
                      <div>
                        <p className="stat-label">
                          Sesiones con fecha · {months[month - 1]}
                        </p>
                        <p className="stat-value">
                          {currentSessions.length}
                          <span>programadas</span>
                        </p>
                      </div>
                    </Card>
                    <Card className="stat-card">
                      <span className="stat-icon bg-teal-50 text-teal-700">
                        <CheckCheck size={21} />
                      </span>
                      <div>
                        <p className="stat-label">Avance del mes</p>
                        <p className="stat-value">
                          {
                            currentSessions.filter(
                              (s) => s.status === "Trabajado",
                            ).length
                          }
                          <span>de {currentSessions.length} trabajadas</span>
                        </p>
                      </div>
                    </Card>
                    <Card className="stat-card">
                      <span className="stat-icon bg-amber-50 text-amber-600">
                        <CalendarClock size={21} />
                      </span>
                      <div>
                        <p className="stat-label">Asignadas sin fecha exacta</p>
                        <p className="stat-value">
                          {unscheduled.length}
                          <span>por programar</span>
                        </p>
                      </div>
                    </Card>
                  </div>
                  {(conflicts.length > 0 ||
                    data.plan.warnings.length > 0 ||
                    unscheduled.length > 0) && (
                    <details className="mb-5 rounded-xl border border-amber-200 bg-amber-50 p-4">
                      <summary className="flex cursor-pointer items-center gap-2 text-sm font-semibold text-amber-900">
                        <TriangleAlert size={17} />
                        Revisar planificación · {conflicts.length} diferencias
                        mes/fecha · {data.plan.warnings.length} solapamientos ·{" "}
                        {unscheduled.length} sin fecha
                      </summary>
                      <ul className="mt-3 space-y-2 text-xs text-amber-950">
                        {conflicts.map((s) => (
                          <li key={"c" + s.id}>
                            <button
                              className="text-left underline"
                              onClick={() => editSession(s)}
                            >
                              {
                                data.topics.find((t) => t.id === s.topicId)
                                  ?.name
                              }
                              : asignado a {s.assignment}, fecha exacta{" "}
                              {s.exactDate}. Se conserva esa fecha.
                            </button>
                          </li>
                        ))}
                        {data.plan.warnings.map((w, i) => (
                          <li key={"w" + i}>
                            Sesión {w.id} · {w.exactDate}: coincide con{" "}
                            {w.label}.
                          </li>
                        ))}
                        {unscheduled.map((s) => (
                          <li key={s.id}>
                            <button
                              className="text-left underline"
                              onClick={() => editSession(s)}
                            >
                              {
                                data.topics.find((t) => t.id === s.topicId)
                                  ?.name
                              }
                              : falta definir fecha exacta.
                            </button>
                          </li>
                        ))}
                      </ul>
                    </details>
                  )}
                  <Calendar
                    data={data}
                    month={month}
                    areaId={areaId}
                    editable={editable}
                    onSession={editSession}
                    onNew={(date) => newSession(undefined, date)}
                  />
                  <div className="mt-6">
                    <Topics
                      data={data}
                      user={user}
                      areaId={areaId}
                      onTopic={(t) => setEditor({ kind: "topics", item: t })}
                      onSession={editSession}
                      onNewSession={(t) => newSession(t)}
                      onNewTopic={() => setEditor({ kind: "topics" })}
                    />
                  </div>
                </>
              )}
              {view === "topics" && (
                <Topics
                  data={data}
                  user={user}
                  areaId={areaId}
                  onTopic={(t) => setEditor({ kind: "topics", item: t })}
                  onSession={editSession}
                  onNewSession={(t) => newSession(t)}
                  onNewTopic={() => setEditor({ kind: "topics" })}
                />
              )}
              {view === "imports" && (
                <Imports key={contextId} data={data} onSaved={reload} />
              )}
              {view === "settings" && (
                <Settings data={data} user={user} onEdit={setEditor} />
              )}
              <footer className="mt-8 flex flex-wrap justify-between gap-2 text-[11px] text-slate-400">
                <span>
                  {user.school.name} · {context?.displayLabel} · Revisión{" "}
                  {data.plan.revision}
                </span>
                <span>Los cambios se guardan únicamente al confirmarlos.</span>
              </footer>
              {editor && (
                <EntityEditor
                  key={`${contextId}-${editor.kind}-${editor.item?.id ?? "new"}-${editor.date ?? ""}`}
                  editor={editor}
                  data={data}
                  user={user}
                  areaId={areaId}
                  onClose={() => {
                    if (identity.current === `${user.id}:${contextId}`)
                      setEditor(null);
                  }}
                  onSaved={reload}
                  onReload={reload}
                />
              )}
            </>
          )}
        </main>
      </div>
    </div>
  );
}
