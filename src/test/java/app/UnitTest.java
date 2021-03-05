package app;

import io.jooby.MockRouter;
import io.jooby.StatusCode;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class UnitTest {
  @Test
  public void welcome() throws IOException {
    MockRouter router = new MockRouter(new App());
    router.get("/", rsp -> {
      assertEquals("Welcome to Jooby!", rsp.value());
      assertEquals(StatusCode.OK, rsp.getStatusCode());
    });
  }
}
