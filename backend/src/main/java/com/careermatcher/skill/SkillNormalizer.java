package com.careermatcher.skill;

import java.text.Normalizer;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Maps free-text skill names (as written in CVs) to canonical catalog skills. Deterministic and
 * conservative: exact alias matches only, after light normalization (case, whitespace, surrounding
 * punctuation, trailing version numbers). Anything else stays unrecognized rather than guessed.
 */
@Service
public class SkillNormalizer {

  private static final Pattern WHITESPACE = Pattern.compile("\\s+");
  private static final Pattern EDGE_PUNCT = Pattern.compile("^[\\s,;:*•·\"'()\\[\\]-]+|[\\s,;:*•·\"'()\\[\\]-]+$");
  private static final Pattern VERSION_SUFFIX =
      Pattern.compile("\\s*(?:v\\.?\\s*)?\\d+(?:\\.\\d+|\\.x)*\\+?$", Pattern.CASE_INSENSITIVE);
  private static final Pattern PAREN_SUFFIX = Pattern.compile("\\s*\\([^)]*\\)$");
  private static final Pattern GENERIC_SUFFIX =
      Pattern.compile("\\s+(?:programming language|language|framework|library|programming)$");

  private final SkillRepository skills;
  private final SkillAliasRepository aliases;
  private final AtomicReference<Catalog> catalog = new AtomicReference<>();

  public SkillNormalizer(SkillRepository skills, SkillAliasRepository aliases) {
    this.skills = skills;
    this.aliases = aliases;
  }

  private record Catalog(Map<UUID, SkillRef> byId, Map<String, SkillRef> byAlias, Map<String, SkillRef> bySlug) {}

  /** Lower case, NFKC, single spaces; punctuation kept. */
  private static String base(String raw) {
    String s = Normalizer.normalize(raw == null ? "" : raw, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
    return WHITESPACE.matcher(s).replaceAll(" ").strip();
  }

  /** Normalized lookup key: {@link #base} without surrounding punctuation. */
  static String key(String raw) {
    return EDGE_PUNCT.matcher(base(raw)).replaceAll("");
  }

  public Optional<SkillRef> resolve(String rawName) {
    Map<String, SkillRef> byAlias = catalog().byAlias();
    String b = base(rawName);
    if (key(b).isEmpty()) {
      return Optional.empty();
    }
    // Most specific first: as written, then without a parenthetical, version or generic suffix.
    for (String form : List.of(b, PAREN_SUFFIX.matcher(b).replaceAll(""))) {
      String k = key(form);
      String noVersion = key(VERSION_SUFFIX.matcher(k).replaceAll(""));
      for (String candidate :
          List.of(
              k,
              noVersion,
              key(GENERIC_SUFFIX.matcher(k).replaceAll("")),
              key(GENERIC_SUFFIX.matcher(noVersion).replaceAll("")))) {
        SkillRef ref = byAlias.get(candidate);
        if (ref != null) {
          return Optional.of(ref);
        }
      }
    }
    return Optional.empty();
  }

  public Optional<SkillRef> byId(UUID id) {
    return Optional.ofNullable(catalog().byId().get(id));
  }

  public Optional<SkillRef> bySlug(String slug) {
    return Optional.ofNullable(catalog().bySlug().get(slug));
  }

  /** Catalog skills whose name or any alias contains the query, sorted by name. */
  public List<SkillRef> search(String query) {
    String q = key(query);
    Catalog c = catalog();
    java.util.stream.Stream<SkillRef> matches =
        q.isEmpty()
            ? c.byId().values().stream()
            : java.util.stream.Stream.concat(
                    c.byId().values().stream().filter(s -> key(s.name()).contains(q)),
                    c.byAlias().entrySet().stream().filter(e -> e.getKey().contains(q)).map(Map.Entry::getValue))
                .distinct();
    return matches.sorted(java.util.Comparator.comparing(SkillRef::name, String.CASE_INSENSITIVE_ORDER)).toList();
  }

  private Catalog catalog() {
    Catalog c = catalog.get();
    if (c == null) {
      c = load();
      catalog.set(c);
    }
    return c;
  }

  /** Reloads the catalog (e.g. after an admin adds aliases). */
  @Transactional(readOnly = true)
  public void reload() {
    catalog.set(load());
  }

  private Catalog load() {
    Map<UUID, SkillRef> byId = new HashMap<>();
    skills.findAll().forEach(s -> byId.put(s.getId(), s.toRef()));
    Map<String, SkillRef> byAlias = new HashMap<>();
    byId.values().forEach(s -> byAlias.put(key(s.name()), s));
    aliases.findAll().forEach(a -> {
      SkillRef ref = byId.get(a.getSkillId());
      if (ref != null) {
        byAlias.put(key(a.getAlias()), ref);
      }
    });
    Map<String, SkillRef> bySlug = new HashMap<>();
    byId.values().forEach(s -> bySlug.put(s.slug(), s));
    return new Catalog(Map.copyOf(byId), Map.copyOf(byAlias), Map.copyOf(bySlug));
  }
}
