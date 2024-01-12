package org.inesctec.flexcomm.energyclient.impl;

public final class OsgiPropertyConstants {

  private OsgiPropertyConstants() {

  }

  public static final String URI_AUTHORITY = "energyURIAuthority";
  public static final String URI_AUTHORITY_DEFAULT = "localhost:8080";

  public static final String URI_PATH = "energyURIPath";
  public static final String URI_PATH_DEFAULT = "api/ffgo/ems/getrtresults";

  public static final String HTTP_USERNAME = "energyHTTPUsername";
  public static final String HTTP_USERNAME_DEFAULT = "";

  public static final String HTTP_PASSWORD = "energyHTTPPassword";
  public static final String HTTP_PASSWORD_DEFAULT = "";

  public static final String UPDATE_RETRIES = "energyUpdateRetries";
  public static final int UPDATE_RETRIES_DEFAULT = 10;
}
