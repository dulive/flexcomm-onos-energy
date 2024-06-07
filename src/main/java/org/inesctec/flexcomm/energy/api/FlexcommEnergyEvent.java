package org.inesctec.flexcomm.energy.api;

import org.onosproject.event.AbstractEvent;

public class FlexcommEnergyEvent extends AbstractEvent<FlexcommEnergyEvent.Type, Energy> {

  // TODO: Add more relevant energy events
  public enum Type {
    ENERGY_UPDATED,
    ENERGY_REMOVED,
    STATIC_ENERGY_UPDATED,
    STATIC_ENERGY_REMOVED
  }

  public FlexcommEnergyEvent(Type type, Energy subject) {
    super(type, subject);
  }

  public FlexcommEnergyEvent(Type type, Energy subject, long time) {
    super(type, subject, time);
  }
}
