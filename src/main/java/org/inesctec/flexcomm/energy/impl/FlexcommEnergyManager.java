package org.inesctec.flexcomm.energy.impl;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Strings.isNullOrEmpty;
import static org.onosproject.security.AppGuard.checkPermission;
import static org.onosproject.security.AppPermission.Type.DEVICE_READ;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;

import org.inesctec.flexcomm.energy.api.Energy;
import org.inesctec.flexcomm.energy.api.EnergyPeriod;
import org.inesctec.flexcomm.energy.api.FlexcommEnergyEvent;
import org.inesctec.flexcomm.energy.api.FlexcommEnergyListener;
import org.inesctec.flexcomm.energy.api.FlexcommEnergyProvider;
import org.inesctec.flexcomm.energy.api.FlexcommEnergyProviderRegistry;
import org.inesctec.flexcomm.energy.api.FlexcommEnergyProviderService;
import org.inesctec.flexcomm.energy.api.FlexcommEnergyService;
import org.inesctec.flexcomm.energy.api.FlexcommEnergyStore;
import org.inesctec.flexcomm.energy.api.FlexcommEnergyStoreDelegate;
import org.inesctec.flexcomm.energy.impl.objects.DefaultEnergyPeriod;
import org.onosproject.cfg.ComponentConfigService;
import org.onosproject.core.CoreService;
import org.onosproject.net.DeviceId;
import org.onosproject.net.device.DeviceService;
import org.onosproject.net.provider.AbstractListenerProviderRegistry;
import org.onosproject.net.provider.AbstractProviderService;
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

import com.google.common.collect.ImmutableList;

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

  @Reference(cardinality = ReferenceCardinality.MANDATORY)
  protected OpenFlowController openFlowController;

  private long startTime = 0L;

  private final InternalMessageListener listener = new InternalMessageListener();

  @Activate
  protected void activate() {
    store.setDelegate(delegate);
    eventDispatcher.addSink(FlexcommEnergyEvent.class, listenerRegistry);
    openFlowController.addMessageListener(listener);

    log.info("Started");
  }

  @Deactivate
  protected void deactivate() {
    store.unsetDelegate(delegate);
    eventDispatcher.removeSink(FlexcommEnergyEvent.class);

    log.info("Stopped");
  }

  @Override
  public List<Energy> getEnergy() {
    checkPermission(DEVICE_READ);

    return store.getEnergy();
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
  public List<EnergyPeriod> getCurrentEnergyPeriod() {
    checkPermission(DEVICE_READ);

    return store.getEnergy().stream().filter(Objects::nonNull).map(this::currentEnergyPeriod)
        .collect(ImmutableList.toImmutableList());
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

  private EnergyPeriod currentEnergyPeriod(Energy energy) {
    Duration currentTime = Duration.ofNanos(System.nanoTime() - startTime);
    int index = (currentTime.toMinutesPart() / 15) + currentTime.toHoursPart() * 4;

    EnergyPeriod.Builder builder = DefaultEnergyPeriod.builder()
        .setEmsId(energy.emsId())
        .setTimestamp(energy.timestamp().plus(index * 15, ChronoUnit.MINUTES))
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
    public void removeEnergy(String emsId) {
      checkArgument(!isNullOrEmpty(emsId), EMS_ID_EMPTY_NULL);
      checkValidity();

      FlexcommEnergyEvent event = store.removeEnergy(emsId);
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
