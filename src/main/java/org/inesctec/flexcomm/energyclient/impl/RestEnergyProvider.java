package org.inesctec.flexcomm.energyclient.impl;

import static com.google.common.base.Strings.isNullOrEmpty;
import static org.inesctec.flexcomm.energyclient.impl.OsgiPropertyConstants.UPDATE_RETRIES;
import static org.inesctec.flexcomm.energyclient.impl.OsgiPropertyConstants.UPDATE_RETRIES_DEFAULT;
import static org.inesctec.flexcomm.energyclient.impl.OsgiPropertyConstants.URI_AUTHORITY;
import static org.inesctec.flexcomm.energyclient.impl.OsgiPropertyConstants.URI_AUTHORITY_DEFAULT;
import static org.inesctec.flexcomm.energyclient.impl.OsgiPropertyConstants.URI_ESTIMATE_PATH;
import static org.inesctec.flexcomm.energyclient.impl.OsgiPropertyConstants.URI_ESTIMATE_PATH_DEFAULT;
import static org.inesctec.flexcomm.energyclient.impl.OsgiPropertyConstants.URI_FLEX_PATH;
import static org.inesctec.flexcomm.energyclient.impl.OsgiPropertyConstants.URI_FLEX_PATH_DEFAULT;
import static org.onlab.util.Tools.get;
import static org.slf4j.LoggerFactory.getLogger;

import java.io.InputStream;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Dictionary;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.ws.rs.ProcessingException;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;

import org.inesctec.flexcomm.energyclient.api.Energy;
import org.inesctec.flexcomm.energyclient.api.EnergyProvider;
import org.inesctec.flexcomm.energyclient.api.EnergyProviderRegistry;
import org.inesctec.flexcomm.energyclient.api.EnergyProviderService;
import org.inesctec.flexcomm.energyclient.impl.objects.DefaultEnergy;
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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Maps;

@Component(immediate = true, property = {
    URI_AUTHORITY + "=" + URI_AUTHORITY_DEFAULT,
    URI_FLEX_PATH + "=" + URI_FLEX_PATH_DEFAULT,
    URI_ESTIMATE_PATH + "=" + URI_ESTIMATE_PATH_DEFAULT,
    UPDATE_RETRIES + ":Integer=" + UPDATE_RETRIES_DEFAULT,
})
public class RestEnergyProvider extends AbstractProvider implements EnergyProvider {

  private static final String QUERY_PARAM = "id";
  private static final String EMSID_KEY = "emsId";

  private final Logger log = getLogger(getClass());

  @Reference(cardinality = ReferenceCardinality.MANDATORY)
  protected ComponentConfigService cfgService;

  @Reference(cardinality = ReferenceCardinality.MANDATORY)
  protected DeviceService deviceService;

  @Reference(cardinality = ReferenceCardinality.MANDATORY)
  protected EnergyProviderRegistry providerRegistry;

  private String energyURIAuthority = URI_AUTHORITY_DEFAULT;

  private String energyURIFlexPath = URI_FLEX_PATH_DEFAULT;

  private String energyURIEstimatePath = URI_ESTIMATE_PATH_DEFAULT;

  private int energyUpdateRetries = UPDATE_RETRIES_DEFAULT;

  private EnergyProviderService providerService;

  private final InternalEnergyProvider listener = new InternalEnergyProvider();

  private Client client;

  private WebTarget target;

  private Map<DeviceId, String> deviceEmsIds = Maps.newConcurrentMap();

  public RestEnergyProvider() {
    super(new ProviderId("rest", "org.inesctec.provider.energy"));
  }

  @Activate
  public void activate(ComponentContext context) {
    cfgService.registerProperties(getClass());

    providerService = providerRegistry.register(this);

    deviceService.addListener(listener);

    client = ClientBuilder.newClient();
    target = client.target("http://" + energyURIAuthority + "/");

    modified(context);

    log.info("Started");
  }

  @Deactivate
  public void deactivate(ComponentContext context) {
    cfgService.unregisterProperties(getClass(), false);
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

    s = get(properties, URI_FLEX_PATH);
    if (!isNullOrEmpty(s)) {
      energyURIFlexPath = s;
    }

    s = get(properties, URI_ESTIMATE_PATH);
    if (!isNullOrEmpty(s)) {
      energyURIEstimatePath = s;
    }

    try {
      s = get(properties, UPDATE_RETRIES);
      if (!isNullOrEmpty(s)) {
        energyUpdateRetries = Integer.parseInt(s.trim());
      }
    } catch (NumberFormatException | ClassCastException e) {
      // do nothing
    }

    target = client.target("http://" + energyURIAuthority + "/");

    log.info("Settings: target=http://{}, paths={} {}, retries={}", energyURIAuthority, energyURIFlexPath,
        energyURIEstimatePath, energyUpdateRetries);
  }

  private void updateEnergy(String emsId) {
    WebTarget flexibilityQuery = target.path(energyURIFlexPath).queryParam(QUERY_PARAM, emsId);
    WebTarget estimateQuery = target.path(energyURIEstimatePath).queryParam(QUERY_PARAM, emsId);

    Map.Entry<String, List<Double>> flexibilityData = null;
    Map.Entry<String, List<Double>> estimateData = null;

    for (int i = 0; i < (energyUpdateRetries + 1); ++i) {
      flexibilityData = doRequest(flexibilityQuery);

      if (flexibilityData == null) {
        return;
      }

      if (!flexibilityData.getKey().equals(emsId)) {
        log.error("Received emsId does not match with query {}\n{}",
            flexibilityQuery, flexibilityData);
        return;
      }
    }

    for (int i = 0; i < (energyUpdateRetries + 1); ++i) {
      estimateData = doRequest(estimateQuery);

      if (estimateData == null) {
        return;
      }

      if (!estimateData.getKey().equals(emsId)) {
        log.error("Received emsId does not match with query {}\n{}",
            estimateQuery, estimateData);
        return;
      }
    }

    Energy.Builder builder = DefaultEnergy.builder();
    Energy energy = builder.setEmsId(emsId)
        .setTimestamp(Instant.now().truncatedTo(ChronoUnit.DAYS))
        .setFlexibilityArray(flexibilityData.getValue())
        .setEstimateArray(estimateData.getValue())
        .build();

    providerService.updateEnergy(emsId, energy);
  }

  private Map.Entry<String, List<Double>> doRequest(WebTarget query) {
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

    Map<String, List<Double>> energyResponse;
    ObjectMapper oMapper = new ObjectMapper();
    try {
      energyResponse = oMapper.readValue(response.readEntity(InputStream.class),
          new TypeReference<Map<String, List<Double>>>() {
          });
    } catch (Exception e) {
      log.error("Response body format is invalid: {}", e.getCause().getMessage());
      return null;
    }

    if (energyResponse == null || energyResponse.isEmpty()) {
      log.error("Received empty response for query: {}", query);
      return null;
    }

    return energyResponse.entrySet().iterator().next();
  }

  @Override
  public Energy performTimestampRequest(String emsId, Instant timestamp) {
    return null;
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
              updateEnergy(emsId);
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
