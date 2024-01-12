package org.inesctec.flexcomm.energyclient.api;

import java.util.Collection;

import org.onosproject.store.Store;

public interface EnergyStore extends Store<EnergyEvent, EnergyStoreDelegate> {

  EnergyEvent updateEnergy(String emsId, Energy energy);

  EnergyEvent updateStaticEnergy(String emsId, Energy energy);

  EnergyEvent removeEnergy(String emsId);

  Collection<Energy> getEnergy();

  Collection<Energy> getStaticEnergy();

  Energy getEnergy(String emsId);

  Energy getStaticEnergy(String emsId);
}
