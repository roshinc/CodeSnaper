# CodeSnap Exception Handling

All exceptions thrown by the CodeSnap library extend `CodeSnapException`, which itself extends `RuntimeException`. This means callers are never forced to use checked exception handling, but can opt in to structured error handling when they need it.

## Exception Hierarchy

```
RuntimeException
  └── CodeSnapException (abstract)
        ├── CloneException        (CLONE_ERROR)
        ├── ScanException         (SCAN_ERROR)
        ├── ParseException        (PARSE_ERROR)
        ├── CodeViolationException (CODE_VIOLATION)
        └── ProcessingException   (PROCESSING_ERROR)
```

## Error Categories

### CLONE_ERROR (`CloneException`)

Thrown when the library fails to clone or validate a Git repository.

| Trigger | Message |
|---------|---------|
| Any exception during Git clone | `Could not clone repo` |
| Failed to delete stale local repo before re-cloning | `The local repo with the wrong commit hash could not be deleted` |
| Exception from `GitRepositoryAccessor.cloneRemote()` | `Could not clone remote` |
| Repository not found at expected path after clone | `Unable to get the repo` |

### SCAN_ERROR (`ScanException`)

Thrown when source code analysis fails for reasons other than annotation violations.

| Trigger | Message |
|---------|---------|
| Unexpected error during project scanning phase | `Error scanning project` |
| Spoon model build/analysis failure | `Error analyzing source code` |

### PARSE_ERROR (`ParseException`)

Thrown when Maven project structure or POM parsing fails.

| Trigger | Message |
|---------|---------|
| Exception during Maven POM analysis phase | `Error scanning project maven project structure and POM` |
| `pom.xml` not found in the project directory | `No pom.xml found at: <path>` |

### CODE_VIOLATION (`CodeViolationException`)

Thrown when the scanned project's service annotations do not satisfy resolution rules. All 16 throw sites are in `SpoonCodeAnalyzer` and relate to `@SmartService` / `@SmartImpl` annotation validation.

**No annotations found:**
- `No @SmartService or @SmartImpl annotations found`

**Missing counterpart (strict mode):**
- `No class found with @SmartImpl annotation`
- `No interface found with @SmartService annotation`

**Too many annotations (strict mode):**
- `Expected exactly one interface with @SmartService annotation, but found N`
- `Expected exactly one class with @SmartImpl annotation, but found N`

**Lenient pair resolution failures:**
- `No valid @SmartImpl class implements any @SmartService interface`
- `Ambiguous service resolution: found N valid pairs`

**Inference failures:**
- `Cannot infer implementation: multiple @SmartService interfaces found`
- `Cannot infer interface: multiple @SmartImpl classes found`
- `No class found that implements @SmartService interface <name>`
- `Multiple classes implement @SmartService interface <name>`
- `@SmartImpl class <name> does not implement any project-local interface`
- `@SmartImpl class <name> implements multiple project-local interfaces`

**Validation failure:**
- `Service implementation <name> does not implement service interface <name>`

### PROCESSING_ERROR (`ProcessingException`)

Thrown for configuration and general processing issues that do not fit the categories above.

| Trigger | Message |
|---------|---------|
| `null` config passed to `DefaultCodeSnapper` constructor | `Invalid Snapper Config` |

## Usage

### Catch by subclass

```java
try {
    snapper.generateSnapShotForProject();
} catch (CloneException e) {
    // Git clone failed -- check network, credentials, or repo URL
} catch (CodeViolationException e) {
    // Project annotations are invalid -- report to the developer
} catch (ParseException e) {
    // POM is missing or malformed
} catch (CodeSnapException e) {
    // Any other library error
}
```

### Switch on category

```java
try {
    snapper.generateSnapShotForProject();
} catch (CodeSnapException e) {
    switch (e.getCategory()) {
        case CLONE_ERROR      -> retryClone();
        case CODE_VIOLATION   -> reportViolation(e);
        case PARSE_ERROR      -> reportParseError(e);
        case SCAN_ERROR       -> reportScanError(e);
        case PROCESSING_ERROR -> reportProcessingError(e);
    }
}
```

### Check category with `is()`

```java
try {
    snapper.generateSnapShotForProject();
} catch (CodeSnapException e) {
    if (e.is(CodeSnapErrorCategory.CODE_VIOLATION)) {
        // handle annotation problems
    }
}
```

### Handling unknown errors

Exceptions that do not originate from CodeSnap (e.g. JVM errors, third-party library failures) will **not** be a `CodeSnapException`. A trailing `catch (Exception e)` handles those:

```java
try {
    snapper.generateSnapShotForProject();
} catch (CodeSnapException e) {
    // known library error
} catch (Exception e) {
    // truly unexpected / unknown error
}
```
