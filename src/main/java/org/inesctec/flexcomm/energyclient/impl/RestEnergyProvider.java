package org.inesctec.flexcomm.energyclient.impl;

import static com.google.common.base.Strings.isNullOrEmpty;
import static java.util.concurrent.Executors.newScheduledThreadPool;
import static org.inesctec.flexcomm.energyclient.impl.OsgiPropertyConstants.HTTP_PASSWORD;
import static org.inesctec.flexcomm.energyclient.impl.OsgiPropertyConstants.HTTP_PASSWORD_DEFAULT;
import static org.inesctec.flexcomm.energyclient.impl.OsgiPropertyConstants.HTTP_USERNAME;
import static org.inesctec.flexcomm.energyclient.impl.OsgiPropertyConstants.HTTP_USERNAME_DEFAULT;
import static org.inesctec.flexcomm.energyclient.impl.OsgiPropertyConstants.UPDATE_RETRIES;
import static org.inesctec.flexcomm.energyclient.impl.OsgiPropertyConstants.UPDATE_RETRIES_DEFAULT;
import static org.inesctec.flexcomm.energyclient.impl.OsgiPropertyConstants.UPDATE_RETRIES_DELAY;
import static org.inesctec.flexcomm.energyclient.impl.OsgiPropertyConstants.UPDATE_RETRIES_DELAY_DEFAULT;
import static org.inesctec.flexcomm.energyclient.impl.OsgiPropertyConstants.URI_AUTHORITY;
import static org.inesctec.flexcomm.energyclient.impl.OsgiPropertyConstants.URI_AUTHORITY_DEFAULT;
import static org.inesctec.flexcomm.energyclient.impl.OsgiPropertyConstants.URI_PATH;
import static org.inesctec.flexcomm.energyclient.impl.OsgiPropertyConstants.URI_PATH_DEFAULT;
import static org.onlab.util.Tools.get;
import static org.onlab.util.Tools.groupedThreads;
import static org.slf4j.LoggerFactory.getLogger;

import java.io.InputStream;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Dictionary;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import javax.ws.rs.ProcessingException;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;

import org.glassfish.jersey.client.authentication.HttpAuthenticationFeature;
import org.inesctec.flexcomm.energyclient.api.Energy;
import org.inesctec.flexcomm.energyclient.api.EnergyProvider;
import org.inesctec.flexcomm.energyclient.api.EnergyProviderRegistry;
import org.inesctec.flexcomm.energyclient.api.EnergyProviderService;
import org.inesctec.flexcomm.energyclient.impl.objects.DefaultEnergy;
import org.inesctec.flexcomm.energyclient.impl.objects.EnergyMessage;
import org.onosproject.cfg.ComponentConfigService;
import org.onosproject.net.Device;
import org.onosproject.net.DeviceId;
import org.onosproject.net.device.DeviceEvent;
import org.onosproject.net.device.DeviceListener;
import org.onosproject.net.device.DeviceService;
import org.onosproject.net.provider.AbstractProvider;
import org.onosproject.net.provider.ProviderId;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.slf4j.Logger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.google.common.collect.Maps;

@Component(immediate = true, property = {
    URI_AUTHORITY + "=" + URI_AUTHORITY_DEFAULT,
    URI_PATH + "=" + URI_PATH_DEFAULT,
    HTTP_USERNAME + "=" + HTTP_USERNAME_DEFAULT,
    HTTP_PASSWORD + "=" + HTTP_PASSWORD_DEFAULT,
    UPDATE_RETRIES + ":Integer=" + UPDATE_RETRIES_DEFAULT,
    UPDATE_RETRIES_DELAY + ":Long=" + UPDATE_RETRIES_DELAY_DEFAULT,
})
public class RestEnergyProvider extends AbstractProvider implements EnergyProvider {

  private static final String EMSID_KEY = "emsId";
  private static final String TIMESTAMP_KEY = "timestamp";
  private static final DateFormat REQUEST_TIMESTAMP_FORMATTER = new SimpleDateFormat("yyyy-MM-dd");
  private static final DateFormat RESPONSE_TIMESTAMP_FORMATTER = new SimpleDateFormat("dd/MM/yyyy hh:mm a");
  private static final int NUM_THREADS = 4;

  private final Logger log = getLogger(getClass());

  @Reference(cardinality = ReferenceCardinality.MANDATORY)
  protected ComponentConfigService cfgService;

  @Reference(cardinality = ReferenceCardinality.MANDATORY)
  protected DeviceService deviceService;

  @Reference(cardinality = ReferenceCardinality.MANDATORY)
  protected EnergyProviderRegistry providerRegistry;

  private String energyURIAuthority = URI_AUTHORITY_DEFAULT;

  private String energyURIPath = URI_PATH_DEFAULT;

  private String energyHTTPUsername = HTTP_USERNAME_DEFAULT;

  private String energyHTTPPassword = HTTP_PASSWORD_DEFAULT;

  private int energyUpdateRetries = UPDATE_RETRIES_DEFAULT;

  private long energyUpdateRetriesDelay = UPDATE_RETRIES_DELAY_DEFAULT;

  private EnergyProviderService providerService;

  private final InternalEnergyProvider listener = new InternalEnergyProvider();

  private Client client;

  private WebTarget target;

  private ScheduledExecutorService energyExecutor;

  private Map<DeviceId, String> deviceEmsIds = Maps.newConcurrentMap();

  public RestEnergyProvider() {
    super(new ProviderId("rest", "org.inesctec.provider.energy"));
  }

  @Activate
  public void activate(ComponentContext context) {
    cfgService.registerProperties(getClass());

    energyExecutor = newScheduledThreadPool(NUM_THREADS,
        groupedThreads("inesctec/flexcomm/energy-client", "energy-updater", log));

    providerService = providerRegistry.register(this);

    deviceService.addListener(listener);

    client = ClientBuilder.newClient();
    target = client.target("http://" + energyURIAuthority + "/").path(energyURIPath);

    schedulePolling();

    modified(context);

    log.info("Started");
  }

  @Deactivate
  public void deactivate(ComponentContext context) {
    cfgService.unregisterProperties(getClass(), false);

    energyExecutor.shutdownNow();
    energyExecutor = null;

    listener.disable();

    deviceService.removeListener(listener);

    providerRegistry.unregister(this);
    providerService = null;

    deviceEmsIds.clear();

    log.info("Stopped");
  }

  @Modified
  public void modified(ComponentContext context) {
    Dictionary<?, ?> properties = context != null ? context.getProperties() : new Properties();

    String s = get(properties, URI_AUTHORITY);
    if (!isNullOrEmpty(s)) {
      energyURIAuthority = s;
    }

    s = get(properties, URI_PATH);
    if (!isNullOrEmpty(s)) {
      energyURIPath = s;
    }

    s = get(properties, HTTP_USERNAME);
    if (!Objects.isNull(s)) {
      energyHTTPUsername = s;
      log.info("Username updated");
    }

    s = get(properties, HTTP_PASSWORD);
    if (!Objects.isNull(s)) {
      energyHTTPPassword = s;
      log.info("Password updated");
    }

    try {
      s = get(properties, UPDATE_RETRIES);
      if (!isNullOrEmpty(s)) {
        energyUpdateRetries = Integer.parseInt(s.trim());
      }

      s = get(properties, UPDATE_RETRIES_DELAY);
      if (!isNullOrEmpty(s)) {
        energyUpdateRetriesDelay = Long.parseLong(s.trim());
      }
    } catch (NumberFormatException | ClassCastException e) {
      // do nothing
    }

    target = client.target("http://" + energyURIAuthority + "/").path(energyURIPath);
    if (!energyHTTPUsername.isEmpty() && !energyHTTPPassword.isEmpty()) {
      HttpAuthenticationFeature auth = HttpAuthenticationFeature.basic(energyHTTPUsername, energyHTTPPassword);
      target.register(auth);
    }

    log.info("Settings: target=http://{}/{}, retries={}, delay={}", energyURIAuthority, energyURIPath,
        energyUpdateRetries, energyUpdateRetriesDelay);
  }

  private void schedulePolling() {
    Instant now = Instant.now();
    Instant next = now.truncatedTo(ChronoUnit.DAYS).plus(1, ChronoUnit.DAYS);

    energyExecutor.scheduleAtFixedRate(this::executeEnergyUpdate, now.until(next, ChronoUnit.SECONDS),
        TimeUnit.DAYS.toSeconds(1), TimeUnit.SECONDS);
  }

  private void executeEnergyUpdate() {
    deviceEmsIds.values().stream().collect(Collectors.toSet())
        .forEach(emsId -> updateEnergy(emsId, true, energyUpdateRetriesDelay));
  }

  private void updateEnergy(String emsId, boolean verifyOutdated, long retries) {
    WebTarget query = target.queryParam(EMSID_KEY, emsId);

    for (int i = 0; i < (energyUpdateRetries + 1); ++i) {
      EnergyMessage data = doRequest(query);

      if (data == null) {
        return;
      }

      if (!data.getEmsId().equals(emsId)) {
        log.error("Received emsId does not match with query {}\n{}",
            query, data);
        return;
      }

      Instant responseTimestamp = parseTimestamp(data);
      if (!verifyOutdated || Objects.equals(responseTimestamp, Instant.now().truncatedTo(ChronoUnit.DAYS))) {
        Energy.Builder builder = DefaultEnergy.builder();
        Energy energy = builder.setEmsId(data.getEmsId())
            .setTimestamp(responseTimestamp)
            .setFlexibilityArray(data.getFlexArrayConsumption())
            .setEstimateArray(data.getFlexArrayEstimate())
            .build();

        providerService.updateEnergy(emsId, energy);
        return;
      } else {
        log.warn("Received outdated energy info for emsId {}", emsId);
        if (retries > 0) {
          log.warn("Repeating GET request for emsId {} in {} seconds", emsId, energyUpdateRetriesDelay);
          energyExecutor.schedule(() -> updateEnergy(emsId, verifyOutdated, retries - 1), energyUpdateRetriesDelay,
              TimeUnit.SECONDS);
        }
      }
    }
  }

  private EnergyMessage doRequest(WebTarget query) {
    Response response;
    try {
      response = query.request().get();
    } catch (ProcessingException e) {
      log.error("Unable to do GET request {}\n{}", query,
          e.getCause().getMessage());
      return null;
    }

    if (response.getStatus() != Response.Status.OK.getStatusCode()) {
      log.error("GET request failed for query {}\n{}",
          query,
          response.getStatusInfo());
      return null;
    }

    List<EnergyMessage> energyResponse;
    ObjectMapper oMapper = new ObjectMapper();
    try {
      energyResponse = oMapper.readValue(response.readEntity(InputStream.class),
          TypeFactory.defaultInstance().constructCollectionType(List.class, EnergyMessage.class));
    } catch (Exception e) {
      log.error("Response body format is invalid: {}", e.getCause().getMessage());
      return null;
    }

    if (energyResponse == null || energyResponse.isEmpty()) {
      log.error("Received empty response for query: {}", query);
      return null;
    }

    return energyResponse.get(0);
  }

  private Instant parseTimestamp(EnergyMessage data) {
    Instant responseTimestamp;
    try {
      responseTimestamp = RESPONSE_TIMESTAMP_FORMATTER.parse(data.getTimestamp()).toInstant()
          .truncatedTo(ChronoUnit.DAYS);
    } catch (ParseException e) {
      log.warn("Failed to parse timestamp with invalid format: {}", e.getCause().getMessage());
      responseTimestamp = null;
    }
    return responseTimestamp;
  }

  @Override
  public Energy performTimestampRequest(String emsId, Instant timestamp) {
    WebTarget query = target.queryParam(EMSID_KEY, emsId).queryParam(TIMESTAMP_KEY,
        REQUEST_TIMESTAMP_FORMATTER.format(timestamp));
    EnergyMessage data = doRequest(query);
    if (data == null) {
      return null;
    }

    if (!data.getEmsId().equals(emsId)) {
      log.error("Received emsId does not match with query {}\n{}",
          query, data);
      return null;
    }

    Instant responseTimestamp = parseTimestamp(data);
    if (!Objects.equals(responseTimestamp, timestamp.truncatedTo(ChronoUnit.DAYS))) {
      log.error("Received timestamp does not match with query {}\n{}",
          query, data);
      return null;
    }

    Energy.Builder builder = DefaultEnergy.builder();
    Energy energy = builder.setEmsId(data.getEmsId())
        .setTimestamp(responseTimestamp)
        .setFlexibilityArray(data.getFlexArrayConsumption())
        .setEstimateArray(data.getFlexArrayEstimate())
        .build();
    providerService.updateStaticEnergy(emsId, energy);
    return energy;
  }

  private class InternalEnergyProvider implements DeviceListener {

    private boolean isDisable = false;

    @Override
    public void event(DeviceEvent event) {
      if (isDisable) {
        return;
      }

      String emsId;
      Device device = event.subject();
      switch (event.type()) {
        case DEVICE_ADDED:
        case DEVICE_UPDATED:
          if (device.annotations().keys().contains(EMSID_KEY)) {
            emsId = device.annotations().value(EMSID_KEY);
            if (!deviceEmsIds.values().contains(emsId)) {
              updateEnergy(emsId, false, 0L);
            }
            deviceEmsIds.put(device.id(), emsId);
          } else {
            emsId = deviceEmsIds.remove(device.id());
            if (!deviceEmsIds.values().contains(emsId)) {
              providerService.removeEnergy(emsId);
            }
          }
          break;

        case DEVICE_REMOVED:
          emsId = deviceEmsIds.remove(device.id());
          if (!deviceEmsIds.values().contains(emsId)) {
            providerService.removeEnergy(emsId);
          }
          break;

        default:
          break;
      }
    }

    private void disable() {
      isDisable = true;
    }
  }
}
