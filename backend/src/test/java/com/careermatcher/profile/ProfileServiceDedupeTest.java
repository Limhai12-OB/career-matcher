package com.careermatcher.profile;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class ProfileServiceDedupeTest {

  @Test
  void dedupeTrimsDropsBlanksAndIgnoresCase() {
    assertThat(ProfileService.dedupe(Arrays.asList(" Java Dev", "java dev", "", "  ", null, "QA")))
        .containsExactly("Java Dev", "QA");
  }
}
