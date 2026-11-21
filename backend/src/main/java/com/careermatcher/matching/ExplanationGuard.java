package com.careermatcher.matching;

import com.careermatcher.aiclient.dto.AiMatchExplanation;
import com.careermatcher.aiclient.dto.MatchFacts;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import tools.jackson.databind.json.JsonMapper;

/**
 * Grounding check for explanations: they may only cite skills present in the calculated factors
 * and numbers present in the facts. Anything else means the text was not derived from the
 * calculation and the explanation is rejected.
 */
final class ExplanationGuard {

  private static final Pattern NUMBER = Pattern.compile("\\d+(?:[.,]\\d+)*");
  private static final Pattern FORBIDDEN = Pattern.compile("(?i)https?://|www\\.|<[a-z/!]");

  private ExplanationGuard() {}

  static List<String> violations(MatchFacts facts, AiMatchExplanation e, JsonMapper json) {
    Set<String> allowedSkills = new HashSet<>();
    facts.components().forEach(c -> c.factors().forEach(f -> allowedSkills.addAll(f.skills())));

    Set<String> allowedNumbers = new HashSet<>();
    String serialized = json.writeValueAsString(facts);
    collectNumbers(serialized, allowedNumbers);
    // Either integer rounding of the total is an honest way to state it.
    allowedNumbers.add(String.valueOf((long) Math.floor(facts.totalScore())));
    allowedNumbers.add(String.valueOf((long) Math.ceil(facts.totalScore())));
    facts.components().forEach(c -> c.factors().forEach(f -> allowedNumbers.add(String.valueOf(f.skills().size()))));

    List<String> problems = new java.util.ArrayList<>();
    List<String> texts =
        Stream.concat(
                Stream.of(e.summary()),
                Stream.of(e.strengths(), e.gaps(), e.nextSteps()).flatMap(List::stream).map(AiMatchExplanation.Point::text))
            .toList();
    for (String t : texts) {
      if (FORBIDDEN.matcher(t).find()) {
        problems.add("links or markup");
      }
      Set<String> nums = new HashSet<>();
      collectNumbers(t, nums);
      for (String n : nums) {
        if (!allowedNumbers.contains(n)) {
          problems.add("unsupported number " + n);
        }
      }
    }
    Stream.of(e.strengths(), e.gaps(), e.nextSteps())
        .flatMap(List::stream)
        .flatMap(p -> p.skills().stream())
        .filter(s -> !allowedSkills.contains(s))
        .forEach(s -> problems.add("unknown skill " + s));
    return problems;
  }

  private static void collectNumbers(String text, Set<String> out) {
    Matcher m = NUMBER.matcher(text);
    while (m.find()) {
      String n = m.group().replace(",", "");
      out.add(n);
      if (n.endsWith(".0")) {
        out.add(n.substring(0, n.length() - 2));
      }
    }
  }
}
