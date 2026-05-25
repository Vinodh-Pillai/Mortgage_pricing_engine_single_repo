package com.wcpe.ratefeed.config;

import java.math.RoundingMode;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * G-008 / Phase 7: Rate Feed configuration.
 *
 * Controls interpolation opt-in and rounding policy.
 */
@ConfigurationProperties(prefix = "ratefeed")
public class RateFeedConfig {

  /** Whether interpolation is enabled by default (opt-in). Default: false (fail-closed). */
  private boolean interpolationEnabled = false;

  /** Rounding mode for interpolated prices. Default: HALF_UP. */
  private RoundingMode roundingMode = RoundingMode.HALF_UP;

  public RateFeedConfig() {
  }

  public RateFeedConfig(boolean interpolationEnabled, RoundingMode roundingMode) {
    this.interpolationEnabled = interpolationEnabled;
    this.roundingMode = roundingMode;
  }

  public boolean isInterpolationEnabled() {
    return interpolationEnabled;
  }

  public void setInterpolationEnabled(boolean interpolationEnabled) {
    this.interpolationEnabled = interpolationEnabled;
  }

  public RoundingMode getRoundingMode() {
    return roundingMode;
  }

  public void setRoundingMode(RoundingMode roundingMode) {
    this.roundingMode = roundingMode;
  }
}
