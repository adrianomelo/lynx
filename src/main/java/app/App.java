package app;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.services.timestreamwrite.AmazonTimestreamWrite;
import com.amazonaws.services.timestreamwrite.AmazonTimestreamWriteClient;
import com.amazonaws.services.timestreamwrite.AmazonTimestreamWriteClientBuilder;
import com.amazonaws.services.timestreamwrite.model.Dimension;
import com.amazonaws.services.timestreamwrite.model.MeasureValueType;
import com.amazonaws.services.timestreamwrite.model.Record;
import com.amazonaws.services.timestreamwrite.model.WriteRecordsRequest;
import com.amazonaws.services.timestreamwrite.model.WriteRecordsResult;
import com.github.tornaia.geoip.GeoIP;
import com.github.tornaia.geoip.GeoIPProvider;
import io.jooby.AccessLogHandler;
import io.jooby.AttachedFile;
import io.jooby.Context;
import io.jooby.Jooby;
import io.jooby.WebSocket;
import io.jooby.aws.AwsModule;
import io.jooby.json.JacksonModule;
import is.tagomor.woothee.Classifier;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
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

  private final ClientConfiguration clientConfiguration = new ClientConfiguration()
      .withMaxConnections(1000)
      .withRequestTimeout(20 * 1000)
      .withMaxErrorRetry(4);

  {
    install(new JacksonModule());
    install(new AwsModule()
        .setup(awsCredentialsProvider -> AmazonTimestreamWriteClientBuilder
            .standard()
            .withRegion("eu-west-1")
            .withCredentials(awsCredentialsProvider)
            .withClientConfiguration(clientConfiguration)
            .enableEndpointDiscovery()
            .build()));
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
          .orElse("Other");

      String referer = ctx
          .header("referer")
          .value("Other");

      Map<String, Object> event = Map
          .of("country", country
              , "referer", referer
              , "userAgent", userAgent
              , "id", id);

      final Dimension countryDim = new Dimension()
          .withName("country")
          .withValue(country);
      final Dimension refererDim = new Dimension()
          .withName("referer")
          .withValue(referer);
      final Dimension userAgentDim = new Dimension()
          .withName("userAgentName")
          .withValue(userAgent.getOrDefault("name", "Other"));
      final Dimension uuidDim = new Dimension()
          .withName("id")
          .withValue(id);

      Record pageView = new Record()
          .withMeasureName("page_view")
          .withDimensions(List.of(countryDim, refererDim, userAgentDim, uuidDim))
          .withTime(String.valueOf(System.currentTimeMillis()))
          .withMeasureValue(String.valueOf(true))
          .withMeasureValueType(MeasureValueType.BOOLEAN);

      WriteRecordsRequest writeRecordsRequest = new WriteRecordsRequest()
          .withDatabaseName("lynx_database_test")
          .withTableName("lynx_table_test")
          .withRecords(List.of(pageView));

      WriteRecordsResult writeRecordsResult = require(AmazonTimestreamWrite.class)
          .writeRecords(writeRecordsRequest);

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
