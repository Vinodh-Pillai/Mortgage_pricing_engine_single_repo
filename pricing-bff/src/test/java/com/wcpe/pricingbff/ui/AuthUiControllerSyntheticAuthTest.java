package com.wcpe.pricingbff.ui;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = "loanweft.dev.synthetic-auth.enabled=true")
@AutoConfigureMockMvc
class AuthUiControllerSyntheticAuthTest {
  @Autowired MockMvc mvc;

  @Test
  void syntheticLoginIssuesDevOnlySessionCookieAndMeReturnsPersona() throws Exception {
    MvcResult login = mvc.perform(post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"sarah.mitchell@wcpe.synthetic.invalid\",\"password\":\"Synthetic-Only-Password!\"}"))
        .andExpect(status().isOk())
        .andExpect(cookie().exists("LW_SYNTH_AUTH"))
        .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("HttpOnly")))
        .andExpect(jsonPath("$.user.email").value("sarah.mitchell@wcpe.synthetic.invalid"))
        .andExpect(jsonPath("$.user.fullName").value("Sarah Mitchell"))
        .andExpect(jsonPath("$.user.role").value("loan_officer"))
        .andExpect(jsonPath("$.user.synthetic").value(true))
        .andReturn();

    String cookie = login.getResponse().getHeader(HttpHeaders.SET_COOKIE);
    mvc.perform(get("/api/auth/me").header(HttpHeaders.COOKIE, cookie))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.user.email").value("sarah.mitchell@wcpe.synthetic.invalid"))
        .andExpect(jsonPath("$.user.role").value("loan_officer"));
  }

  @Test
  void syntheticRegistrationAcceptsOnlyGeneratedSyntheticPersonaPasswords() throws Exception {
    mvc.perform(post("/api/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"pricing-admin.loop-009@wcpe.synthetic.invalid\",\"password\":\"Synthetic-generated-loop009-Only!\",\"fullName\":\"Synthetic Pricing Admin\",\"role\":\"pricing_analyst\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.user.email").value("pricing-admin.loop-009@wcpe.synthetic.invalid"))
        .andExpect(jsonPath("$.user.role").value("pricing_analyst"));

    mvc.perform(post("/api/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"bad.loop-009@wcpe.synthetic.invalid\",\"password\":\"not-a-synthetic-password\",\"fullName\":\"Bad User\",\"role\":\"admin\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("Synthetic auth registration accepts only generated non-production persona passwords"));
  }

  @Test
  void syntheticPersonaBadPasswordDoesNotFallThroughToRealAuth() throws Exception {
    mvc.perform(post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"sarah.mitchell@wcpe.synthetic.invalid\",\"password\":\"wrong\"}"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error").value("Invalid synthetic persona credentials"));
  }
}
