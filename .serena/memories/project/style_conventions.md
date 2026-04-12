# Code Style & Conventions

## Java

- **Formatter**: Palantir Java Format (via Spotless Maven plugin, auto-applied at `process-resources`)
- **Java version**: 21 (with `--parameters` compiler flag)
- **No `var`**: Always explicit types
- **Always `final`**: All variables and method parameters must be `final`
- **Spring Assert**: Use `Assert.notNull()`, `Assert.hasText()` etc. for fail-fast validation (not manual if/throw)
- **No nesting**: Max ONE level of nesting per method; extract to helper methods
- **Single responsibility**: Each method does ONE thing
- **Lombok**: Use `@Data`, `@Builder`, `@Slf4j`, `@RequiredArgsConstructor` to reduce boilerplate
- **Records**: For DTOs and value objects (Java 17+)
- **Switch expressions**: Modern arrow-style, not statement with break
- **Text blocks**: For SQL, JSON, YAML literals
- **Javadoc in Russian OK**: Classes, public methods, ALL fields (including private) must have comments
- **`@ConfigurationProperties`**: Include YAML example in Javadoc
- **No mutable static state**: Only `static final Logger` is OK
- **Named constants**: No magic numbers/strings
- **Error handling**: `@ResponseStatus` on exceptions + `@ControllerAdvice`, no try/catch in controllers
- **Persistence**: Spring Data JDBC (not JPA), `@Table`, `@Id`, `@PersistenceCreator`
- **Virtual threads**: Enabled (`spring.threads.virtual.enabled: true`)

## TypeScript / React

- **Functional components only** (`function` declarations, not arrow `const`)
- **Props**: Separate `interface` with JSDoc, declared above component
- **No `React.FC`**
- **Custom hooks**: Extract logic from components (if >1 useState+useEffect)
- **`useReducer`** for related state, `useState` for simple values
- **`useEffect`** must have cleanup or explicit comment why not needed
- **Memoization**: Only when actually needed (expensive computations, referential equality)
- **State management**: Jotai for global state, TanStack Query for server state

## Commits

- Language: English
- Style: Conventional Commits (`feat`, `fix`, `docs`, `refactor`, `test`, etc.)
- No `gnhf` prefix

