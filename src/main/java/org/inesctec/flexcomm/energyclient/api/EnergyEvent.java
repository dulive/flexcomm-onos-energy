package org.inesctec.flexcomm.energyclient.api;

import org.onosproject.event.AbstractEvent;

public class EnergyEvent extends AbstractEvent<EnergyEvent.Type, Energy> {

  // TODO: Add more relevant energy events
  public enum Type {
    ENERGY_UPDATED,
    ENERGY_REMOVED,
    STATIC_ENERGY_UPDATED,
    STATIC_ENERGY_REMOVED
  }

  public EnergyEvent(Type type, Energy subject) {
    super(type, subject);
  }

  public EnergyEvent(Type type, Energy subject, long time) {
    super(type, subject, time);
  }
}
