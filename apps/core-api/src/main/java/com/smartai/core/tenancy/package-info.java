/**
 * Owns tenant resolution and enforcement policy.
 *
 * <p>No tenant-scoped data API may be exposed until tenant resolution and
 * repository-level enforcement are implemented. Composite foreign keys provide
 * structural protection only; PostgreSQL row-level security is not implemented.
 * Cross-tenant integration tests and an explicit RLS decision are release gates.</p>
 */
@ApplicationModule(displayName = "Tenancy", allowedDependencies = "platform::api")
package com.smartai.core.tenancy;

import org.springframework.modulith.ApplicationModule;
