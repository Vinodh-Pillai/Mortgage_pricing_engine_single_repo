package com.wcpe.catalog.domain;

final class RequestContext {
  private static final ThreadLocal<String> ROLES = new ThreadLocal<>();

  private RequestContext() {}

  static void roles(String roles) { ROLES.set(roles); }
  static String roles() { return ROLES.get(); }
  static void clear() { ROLES.remove(); }
}
