package org.inesctec.flexcomm.energy.rest;

import static com.google.common.base.Preconditions.checkNotNull;

import org.inesctec.flexcomm.energy.api.Energy;
import org.onosproject.codec.CodecContext;
import org.onosproject.codec.impl.AnnotatedCodec;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public final class EnergyCodec extends AnnotatedCodec<Energy> {

  @Override
  public ObjectNode encode(Energy entry, CodecContext context) {
    checkNotNull(entry, "Energy cannot be null");

    final ObjectNode result = context.mapper().createObjectNode()
        .put("emsId", entry.emsId())
        .put("timestamp", entry.timestamp().toString());
    final ArrayNode flexibilityNode = result.putArray("flexibility");
    entry.flexibilityArray().forEach(flexibilityNode::add);

    final ArrayNode estimateNode = result.putArray("estimate");
    entry.estimateArray().forEach(estimateNode::add);

    return annotate(result, entry, context);
  }

}
