package pe.edu.planner.progress;

public enum TopicAnnualStatus {
  COMPLETADO("Trabajado"),
  PLANIFICADO_PENDIENTE("Planificado pendiente"),
  NO_PLANIFICADO("No planificado");

  private final String displayName;

  TopicAnnualStatus(String displayName) {
    this.displayName = displayName;
  }

  public String getDisplayName() {
    return displayName;
  }
}

