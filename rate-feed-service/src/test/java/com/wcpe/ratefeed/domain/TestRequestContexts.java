package com.wcpe.ratefeed.domain;

public final class TestRequestContexts {
  private TestRequestContexts() {}

  public static void roles(String roles) {
    RequestContext.roles(roles);
  }

  public static void clear() {
    RequestContext.clear();
  }
}
