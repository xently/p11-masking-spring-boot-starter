---
marp: true
theme: default
paginate: true
header: 'P11 Masking Spring Boot Starter'
footer: 'Ensuring Secure & Clean Logs'
---

# Secure Logging in Spring Boot
### P11 Masking & Log Forging Prevention

**Speaker:** [Your Name]
**Date:** June 14, 2026

---

# The Problem: Sensitive Data Leakage

- **PII Leakage:** Accidentally logging emails, phone numbers, or credit card info.
- **Compliance Risks:** GDPR, PCI-DSS, and local data protection laws (e.g., P11).
- **Log Forging:** Attackers injecting control characters (`\n`) to spoof logs.
- **Manual Effort:** Hardcoding masking logic in every `toString()` or `Logger` call.

---

# The Solution: A Plug-and-Play Starter

**kcb-p11-masking-spring-boot-starter**

- **Automatic:** Intercepts logs at the Logback level.
- **Transparent:** Works with existing `log.info()`, `log.error()`, etc.
- **Configurable:** Fine-grained control via `application.yml`.
- **Annotation-powered:** Explicit control where defaults aren't enough.

---

# Feature 1: Intelligent Log Masking

Two main styles to protect sensitive fields:

1.  **FULL Masking:** Replaces the entire value with a fixed-length mask.
2.  **PARTIAL Masking:** Masks only parts of the value (e.g., `j***e@domain.com`).

**Supports:**
- Default common fields (email, password, ssn, pin, etc.)
- Custom field lists
- Regex-based pattern matching

---

# Feature 2: Log Forging Prevention

Prevents "Log Injection" attacks by sanitizing control characters.

- **Automatic Sanitization:** Simple arguments (Strings, Numbers) are cleaned.
- **Control:** Replaces `\n`, `\r`, `\t` with a configurable character (default `_`).
- **Annotation:** `@NoLogForging` for class-level or field-level enforcement.

---

# Feature 3: Annotation-Based Control

Need specific behavior for a single field? Use annotations.

```java
public record UserDto(
    String name,
    
    @Mask(style = MaskingStyle.FULL) 
    String ssn,
    
    @Mask(style = MaskingStyle.PARTIAL, maskCharacter = "#") 
    String cardNumber,
    
    @NoLogForging 
    String comment
) {}
```

---

# Easy Configuration

Just add it to your `application.yml`:

```yaml
log:
  p11:
    masking:
      enabled: true
      mask-style: PARTIAL
      fields:
        - email
        - phoneNumber
  forging:
    replacement: "_"
    replace-continuous-at-once: true
```

---

# Why We Should Use This

- **Consistency:** Centralized masking logic across all microservices.
- **Safety:** Harder to accidentally leak data in logs.
- **Developer Experience:** No more manually masking values before logging.
- **Performance:** Efficient Logback converter implementation.

---

# Quick Start

1. Add the dependency to your `pom.xml`.
2. Configure fields in `application.yml`.
3. Enjoy secure logs!

**Demo available in:** `/demo` module

---

# Q&A

**Thank You!**

*Questions? Suggestions?*
