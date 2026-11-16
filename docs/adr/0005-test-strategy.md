# 0005. Test strategy

Status: Superseded by 0006
Date: 2026-11-13

## Context
Business rules should be fast to test, but persistence, commit and rollback need a real database.

## Decision
- Service tests are Mockito unit tests (mocked `AccountRepository`).
- `PersistenceTest` (`@DataJpaTest`) covers mappings, constraints and locking against H2.
- `TransferIntegrationTest` (`@SpringBootTest` + MockMvc + `@Transactional`) covers the full request path.

## Consequences
Each `@DataJpaTest` and integration test rolls back, so tests don't affect each other's data.
