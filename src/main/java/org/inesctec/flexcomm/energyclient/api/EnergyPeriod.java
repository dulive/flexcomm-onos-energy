package org.inesctec.flexcomm.energyclient.api;

import static org.onosproject.net.DefaultAnnotations.EMPTY;

import java.time.Instant;

import org.onosproject.net.Annotated;
import org.onosproject.net.Annotations;

public interface EnergyPeriod extends Annotated {

  String emsId();

  Instant timestamp();

  double flexibility();

  double estimate();

  @Override
  default Annotations annotations() {
    return EMPTY;
  }

  interface Builder {
    Builder setEmsId(String emsId);

    Builder setTimestamp(Instant timestamp);

    Builder setFlexibility(double flexibility);

    Builder setEstimate(double estimate);

    Builder setAnnotations(Annotations anns);

    EnergyPeriod build();
  }

}
