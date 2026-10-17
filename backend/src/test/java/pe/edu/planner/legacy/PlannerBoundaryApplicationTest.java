package pe.edu.planner.legacy;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import pe.edu.planner.PlannerApplication;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class PlannerBoundaryApplicationTest {
  @Autowired PlannerApplication application;

  @Test
  void startsSharedWebApplicationWithExplicitTestDatabase() {
    assertThat(application).isNotNull();
  }
}
