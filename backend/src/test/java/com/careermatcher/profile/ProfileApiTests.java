package com.careermatcher.profile;

import static com.careermatcher.support.TestApi.json;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.careermatcher.support.IntegrationTest;
import com.careermatcher.support.TestApi;
import com.careermatcher.support.TestApi.Session;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@IntegrationTest
class ProfileApiTests {

  static final String VALID_PROFILE =
      json(
          "{'fullName':'Grace Hopper','headline':'Aspiring backend developer',"
              + "'bio':'Line one\\nLine two','city':'Phnom Penh','countryCode':'KH',"
              + "'careerStage':'RECENT_GRADUATE','graduationYear':2025,"
              + "'linkedinUrl':'https://www.linkedin.com/in/grace','githubUrl':'https://github.com/grace',"
              + "'portfolioUrl':'https://grace.dev'}");

  static final String VALID_PREFERENCES =
      json(
          "{'desiredRoles':['Backend Developer','backend developer ',' QA Engineer'],"
              + "'preferredLocations':['Phnom Penh'],'employmentTypes':['INTERNSHIP','FULL_TIME'],"
              + "'remotePreference':'HYBRID','willingToRelocate':true,"
              + "'expectedMinSalary':800,'salaryCurrency':'USD','availableFrom':'2026-12-01'}");

  @Autowired TestApi api;

  @Test
  void newProfileStartsWithRegistrationNameAndLowCompleteness() throws Exception {
    Session s = api.register(TestApi.uniqueEmail(), "Linus");
    api.get(s.accessToken(), "/api/v1/me/profile")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.fullName").value("Linus"))
        .andExpect(jsonPath("$.completeness.percent").value(10))
        .andExpect(jsonPath("$.completeness.missing", hasItem("desiredRoles")));
  }

  @Test
  void updateProfilePersistsAndRaisesCompleteness() throws Exception {
    Session s = api.register();
    api.put(s.accessToken(), "/api/v1/me/profile", VALID_PROFILE)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.fullName").value("Grace Hopper"))
        .andExpect(jsonPath("$.bio").value("Line one\nLine two"))
        .andExpect(jsonPath("$.countryCode").value("KH"))
        .andExpect(jsonPath("$.completeness.percent").value(70));
    api.get(s.accessToken(), "/api/v1/me/profile")
        .andExpect(jsonPath("$.careerStage").value("RECENT_GRADUATE"))
        .andExpect(jsonPath("$.githubUrl").value("https://github.com/grace"));
  }

  @Test
  void blankOptionalFieldsAreStoredAsNull() throws Exception {
    Session s = api.register();
    api.put(
            s.accessToken(),
            "/api/v1/me/profile",
            json("{'fullName':'Ken','headline':'   ','linkedinUrl':''}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.headline").doesNotExist())
        .andExpect(jsonPath("$.linkedinUrl").doesNotExist());
  }

  @Test
  void updateProfileRejectsInvalidValues() throws Exception {
    Session s = api.register();
    api.put(
            s.accessToken(),
            "/api/v1/me/profile",
            json(
                "{'fullName':'Ok Name','countryCode':'XX','linkedinUrl':'https://evil.example/in/x',"
                    + "'githubUrl':'http://github.com/x','portfolioUrl':'javascript:alert(1)'}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.errors[*].field", hasItem("countryCode")))
        .andExpect(jsonPath("$.errors[*].field", hasItem("linkedinUrl")))
        .andExpect(jsonPath("$.errors[*].field", hasItem("githubUrl")))
        .andExpect(jsonPath("$.errors[*].field", hasItem("portfolioUrl")));
  }

  @Test
  void updateProfileRejectsControlCharactersAndBadEnums() throws Exception {
    Session s = api.register();
    api.put(s.accessToken(), "/api/v1/me/profile", json("{'fullName':'Bad\\u0000Name'}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("fullName"));
    api.put(
            s.accessToken(),
            "/api/v1/me/profile",
            json("{'fullName':'Ok Name','careerStage':'ASTRONAUT'}"))
        .andExpect(status().isBadRequest());
    api.put(
            s.accessToken(),
            "/api/v1/me/profile",
            json("{'fullName':'Ok Name','graduationYear':2099}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("graduationYear"));
  }

  @Test
  void preferencesRoundTripWithDeduplication() throws Exception {
    Session s = api.register();
    api.put(s.accessToken(), "/api/v1/me/preferences", VALID_PREFERENCES)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.desiredRoles", contains("Backend Developer", "QA Engineer")))
        .andExpect(jsonPath("$.employmentTypes", contains("FULL_TIME", "INTERNSHIP")))
        .andExpect(jsonPath("$.remotePreference").value("HYBRID"))
        .andExpect(jsonPath("$.salaryCurrency").value("USD"));
    api.get(s.accessToken(), "/api/v1/me/preferences")
        .andExpect(jsonPath("$.availableFrom").value("2026-12-01"))
        .andExpect(jsonPath("$.willingToRelocate").value(true));
  }

  @Test
  void preferencesRequireSalaryAndCurrencyTogether() throws Exception {
    Session s = api.register();
    api.put(s.accessToken(), "/api/v1/me/preferences", json("{'expectedMinSalary':500}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("salaryCurrency"));
    api.put(
            s.accessToken(),
            "/api/v1/me/preferences",
            json("{'expectedMinSalary':500,'salaryCurrency':'ZZZ'}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void preferencesRejectTooManyRolesAndInvalidTypes() throws Exception {
    Session s = api.register();
    String roles = "'r1','r2','r3','r4','r5','r6','r7','r8','r9','r10','r11'";
    api.put(s.accessToken(), "/api/v1/me/preferences", json("{'desiredRoles':[" + roles + "]}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("desiredRoles"));
    api.put(
            s.accessToken(),
            "/api/v1/me/preferences",
            json("{'employmentTypes':['FREELANCE_ASTRONAUT']}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void onboardingCannotCompleteUntilRequiredFieldsPresent() throws Exception {
    Session s = api.register();
    api.get(s.accessToken(), "/api/v1/me/onboarding")
        .andExpect(jsonPath("$.completed").value(false))
        .andExpect(jsonPath("$.missingRequired", hasItem("countryCode")));

    api.post(s.accessToken(), "/api/v1/me/onboarding/complete", null)
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.code").value("ONBOARDING_INCOMPLETE"))
        .andExpect(jsonPath("$.missingRequired", hasItem("desiredRoles")));

    api.put(s.accessToken(), "/api/v1/me/profile", VALID_PROFILE).andExpect(status().isOk());
    api.post(s.accessToken(), "/api/v1/me/onboarding/complete", null)
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.missingRequired", not(hasItem("countryCode"))));

    api.put(s.accessToken(), "/api/v1/me/preferences", VALID_PREFERENCES)
        .andExpect(status().isOk());
    api.post(s.accessToken(), "/api/v1/me/onboarding/complete", null)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.completed").value(true))
        .andExpect(jsonPath("$.completedAt").isNotEmpty());

    api.get(s.accessToken(), "/api/v1/auth/me")
        .andExpect(jsonPath("$.onboardingCompleted").value(true));
    api.get(s.accessToken(), "/api/v1/me/profile")
        .andExpect(jsonPath("$.completeness.percent").value(100));
  }
}
