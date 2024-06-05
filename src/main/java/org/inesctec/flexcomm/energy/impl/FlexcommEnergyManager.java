package org.inesctec.flexcomm.energy.impl;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Strings.isNullOrEmpty;
import static org.onosproject.security.AppGuard.checkPermission;
import static org.onosproject.security.AppPermission.Type.DEVICE_READ;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import org.inesctec.flexcomm.energy.api.Energy;
import org.inesctec.flexcomm.energy.api.FlexcommEnergyEvent;
import org.inesctec.flexcomm.energy.api.FlexcommEnergyListener;
import org.inesctec.flexcomm.energy.api.EnergyPeriod;
import org.inesctec.flexcomm.energy.api.FlexcommEnergyProvider;
import org.inesctec.flexcomm.energy.api.FlexcommEnergyProviderRegistry;
import org.inesctec.flexcomm.energy.api.FlexcommEnergyProviderService;
import org.inesctec.flexcomm.energy.api.FlexcommEnergyService;
import org.inesctec.flexcomm.energy.api.FlexcommEnergyStore;
import org.inesctec.flexcomm.energy.api.FlexcommEnergyStoreDelegate;
import org.inesctec.flexcomm.energy.impl.objects.DefaultEnergyPeriod;
import org.onosproject.cfg.ComponentConfigService;
import org.onosproject.core.CoreService;
import org.onosproject.net.Device;
import org.onosproject.net.DeviceId;
import org.onosproject.net.device.DeviceService;
import org.onosproject.net.provider.AbstractListenerProviderRegistry;
import org.onosproject.net.provider.AbstractProviderService;
import org.onosproject.net.provider.ProviderId;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableSet;

@Component(immediate = true, service = {
    FlexcommEnergyService.class,
    FlexcommEnergyProviderRegistry.class
})
public class FlexcommEnergyManager
    extends
    AbstractListenerProviderRegistry<FlexcommEnergyEvent, FlexcommEnergyListener, FlexcommEnergyProvider, FlexcommEnergyProviderService>
    implements FlexcommEnergyService, FlexcommEnergyProviderRegistry {

  private static final String EMSID_KEY = "emsId";
  private static final String EMS_ID_EMPTY_NULL = "Ems ID cannot be null or empty";
  private static final String DEVICE_ID_NULL = "Device ID cannot be null";
  private static final String TIMESTAMP_NULL = "Timestamp cannot be null";

  private final Logger log = LoggerFactory.getLogger(getClass());

  private final FlexcommEnergyStoreDelegate delegate = new InternalEnergyStoreDelegate();

  @Reference(cardinality = ReferenceCardinality.MANDATORY)
  protected FlexcommEnergyStore store;

  @Reference(cardinality = ReferenceCardinality.MANDATORY)
  protected CoreService coreService;

  @Reference(cardinality = ReferenceCardinality.MANDATORY)
  protected ComponentConfigService configService;

  @Reference(cardinality = ReferenceCardinality.MANDATORY)
  protected DeviceService deviceService;

  @Activate
  protected void activate() {
    store.setDelegate(delegate);
    eventDispatcher.addSink(FlexcommEnergyEvent.class, listenerRegistry);

    log.info("Started");
  }

  @Deactivate
  protected void deactivate() {
    store.unsetDelegate(delegate);
    eventDispatcher.removeSink(FlexcommEnergyEvent.class);

    log.info("Stopped");
  }

  @Override
  public Collection<Energy> getEnergy() {
    checkPermission(DEVICE_READ);

    return store.getEnergy();
  }

  @Override
  public Collection<Energy> getEnergy(Instant timestamp) {
    checkPermission(DEVICE_READ);
    checkNotNull(timestamp, TIMESTAMP_NULL);

    return ImmutableSet.copyOf(getEnergyEachDevice(timestamp));
  }

  @Override
  public Energy getEnergy(String emsId) {
    checkPermission(DEVICE_READ);
    checkArgument(!isNullOrEmpty(emsId), EMS_ID_EMPTY_NULL);

    return store.getEnergy(emsId);
  }

  @Override
  public Energy getEnergy(DeviceId deviceId) {
    checkPermission(DEVICE_READ);
    checkNotNull(deviceId, DEVICE_ID_NULL);

    return getEnergy(deviceService.getDevice(deviceId).annotations().value(EMSID_KEY));
  }

  @Override
  public Energy getEnergy(String emsId, Instant timestamp) {
    checkPermission(DEVICE_READ);
    checkArgument(!isNullOrEmpty(emsId), EMS_ID_EMPTY_NULL);
    checkNotNull(timestamp, TIMESTAMP_NULL);

    Energy res = store.getStaticEnergy(emsId);
    if (res == null || !res.timestamp().equals(timestamp.truncatedTo(ChronoUnit.DAYS))) {
      for (ProviderId id : getProviders()) {
        FlexcommEnergyProvider provider = getProvider(id);
        res = provider.performTimestampRequest(emsId, timestamp);
        if (res != null) {
          break;
        }
      }
    }

    return res;
  }

  @Override
  public Energy getEnergy(DeviceId deviceId, Instant timestamp) {
    checkPermission(DEVICE_READ);
    checkNotNull(deviceId, DEVICE_ID_NULL);
    checkNotNull(timestamp, TIMESTAMP_NULL);

    return getEnergy(deviceService.getDevice(deviceId).annotations().value(EMSID_KEY), timestamp);
  }

  @Override
  public Collection<EnergyPeriod> getCurrentEnergyPeriod() {
    checkPermission(DEVICE_READ);

    return store.getEnergy().stream().filter(Objects::nonNull).map(this::currentEnergyPeriod)
        .collect(ImmutableSet.toImmutableSet());
  }

  @Override
  public Collection<EnergyPeriod> getCurrentEnergyPeriod(Instant timestamp) {
    checkPermission(DEVICE_READ);
    checkNotNull(timestamp, TIMESTAMP_NULL);

    return getEnergyEachDevice(timestamp).stream().filter(Objects::nonNull).map(this::currentEnergyPeriod)
        .collect(ImmutableSet.toImmutableSet());
  }

  @Override
  public EnergyPeriod getCurrentEnergyPeriod(String emsId) {
    checkPermission(DEVICE_READ);
    checkArgument(!isNullOrEmpty(emsId), EMS_ID_EMPTY_NULL);

    Energy e = store.getEnergy(emsId);
    return e != null ? currentEnergyPeriod(e) : null;
  }

  @Override
  public EnergyPeriod getCurrentEnergyPeriod(DeviceId deviceId) {
    checkPermission(DEVICE_READ);
    checkNotNull(deviceId, DEVICE_ID_NULL);

    return getCurrentEnergyPeriod(deviceService.getDevice(deviceId).annotations().value(EMSID_KEY));
  }

  @Override
  public EnergyPeriod getCurrentEnergyPeriod(String emsId, Instant timestamp) {
    checkPermission(DEVICE_READ);
    checkArgument(!isNullOrEmpty(emsId), EMS_ID_EMPTY_NULL);
    checkNotNull(timestamp, TIMESTAMP_NULL);

    Energy e = getEnergy(emsId, timestamp);
    return e != null ? currentEnergyPeriod(e) : null;
  }

  @Override
  public EnergyPeriod getCurrentEnergyPeriod(DeviceId deviceId, Instant timestamp) {
    checkPermission(DEVICE_READ);
    checkNotNull(deviceId, DEVICE_ID_NULL);
    checkNotNull(timestamp, TIMESTAMP_NULL);

    return getCurrentEnergyPeriod(deviceService.getDevice(deviceId).annotations().value(EMSID_KEY), timestamp);
  }

  private Set<Energy> getEnergyEachDevice(Instant timestamp) {
    Set<Energy> res = new HashSet<>();

    for (Device d : deviceService.getDevices()) {
      String emsId = d.annotations().value(EMSID_KEY);
      if (!isNullOrEmpty(emsId)) {
        Energy e = getEnergy(emsId, timestamp);
        if (e != null) {
          res.add(e);
        }
      }
    }

    return res;
  }

  private EnergyPeriod currentEnergyPeriod(Energy energy) {
    ZonedDateTime currentTime = Instant.now().atZone(ZoneOffset.UTC);
    int index = (currentTime.getMinute() / 15) + currentTime.getHour() * 4;

    EnergyPeriod.Builder builder = DefaultEnergyPeriod.builder()
        .setEmsId(energy.emsId())
        .setTimestamp(energy.timestamp())
        .setFlexibility(energy.flexibilityArray().get(index))
        .setEstimate(energy.estimateArray().get(index))
        .setAnnotations(energy.annotations());

    return builder.build();
  }

  @Override
  protected FlexcommEnergyProviderService createProviderService(FlexcommEnergyProvider provider) {
    return new InternalFlexcommEnergyProviderService(provider);
  }

  private class InternalEnergyStoreDelegate implements FlexcommEnergyStoreDelegate {

    @Override
    public void notify(FlexcommEnergyEvent event) {
      post(event);
    }

  }

  private class InternalFlexcommEnergyProviderService extends AbstractProviderService<FlexcommEnergyProvider>
      implements FlexcommEnergyProviderService {

    InternalFlexcommEnergyProviderService(FlexcommEnergyProvider provider) {
      super(provider);
    }

    @Override
    public void updateEnergy(String emsId, Energy energy) {
      checkArgument(!isNullOrEmpty(emsId), EMS_ID_EMPTY_NULL);
      checkNotNull(energy, "Energy data cannot be null");
      checkValidity();

      FlexcommEnergyEvent event = store.updateEnergy(emsId, energy);
      post(event);
    }

    @Override
    public void updateStaticEnergy(String emsId, Energy energy) {
      checkArgument(!isNullOrEmpty(emsId), EMS_ID_EMPTY_NULL);
      checkNotNull(energy, "Energy data cannot be null");
      checkValidity();

      FlexcommEnergyEvent event = store.updateStaticEnergy(emsId, energy);
      post(event);
    }

    @Override
    public void removeEnergy(String emsId) {
      checkArgument(!isNullOrEmpty(emsId), EMS_ID_EMPTY_NULL);
      checkValidity();

      FlexcommEnergyEvent event = store.removeEnergy(emsId);
      post(event);
    }
  }

}
