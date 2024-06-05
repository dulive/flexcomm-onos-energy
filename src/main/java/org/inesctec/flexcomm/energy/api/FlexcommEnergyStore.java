package org.inesctec.flexcomm.energy.api;

import java.util.Collection;

import org.onosproject.store.Store;

public interface FlexcommEnergyStore extends Store<FlexcommEnergyEvent, FlexcommEnergyStoreDelegate> {

  FlexcommEnergyEvent updateEnergy(String emsId, Energy energy);

  FlexcommEnergyEvent updateStaticEnergy(String emsId, Energy energy);

  FlexcommEnergyEvent removeEnergy(String emsId);

  Collection<Energy> getEnergy();

  Collection<Energy> getStaticEnergy();

  Energy getEnergy(String emsId);

  Energy getStaticEnergy(String emsId);
}
