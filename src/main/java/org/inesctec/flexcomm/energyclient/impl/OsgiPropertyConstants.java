package org.inesctec.flexcomm.energyclient.impl;

public final class OsgiPropertyConstants {

  private OsgiPropertyConstants() {

  }

  public static final String URI_AUTHORITY = "energyURIAuthority";
  public static final String URI_AUTHORITY_DEFAULT = "localhost:5000";

  public static final String URI_FLEX_PATH = "energyURIFlexPath";
  public static final String URI_FLEX_PATH_DEFAULT = "flex";

  public static final String URI_ESTIMATE_PATH = "energyURIEstimatePath";
  public static final String URI_ESTIMATE_PATH_DEFAULT = "estimate";

  public static final String UPDATE_RETRIES = "energyUpdateRetries";
  public static final int UPDATE_RETRIES_DEFAULT = 10;
}
