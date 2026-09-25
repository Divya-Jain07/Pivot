# PIVOT — Pre-Deployment Security & Config Fix Plan

Scope: fixes identified from a security scan of the codebase before public deployment. Ordered by priority (money/security risk first).

---

## 1. Turn off verbose error responses (Priority: High)

**File:** `backend/src/main/resources/application.properties`

**Problem:** `server.error.include-message=always` and `server.error.include-exception=true` leak full exception messages and stack traces to any API client on error. Leftover from local debugging.

**Fix:**
```properties
# Remove or override these for production
server.error.include-message=on_param
server.error.include-exception=false
server.error.include-stacktrace=never
```

Better: use a Spring profile so local dev keeps verbose errors and prod doesn't.

- Create `application-prod.properties` with the safe values above.
- Keep the current verbose values in `application-dev.properties` (or just the default file, if default = local).
- Set `SPRING_PROFILES_ACTIVE=prod` as an env var on the deployed backend.

Also lower logging noise:
```properties
logging.level.com.pivot.agent=INFO
```
(keep DEBUG only in the dev profile)

**Effort:** ~15 min.

---

## 2. Configure CORS explicitly (Priority: High)

**Problem:** No CORS configuration exists. Locally this is masked by Vite's dev proxy. Once frontend and backend are deployed on separate domains, requests will fail — and if CORS is later opened with a wildcard, it exposes the API to any origin.

**Fix:** Add a CORS config class scoped to just the deployed frontend's domain.

**New file:** `backend/src/main/java/com/pivot/agent/config/CorsConfig.java`
```java
package com.pivot.agent.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Value("${app.frontend.url}")
    private String frontendUrl;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(frontendUrl)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(false);
    }
}
```

Add to `application.properties`:
```properties
app.frontend.url=${FRONTEND_URL}
```

Set `FRONTEND_URL=https://your-deployed-frontend.vercel.app` as an env var on the backend host.

**Note:** exclude `/api/orders/webhook` from strict CORS handling if needed — Razorpay's webhook calls aren't browser-origin requests, so CORS doesn't apply to them regardless, but don't accidentally lock the route down with unrelated auth.

**Effort:** ~30 min + testing from deployed frontend.

---

## 3. Add basic rate limiting (Priority: High — cost risk)

**Problem:** `/api/chat/extract` (calls Gemini) and `/api/orders/create` (calls Razorpay) have no request limits. A public link with no auth means anyone can script requests and run up API costs.

**Fix — simplest viable option:** a lightweight in-memory per-IP/per-session limiter using [Bucket4j](https://github.com/bucket4j/bucket4j) or a hand-rolled `ConcurrentHashMap<String, RateLimiter>`.

**Option A — Bucket4j (recommended, ~1 hr):**
1. Add dependency to `pom.xml`:
```xml
<dependency>
    <groupId>com.bucket4j</groupId>
    <artifactId>bucket4j_jdk17-core</artifactId>
    <version>8.10.1</version>
</dependency>
```
2. Add a simple filter/interceptor that buckets by IP (or `sessionId` for chat), e.g. 20 requests/minute for `/api/chat/**`, 5 requests/minute for `/api/orders/create`.
3. Return `429 Too Many Requests` when exceeded.

**Option B — quick stopgap (~20 min):** a `ConcurrentHashMap<String, AtomicInteger>` keyed by IP, reset on a scheduled task every minute, checked in a `HandlerInterceptor`. Less robust but works for a portfolio demo under low traffic.

**Also set spend alerts** on the Gemini API dashboard and Razorpay dashboard so you're notified before a runaway loop costs real money, independent of the code fix.

**Effort:** 20 min–1 hr depending on approach.

---

## 4. Fix the hardcoded/disconnected Razorpay key_id (Priority: Medium)

**File:** `frontend/src/App.jsx` (line ~29)

**Problem:** `key: 'rzp_test_TWng88f9gaN4hA'` is hardcoded and disconnected from the backend's `RAZORPAY_KEY_ID` env var. Not a secret leak (key_id is meant to be public), but a rotation/environment mismatch waiting to happen — checkout will break with a cryptic error if the two ever diverge.

**Fix:** expose the key_id from the backend so both places always agree.

**New endpoint — `backend/.../controllers/ConfigController.java`:**
```java
package com.pivot.agent.controllers;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/config")
public class ConfigController {

    @Value("${razorpay.key.id}")
    private String razorpayKeyId;

    @GetMapping
    public Map<String, String> getConfig() {
        return Map.of("razorpayKeyId", razorpayKeyId);
    }
}
```

**Frontend change — `App.jsx`:**
- On mount (or lazily before first checkout), fetch `/api/config` and store `razorpayKeyId` in state.
- Replace the hardcoded string with the fetched value in the `handleCheckout` options object.

**Effort:** ~30 min.

---

## 5. Bound the in-memory session maps (Priority: Medium — memory leak over time)

**File:** `backend/.../controllers/ChatController.java`

**Problem:** `sessionState` and `sessionHistory` are `ConcurrentHashMap`s keyed by a client-supplied `sessionId`, with no eviction. Left running, this grows unbounded.

**Fix — simplest option:** use a size-and-time-bounded cache instead of a raw map.

Add Caffeine (if not already pulled in transitively):
```xml
<dependency>
    <groupId>com.github.ben-manes.caffeine</groupId>
    <artifactId>caffeine</artifactId>
</dependency>
```

Replace:
```java
private final Map<String, ExtractedState> sessionState = new ConcurrentHashMap<>();
private final Map<String, StringBuilder> sessionHistory = new ConcurrentHashMap<>();
```
with:
```java
private final Cache<String, ExtractedState> sessionState = Caffeine.newBuilder()
        .maximumSize(10_000)
        .expireAfterAccess(Duration.ofHours(2))
        .build();

private final Cache<String, StringBuilder> sessionHistory = Caffeine.newBuilder()
        .maximumSize(10_000)
        .expireAfterAccess(Duration.ofHours(2))
        .build();
```
Update `.get()`/`.put()` calls to Caffeine's `getIfPresent()` / `put()` (API is close enough to a drop-in swap).

**Effort:** ~20 min.

---

## 6. Pre-deploy checklist (do these regardless of code changes)

- [ ] Confirm `.env` was never committed: `git log --all --full-history -- "**/.env"` should return nothing
- [ ] Rotate `RAZORPAY_KEY_SECRET`, `RAZORPAY_WEBHOOK_SECRET`, `GEMINI_API_KEY`, `AGENT_MONGODB_URI` if there's any doubt they were exposed locally
- [ ] MongoDB Atlas: create a dedicated DB user with least-privilege access (not the cluster admin), restrict network access to your backend host's IP range if the host supports static IPs
- [ ] Update the Razorpay dashboard webhook URL to point to the deployed backend (not `localhost`)
- [ ] Add a visible "Test Mode — no real payment" banner in the UI
- [ ] Set `FRONTEND_URL`, `SPRING_PROFILES_ACTIVE=prod`, and all secret env vars on the backend host (Render/Railway)
- [ ] Run `npm run build` locally and smoke-test the production build against the deployed backend before sharing the link
- [ ] Re-test the full webhook flow end-to-end once live (signature/URL mismatches are the most common deploy-time bug)

---

## Suggested order of work

1. Fix #1 (error verbosity) and #2 (CORS) — required for the app to even work correctly and safely once split across domains.
2. Fix #3 (rate limiting) — before sharing the link publicly, to avoid a cost surprise.
3. Fix #4 (key_id config) and #5 (session bounding) — can ship slightly after initial deploy if time-constrained, but do before leaving it live long-term.
4. Work through the checklist in section 6 as the final pass immediately before/after deploying.

**Total estimated effort:** ~3–4 hours for all code changes, plus deploy/test time.
