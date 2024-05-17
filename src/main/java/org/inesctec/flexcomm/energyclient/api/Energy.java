package org.inesctec.flexcomm.energyclient.api;

import static org.onosproject.net.DefaultAnnotations.EMPTY;

import java.time.Instant;
import java.util.List;

import org.onosproject.net.Annotated;
import org.onosproject.net.Annotations;

// TODO: change name
public interface Energy extends Annotated {

  String emsId();

  Instant timestamp();

  List<Double> flexibilityArray();

  List<Double> estimateArray();

  @Override
  default Annotations annotations() {
    return EMPTY;
  }

  interface Builder {
    Builder setEmsId(String emsId);

    Builder setTimestamp(Instant timestamp);

    Builder setFlexibilityArray(List<Double> flexibilityArray);

    Builder setEstimateArray(List<Double> estimateArray);

    Builder setAnnotations(Annotations anns);

    Energy build();
  }
}
