package com.careermatcher.gap;

import com.careermatcher.aiclient.dto.AiRoadmap;
import com.careermatcher.aiclient.dto.RoadmapFacts;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import tools.jackson.databind.json.JsonMapper;

/**
 * Validates a learning roadmap against its facts: every step must target a real gap, use only
 * curated resources for that skill, cite only numbers from the facts, and make no claims about
 * qualifications or employment outcomes.
 */
final class RoadmapGuard {

  private static final Pattern NUMBER = Pattern.compile("\\d+(?:[.,]\\d+)*");
  private static final Pattern MARKUP = Pattern.compile("(?i)https?://|www\\.|<[a-z/!]");

  /** Promises the platform must never make (employment outcomes, qualifications, certainty). */
  static final List<Pattern> UNSUPPORTED_CLAIMS =
      List.of(
          Pattern.compile("(?i)\\bguarantee(s|d)?\\b"),
          Pattern.compile("(?i)\\bpromise(s|d)?\\b"),
          Pattern.compile("(?i)\\b(will|going to)\\s+(definitely\\s+|surely\\s+)?(get|land|secure|win|receive)\\s+(you\\s+)?(a|an|the|your)?\\s*(job|offer|position|role|interview|internship)s?\\b"),
          Pattern.compile("(?i)\\b(get|be|being)\\s+hired\\b"),
          Pattern.compile("(?i)\\bjob\\s+offers?\\b"),
          Pattern.compile("(?i)\\b(makes?|making)\\s+you\\s+(fully\\s+)?(qualified|eligible|job[- ]ready)\\b"),
          Pattern.compile("(?i)\\byou\\s+(are|'re|will\\s+be)\\s+(now\\s+)?(fully\\s+)?(qualified|certified)\\b"),
          Pattern.compile("(?i)\\b(certain\\s+(to|that\\s+you)|assured\\s+(of|to)|100\\s*%\\s*(chance|sure|certain|guaranteed))"));

  private RoadmapGuard() {}

  static List<String> violations(RoadmapFacts facts, AiRoadmap roadmap, JsonMapper json) {
    List<String> problems = new ArrayList<>();
    Set<String> gapSkills = facts.gaps().stream().map(RoadmapFacts.Gap::skill).collect(Collectors.toSet());
    Map<String, RoadmapFacts.Resource> resources =
        facts.resources().stream().collect(Collectors.toMap(RoadmapFacts.Resource::id, Function.identity()));

    Set<String> allowedNumbers = new HashSet<>();
    collectNumbers(json.writeValueAsString(facts), allowedNumbers);
    for (double d : new double[] {facts.averageScore(), facts.requiredCoverage()}) {
      allowedNumbers.add(String.valueOf((long) Math.floor(d)));
      allowedNumbers.add(String.valueOf((long) Math.ceil(d)));
    }
    roadmap.steps().forEach(s -> allowedNumbers.add(String.valueOf(s.estimatedWeeks())));
    allowedNumbers.add(String.valueOf(roadmap.steps().size()));

    Set<String> seen = new HashSet<>();
    for (AiRoadmap.Step s : roadmap.steps()) {
      if (!gapSkills.contains(s.skill())) {
        problems.add("step for a skill that is not a gap: " + s.skill());
      }
      if (!seen.add(s.skill())) {
        problems.add("duplicate step for " + s.skill());
      }
      for (String id : s.resourceIds()) {
        RoadmapFacts.Resource r = resources.get(id);
        if (r == null) {
          problems.add("unknown resource " + id);
        } else if (!r.skill().equals(s.skill())) {
          problems.add("resource " + id + " is not for " + s.skill());
        }
      }
    }
    List<String> texts =
        Stream.concat(
                Stream.of(roadmap.summary()),
                roadmap.steps().stream().flatMap(s -> Stream.concat(Stream.of(s.title(), s.why()), s.activities().stream())))
            .toList();
    for (String t : texts) {
      if (MARKUP.matcher(t).find()) {
        problems.add("links or markup");
      }
      for (Pattern p : UNSUPPORTED_CLAIMS) {
        Matcher m = p.matcher(t);
        if (m.find()) {
          problems.add("unsupported claim: " + m.group());
        }
      }
      Set<String> nums = new HashSet<>();
      collectNumbers(t, nums);
      nums.stream().filter(n -> !allowedNumbers.contains(n)).forEach(n -> problems.add("unsupported number " + n));
    }
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
