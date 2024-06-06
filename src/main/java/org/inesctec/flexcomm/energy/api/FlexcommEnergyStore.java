package org.inesctec.flexcomm.energy.api;

import java.util.List;

import org.onosproject.store.Store;

public interface FlexcommEnergyStore extends Store<FlexcommEnergyEvent, FlexcommEnergyStoreDelegate> {

  FlexcommEnergyEvent updateEnergy(String emsId, Energy energy);

  FlexcommEnergyEvent removeEnergy(String emsId);

  List<Energy> getEnergy();

  Energy getEnergy(String emsId);

}
