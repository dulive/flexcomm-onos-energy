package org.inesctec.flexcomm.energyclient.impl;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Strings.isNullOrEmpty;
import static org.onosproject.security.AppGuard.checkPermission;
import static org.onosproject.security.AppPermission.Type.DEVICE_READ;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.inesctec.flexcomm.energyclient.api.Energy;
import org.inesctec.flexcomm.energyclient.api.EnergyEvent;
import org.inesctec.flexcomm.energyclient.api.EnergyListener;
import org.inesctec.flexcomm.energyclient.api.EnergyPeriod;
import org.inesctec.flexcomm.energyclient.api.EnergyProvider;
import org.inesctec.flexcomm.energyclient.api.EnergyProviderRegistry;
import org.inesctec.flexcomm.energyclient.api.EnergyProviderService;
import org.inesctec.flexcomm.energyclient.api.EnergyService;
import org.inesctec.flexcomm.energyclient.api.EnergyStore;
import org.inesctec.flexcomm.energyclient.api.EnergyStoreDelegate;
import org.inesctec.flexcomm.energyclient.impl.objects.DefaultEnergyPeriod;
import org.onosproject.cfg.ComponentConfigService;
import org.onosproject.core.CoreService;
import org.onosproject.net.Device;
import org.onosproject.net.DeviceId;
import org.onosproject.net.device.DeviceService;
import org.onosproject.net.provider.AbstractListenerProviderRegistry;
import org.onosproject.net.provider.AbstractProviderService;
import org.onosproject.net.provider.ProviderId;
import org.onosproject.openflow.controller.Dpid;
import org.onosproject.openflow.controller.OpenFlowController;
import org.onosproject.openflow.controller.OpenFlowMessageListener;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableSet;

// TODO: have an annotation on the device that define which energy provider to use (scheme)
@Component(immediate = true, service = {
    EnergyService.class,
    EnergyProviderRegistry.class
})
public class EnergyManager
    extends AbstractListenerProviderRegistry<EnergyEvent, EnergyListener, EnergyProvider, EnergyProviderService>
    implements EnergyService, EnergyProviderRegistry {

  private static final String EMSID_KEY = "emsId";
  private static final String EMS_ID_EMPTY_NULL = "Ems ID cannot be null or empty";
  private static final String DEVICE_ID_NULL = "Device ID cannot be null";
  private static final String TIMESTAMP_NULL = "Timestamp cannot be null";

  private final Logger log = LoggerFactory.getLogger(getClass());

  private final EnergyStoreDelegate delegate = new InternalEnergyStoreDelegate();

  @Reference(cardinality = ReferenceCardinality.MANDATORY)
  protected EnergyStore store;

  @Reference(cardinality = ReferenceCardinality.MANDATORY)
  protected CoreService coreService;

  @Reference(cardinality = ReferenceCardinality.MANDATORY)
  protected ComponentConfigService configService;

  @Reference(cardinality = ReferenceCardinality.MANDATORY)
  protected DeviceService deviceService;

  @Reference(cardinality = ReferenceCardinality.MANDATORY)
  protected OpenFlowController openFlowController;

  private long startTime = 0L;

  private final InternalMessageListener listener = new InternalMessageListener();

  @Activate
  protected void activate() {
    store.setDelegate(delegate);
    eventDispatcher.addSink(EnergyEvent.class, listenerRegistry);
    openFlowController.addMessageListener(listener);

    log.info("Started");
  }

  @Deactivate
  protected void deactivate() {
    store.unsetDelegate(delegate);
    eventDispatcher.removeSink(EnergyEvent.class);

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
        EnergyProvider provider = getProvider(id);
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
    Duration currentTime = Duration.ofNanos(System.nanoTime() - startTime);
    int index = (currentTime.toMinutesPart() / 15) + currentTime.toHoursPart() * 4;

    EnergyPeriod.Builder builder = DefaultEnergyPeriod.builder()
        .setEmsId(energy.emsId())
        .setTimestamp(energy.timestamp())
        .setFlexibility(energy.flexibilityArray().get(index))
        .setEstimate(energy.estimateArray().get(index))
        .setAnnotations(energy.annotations());

    return builder.build();
  }

  @Override
  protected EnergyProviderService createProviderService(EnergyProvider provider) {
    return new InternalEnergyProviderService(provider);
  }

  private class InternalEnergyStoreDelegate implements EnergyStoreDelegate {

    @Override
    public void notify(EnergyEvent event) {
      post(event);
    }

  }

  private class InternalEnergyProviderService extends AbstractProviderService<EnergyProvider>
      implements EnergyProviderService {

    InternalEnergyProviderService(EnergyProvider provider) {
      super(provider);
    }

    @Override
    public void updateEnergy(String emsId, Energy energy) {
      checkArgument(!isNullOrEmpty(emsId), EMS_ID_EMPTY_NULL);
      checkNotNull(energy, "Energy data cannot be null");
      checkValidity();

      EnergyEvent event = store.updateEnergy(emsId, energy);
      post(event);
    }

    @Override
    public void updateStaticEnergy(String emsId, Energy energy) {
      checkArgument(!isNullOrEmpty(emsId), EMS_ID_EMPTY_NULL);
      checkNotNull(energy, "Energy data cannot be null");
      checkValidity();

      EnergyEvent event = store.updateStaticEnergy(emsId, energy);
      post(event);
    }

    @Override
    public void removeEnergy(String emsId) {
      checkArgument(!isNullOrEmpty(emsId), EMS_ID_EMPTY_NULL);
      checkValidity();

      EnergyEvent event = store.removeEnergy(emsId);
      post(event);
    }
  }

  private synchronized void setStartTime() {
    if (startTime == 0L) {
      startTime = System.nanoTime();
    }
    openFlowController.removeMessageListener(listener);
    listener.disable();
  }

  private class InternalMessageListener implements OpenFlowMessageListener {

    private boolean isDisable = false;

    @Override
    public void handleIncomingMessage(Dpid dpid, OFMessage msg) {
      if (isDisable) {
        return;
      }

      setStartTime();
    }

    @Override
    public void handleOutgoingMessage(Dpid dpid, List<OFMessage> msgs) {
      return;
    }

    private void disable() {
      isDisable = true;
    }

  }

}
