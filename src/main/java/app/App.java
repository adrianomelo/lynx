package app;

import com.github.tornaia.geoip.GeoIP;
import com.github.tornaia.geoip.GeoIPProvider;
import io.jooby.AccessLogHandler;
import io.jooby.AttachedFile;
import io.jooby.Context;
import io.jooby.Jooby;
import io.jooby.WebSocket;
import io.jooby.json.JacksonModule;
import is.tagomor.woothee.Classifier;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class App extends Jooby {

  private final GeoIP geoIP = GeoIPProvider.getGeoIP();
  private final Path file = Paths.get("conf/small.gif");
  private final ConcurrentHashMap<UUID, Set<WebSocket>> connectedUsers = new ConcurrentHashMap();

  {
    install(new JacksonModule());
    decorator(new AccessLogHandler());

    get("/{uuid}.gif", ctx -> {
      Optional<UUID> maybeUuid = getUuid(ctx);
      String id = maybeUuid
          .map(UUID::toString)
          .orElseThrow();
      UUID uuid = maybeUuid.get();

      String userAgentHeader = ctx
          .header("User-Agent")
          .value("");
      Map<String, String> userAgent = Classifier
          .parse(userAgentHeader);

      String remoteAddress = ctx.getRemoteAddress();
      String country = geoIP
          .getTwoLetterCountryCode(remoteAddress)
          .orElse("");

      String referer = ctx
          .header("referer")
          .value("");

      Map<String, Object> event = Map
          .of("country", country
              , "referer", referer
              , "userAgent", userAgent
              , "id", id);

      Optional
          .ofNullable(connectedUsers.get(uuid))
          .ifPresent(it -> {
            it.forEach(ws -> {
              ws.render(event);
            });
          });

      return new AttachedFile(file);
    });

    ws("/ws/{uuid}", (ctx, configurer) -> {
      configurer.onConnect(ws -> {
        getUuid(ctx)
            .ifPresent(it -> {
              connectedUsers.putIfAbsent(it, new HashSet());
              connectedUsers.computeIfPresent(it, (uuid, webSockets) -> {
                webSockets.add(ws);
                return webSockets;
              });
            });
      });

      configurer.onMessage((ws, message) -> {
      });

      configurer.onClose((ws, statusCode) -> {
        getUuid(ctx)
            .ifPresent(it -> {
              connectedUsers.computeIfPresent(it, (uuid, webSockets) -> {
                webSockets.remove(ws);
                return webSockets;
              });
            });
      });

      configurer.onError((ws, cause) -> {
          log.error("onError {}: {}", ws, cause);
      });
    });
  }

  Optional<UUID> getUuid(Context ctx) {
    UUID uuid = UUID.fromString(ctx.path("uuid").value(""));
    return Optional
        .ofNullable(uuid);
  }

  public static void main(final String[] args) {
    runApp(args, App::new);
  }

}
