package org.inesctec.flexcomm.energy.rest;

import static com.google.common.base.Preconditions.checkNotNull;

import org.inesctec.flexcomm.energy.api.EnergyPeriod;
import org.onosproject.codec.CodecContext;
import org.onosproject.codec.impl.AnnotatedCodec;

import com.fasterxml.jackson.databind.node.ObjectNode;

public final class EnergyPeriodCodec extends AnnotatedCodec<EnergyPeriod> {

  @Override
  public ObjectNode encode(EnergyPeriod entry, CodecContext context) {
    checkNotNull(entry, "Energy Period cannot be null");

    final ObjectNode result = context.mapper().createObjectNode()
        .put("emsId", entry.emsId())
        .put("timestamp", entry.timestamp().toString())
        .put("flexibility", entry.flexibility())
        .put("estimate", entry.estimate());

    return annotate(result, entry, context);
  }

}
