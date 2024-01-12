package org.inesctec.flexcomm.energyclient.impl;

import static org.inesctec.flexcomm.energyclient.api.EnergyEvent.Type.ENERGY_REMOVED;
import static org.inesctec.flexcomm.energyclient.api.EnergyEvent.Type.ENERGY_UPDATED;
import static org.inesctec.flexcomm.energyclient.api.EnergyEvent.Type.STATIC_ENERGY_REMOVED;
import static org.inesctec.flexcomm.energyclient.api.EnergyEvent.Type.STATIC_ENERGY_UPDATED;
import static org.slf4j.LoggerFactory.getLogger;

import java.util.Collection;

import org.inesctec.flexcomm.energyclient.api.Energy;
import org.inesctec.flexcomm.energyclient.api.EnergyEvent;
import org.inesctec.flexcomm.energyclient.api.EnergyStore;
import org.inesctec.flexcomm.energyclient.api.EnergyStoreDelegate;
import org.onlab.util.KryoNamespace;
import org.onosproject.store.AbstractStore;
import org.onosproject.store.serializers.KryoNamespaces;
import org.onosproject.store.service.EventuallyConsistentMap;
import org.onosproject.store.service.EventuallyConsistentMapEvent;
import org.onosproject.store.service.EventuallyConsistentMapListener;
import org.onosproject.store.service.StorageService;
import org.onosproject.store.service.WallClockTimestamp;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.slf4j.Logger;

import com.google.common.collect.ImmutableSet;

@Component(immediate = true, service = EnergyStore.class)
public class DistributedEnergyStore extends AbstractStore<EnergyEvent, EnergyStoreDelegate> implements EnergyStore {

  private final Logger log = getLogger(getClass());

  @Reference(cardinality = ReferenceCardinality.MANDATORY)
  protected StorageService storageService;

  private EventuallyConsistentMap<String, Energy> energyData;
  private EventuallyConsistentMapListener<String, Energy> energyDataListener = new InternalEnergyListerner();

  private EventuallyConsistentMap<String, Energy> staticEnergyData;
  private EventuallyConsistentMapListener<String, Energy> staticEnergyDataListener = new InternalStaticEnergyListerner();

  protected static final KryoNamespace.Builder SERIALIZER_BUILDER = KryoNamespace.newBuilder()
      .register(KryoNamespaces.API)
      .register(Energy.class);

  @Activate
  public void activate() {
    energyData = storageService.<String, Energy>eventuallyConsistentMapBuilder()
        .withName("onos-flexcomm-energy")
        .withSerializer(SERIALIZER_BUILDER)
        .withTimestampProvider((k, v) -> new WallClockTimestamp())
        .build();
    energyData.addListener(energyDataListener);

    staticEnergyData = storageService.<String, Energy>eventuallyConsistentMapBuilder()
        .withName("onos-flexcomm-static-energy")
        .withSerializer(SERIALIZER_BUILDER)
        .withTimestampProvider((k, v) -> new WallClockTimestamp())
        .build();
    staticEnergyData.addListener(staticEnergyDataListener);

    log.info("Started");
  }

  @Deactivate
  public void deactivate() {
    energyData.removeListener(energyDataListener);
    staticEnergyData.removeListener(staticEnergyDataListener);
    energyData.destroy();
    staticEnergyData.destroy();
    log.info("Stopped");
  }

  @Override
  public EnergyEvent updateEnergy(String emsId, Energy energy) {
    energyData.put(emsId, energy);

    return null;
  }

  @Override
  public EnergyEvent updateStaticEnergy(String emsId, Energy energy) {
    staticEnergyData.put(emsId, energy);
    return null;
  }

  @Override
  public Collection<Energy> getEnergy() {
    return ImmutableSet.copyOf(energyData.values());
  }

  @Override
  public Energy getEnergy(String emsId) {
    return energyData.get(emsId);
  }

  @Override
  public Collection<Energy> getStaticEnergy() {
    return ImmutableSet.copyOf(staticEnergyData.values());
  }

  @Override
  public Energy getStaticEnergy(String emsId) {
    return staticEnergyData.get(emsId);
  }

  @Override
  public EnergyEvent removeEnergy(String emsId) {
    energyData.remove(emsId);
    staticEnergyData.remove(emsId);

    return null;
  }

  private class InternalEnergyListerner implements EventuallyConsistentMapListener<String, Energy> {
    @Override
    public void event(EventuallyConsistentMapEvent<String, Energy> event) {
      Energy energy = event.value();
      switch (event.type()) {
        case PUT:
          notifyDelegate(new EnergyEvent(ENERGY_UPDATED, energy));
          break;
        case REMOVE:
          notifyDelegate(new EnergyEvent(ENERGY_REMOVED, energy));
          break;
        default:
          break;
      }
    }
  }

  private class InternalStaticEnergyListerner implements EventuallyConsistentMapListener<String, Energy> {
    @Override
    public void event(EventuallyConsistentMapEvent<String, Energy> event) {
      Energy energy = event.value();
      switch (event.type()) {
        case PUT:
          notifyDelegate(new EnergyEvent(STATIC_ENERGY_UPDATED, energy));
          break;
        case REMOVE:
          notifyDelegate(new EnergyEvent(STATIC_ENERGY_REMOVED, energy));
          break;
        default:
          break;
      }
    }
  }
}
