package pe.edu.planner.auth;

import java.util.List;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;

public final class PlannerPrincipal extends User {
  private final String id;
  private final String schoolId;
  private final String role;

  public PlannerPrincipal(
      String id, String schoolId, String username, String password, String role) {
    super(username, password, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    this.id = id;
    this.schoolId = schoolId;
    this.role = role;
  }

  public String id() {
    return id;
  }

  public String schoolId() {
    return schoolId;
  }

  public String role() {
    return role;
  }

  public boolean admin() {
    return role.equals("ADMIN");
  }
}
