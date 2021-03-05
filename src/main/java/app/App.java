package app;

import com.github.tornaia.geoip.GeoIP;
import com.github.tornaia.geoip.GeoIPProvider;
import io.jooby.AttachedFile;
import io.jooby.Jooby;
import is.tagomor.woothee.Classifier;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class App extends Jooby {

  private GeoIP geoIP = GeoIPProvider.getGeoIP();
  private Path file = Paths.get("conf/small.gif");

  {
    get("/{uuid}.gif", ctx -> {
      UUID uuid = UUID.fromString(ctx.path("uuid").value());
      String id = Optional
          .ofNullable(uuid)
          .map(UUID::toString)
          .orElseThrow();

      String userAgentHeader = ctx
          .header("User-Agent")
          .value("");
      Map<String, String> userAgent = Classifier
          .parse(userAgentHeader);

      String remoteAddress = ctx.getRemoteAddress();
      String country = geoIP
          .getTwoLetterCountryCode(remoteAddress)
          .orElse("");

      String referer = ctx.header("referer").value("");

      Map a = Map
          .of("country", country
              , "referer", referer
              , "userAgent", userAgent
              , "id", id);
      System.out.println(a);

      return new AttachedFile(file);
    });
  }

  public static void main(final String[] args) {
    runApp(args, App::new);
  }

}
