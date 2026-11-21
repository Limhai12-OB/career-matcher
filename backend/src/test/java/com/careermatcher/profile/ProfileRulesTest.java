package com.careermatcher.profile;

import static org.assertj.core.api.Assertions.assertThat;

import com.careermatcher.common.error.FieldError;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ProfileRulesTest {

  @ParameterizedTest
  @ValueSource(
      strings = {
        "https://linkedin.com/in/a",
        "https://www.linkedin.com/in/a",
        "https://kh.linkedin.com/in/a"
      })
  void acceptsLinkedinUrls(String url) {
    assertThat(errors(url, ProfileRules.LINKEDIN_HOSTS)).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "http://linkedin.com/in/a",
        "https://linkedin.com.evil.example/in/a",
        "https://evillinkedin.com/in/a",
        "https://user:pass@linkedin.com/in/a",
        "javascript:alert(1)",
        "linkedin.com/in/a",
        "https://bad host/"
      })
  void rejectsUnsafeOrForeignUrls(String url) {
    assertThat(errors(url, ProfileRules.LINKEDIN_HOSTS)).hasSize(1);
  }

  @ParameterizedTest
  @ValueSource(strings = {"https://grace.dev", "https://example.com/portfolio?x=1"})
  void portfolioAcceptsAnyHttpsHost(String url) {
    assertThat(errors(url, Set.of())).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(strings = {"KH", "US", "DE"})
  void acceptsIsoCountries(String code) {
    List<FieldError> errors = new ArrayList<>();
    ProfileRules.checkCountry(code, errors);
    assertThat(errors).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(strings = {"XX", "ZZ"})
  void rejectsUnknownCountries(String code) {
    List<FieldError> errors = new ArrayList<>();
    ProfileRules.checkCountry(code, errors);
    assertThat(errors).hasSize(1);
  }

  private static List<FieldError> errors(String url, Set<String> hosts) {
    List<FieldError> errors = new ArrayList<>();
    ProfileRules.checkUrl("f", url, hosts, errors);
    return errors;
  }
}
