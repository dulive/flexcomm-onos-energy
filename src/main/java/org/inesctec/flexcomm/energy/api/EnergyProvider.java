package org.inesctec.flexcomm.energy.api;

import java.time.Instant;

import org.onosproject.net.provider.Provider;

public interface EnergyProvider extends Provider {

  public Energy performTimestampRequest(String emsId, Instant timestamp);

}
