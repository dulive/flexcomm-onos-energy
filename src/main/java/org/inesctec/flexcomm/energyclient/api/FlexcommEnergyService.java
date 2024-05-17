package org.inesctec.flexcomm.energyclient.api;

import java.time.Instant;
import java.util.Collection;

import org.onosproject.event.ListenerService;
import org.onosproject.net.DeviceId;

// TODO: change name of energy to something else
public interface FlexcommEnergyService extends ListenerService<FlexcommEnergyEvent, FlexcommEnergyListener> {

  public Collection<Energy> getEnergy();

  public Collection<Energy> getEnergy(Instant timestamp);

  public Energy getEnergy(String emsId);

  public Energy getEnergy(DeviceId deviceId);

  public Energy getEnergy(String emsId, Instant timestamp);

  public Energy getEnergy(DeviceId deviceId, Instant timestamp);

  public Collection<EnergyPeriod> getCurrentEnergyPeriod();

  public Collection<EnergyPeriod> getCurrentEnergyPeriod(Instant timestamp);

  public EnergyPeriod getCurrentEnergyPeriod(String emsId);

  public EnergyPeriod getCurrentEnergyPeriod(DeviceId deviceID);

  public EnergyPeriod getCurrentEnergyPeriod(String emsId, Instant timestamp);

  public EnergyPeriod getCurrentEnergyPeriod(DeviceId deviceID, Instant timestamp);
}
