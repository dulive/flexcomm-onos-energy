package org.inesctec.flexcomm.energyclient.api;

import org.onosproject.net.provider.ProviderService;

public interface EnergyProviderService extends ProviderService<EnergyProvider> {

  void updateEnergy(String emsId, Energy energy);

  void updateStaticEnergy(String emsId, Energy energy);

  void removeEnergy(String emsId);
}
