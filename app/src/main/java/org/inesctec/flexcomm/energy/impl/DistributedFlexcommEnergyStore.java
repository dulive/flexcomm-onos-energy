package org.inesctec.flexcomm.energy.impl;

import static org.inesctec.flexcomm.energy.api.FlexcommEnergyEvent.Type.ENERGY_REMOVED;
import static org.inesctec.flexcomm.energy.api.FlexcommEnergyEvent.Type.ENERGY_UPDATED;
import static org.slf4j.LoggerFactory.getLogger;

import java.util.List;

import org.inesctec.flexcomm.energy.api.Energy;
import org.inesctec.flexcomm.energy.api.FlexcommEnergyEvent;
import org.inesctec.flexcomm.energy.api.FlexcommEnergyStore;
import org.inesctec.flexcomm.energy.api.FlexcommEnergyStoreDelegate;
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

import com.google.common.collect.ImmutableList;

@Component(immediate = true, service = FlexcommEnergyStore.class)
public class DistributedFlexcommEnergyStore extends AbstractStore<FlexcommEnergyEvent, FlexcommEnergyStoreDelegate>
    implements FlexcommEnergyStore {

  private final Logger log = getLogger(getClass());

  @Reference(cardinality = ReferenceCardinality.MANDATORY)
  protected StorageService storageService;

  private EventuallyConsistentMap<String, Energy> energyData;
  private EventuallyConsistentMapListener<String, Energy> energyDataListener = new InternalEnergyListerner();

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

    log.info("Started");
  }

  @Deactivate
  public void deactivate() {
    energyData.removeListener(energyDataListener);
    energyData.destroy();
    log.info("Stopped");
  }

  @Override
  public FlexcommEnergyEvent updateEnergy(String emsId, Energy energy) {
    energyData.put(emsId, energy);

    return null;
  }

  @Override
  public List<Energy> getEnergy() {
    return ImmutableList.copyOf(energyData.values());
  }

  @Override
  public Energy getEnergy(String emsId) {
    return energyData.get(emsId);
  }

  @Override
  public FlexcommEnergyEvent removeEnergy(String emsId) {
    energyData.remove(emsId);

    return null;
  }

  private class InternalEnergyListerner implements EventuallyConsistentMapListener<String, Energy> {
    @Override
    public void event(EventuallyConsistentMapEvent<String, Energy> event) {
      Energy energy = event.value();
      switch (event.type()) {
        case PUT:
          notifyDelegate(new FlexcommEnergyEvent(ENERGY_UPDATED, energy));
          break;
        case REMOVE:
          notifyDelegate(new FlexcommEnergyEvent(ENERGY_REMOVED, energy));
          break;
        default:
          break;
      }
    }
  }

}
