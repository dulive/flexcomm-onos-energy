package org.inesctec.flexcomm.energy.api;

import java.time.Instant;
import java.util.List;

import org.onosproject.event.ListenerService;
import org.onosproject.net.DeviceId;

public interface FlexcommEnergyService extends ListenerService<FlexcommEnergyEvent, FlexcommEnergyListener> {

  public List<Energy> getEnergy();

  public Energy getEnergy(String emsId);

  public Energy getEnergy(DeviceId deviceId);

  public List<Energy> getEnergy(Instant timestamp);

  public Energy getEnergy(String emsId, Instant timestamp);

  public Energy getEnergy(DeviceId deviceId, Instant timestamp);

  public List<EnergyPeriod> getCurrentEnergyPeriod();

  public EnergyPeriod getCurrentEnergyPeriod(String emsId);

  public EnergyPeriod getCurrentEnergyPeriod(DeviceId deviceID);

}
