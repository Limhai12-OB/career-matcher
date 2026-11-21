package com.careermatcher.common.ratelimit;

import com.careermatcher.common.error.RateLimitedException;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

/**
 * Token-bucket rate limiter keyed by an arbitrary string (IP, e-mail, user id). Buckets live in a
 * bounded, expiring in-memory cache. Suitable for a single backend instance; replace the cache
 * with a distributed store (e.g. Redis) before scaling horizontally.
 */
@Component
public class RateLimiter {

  private final RateLimitProperties props;
  private final Cache<String, Bucket> buckets =
      Caffeine.newBuilder().maximumSize(100_000).expireAfterAccess(Duration.ofHours(2)).build();

  public RateLimiter(RateLimitProperties props) {
    this.props = props;
  }

  /**
   * Consumes one token for {@code key} under {@code rule}.
   *
   * @throws RateLimitedException if the bucket is empty
   */
  public void consume(String namespace, String key, RateLimitProperties.Rule rule) {
    if (!props.enabled()) {
      return;
    }
    Bucket bucket = buckets.get(namespace + ':' + key, k -> newBucket(rule));
    ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
    if (!probe.isConsumed()) {
      throw new RateLimitedException(
          TimeUnit.NANOSECONDS.toSeconds(probe.getNanosToWaitForRefill()) + 1);
    }
  }

  /** Clears the bucket, e.g. after a successful login. */
  public void reset(String namespace, String key) {
    buckets.invalidate(namespace + ':' + key);
  }

  private static Bucket newBucket(RateLimitProperties.Rule rule) {
    return Bucket.builder()
        .addLimit(
            Bandwidth.builder()
                .capacity(rule.capacity())
                .refillGreedy(rule.capacity(), rule.period())
                .build())
        .build();
  }
}
