# AI Usage

## 1. Tools and models

- **Claude Opus 5.5** (Anthropic, model ID `claude-opus-5-5`), used through the Claude app (claude.ai) in one conversation on October 5–6, 2026.
- The session had a sandboxed Linux workspace, so Claude could write files, compile and run code, run a headless browser, and search the web. It had no access to my repository. It worked from my descriptions and gave files back to me, which I integrated.

## 2. Summary

I designed the application: the data model, the API shape, the search and suggestion semantics, the frontend behavior, and the deployment target. I wrote a specification for each component and used Claude to implement it. Claude also reviewed my specs and pointed out problems in them. I reviewed every file it produced, integrated it with code I had already written (interfaces, response types, `index.html`), ran it locally, and decided which of its suggestions to adopt. Claude also tested its own output in its sandbox, and those results are noted per component below.

## 3. Log by component

Prompts are quoted from the conversation and trimmed where marked with `[…]`. The full code is in the repository, so responses are summarized.

### 3.0 Initial plan review

**Prompt (trimmed):**
> I am building a search tool to search cars […] I will generate a json file with around 1000 car record which has make, model, year, location and i will have a paginated get api for the home screen which fetches 10 records per page. […] as and when i type on the search bar i ll show top 10 suggestions, also if the user is typing too fast may be have a abortcall option and will run the backend on javaspringboot and will have static html web page. will deploy it in railways probably

**Response:** Claude reviewed the plan and wrote no code. Its points:
- With 1000 records, suggestions could be filtered entirely in the browser.
- Debouncing reduces the number of requests, whereas aborting only prevents out-of-order responses.
- Search semantics (prefix vs. contains, multi-word queries) needed defining.
- The home page and search could share one endpoint.
- The app must read Railway's `PORT` variable.

**Follow-up:** I kept search and suggestions server-side. I chose not to debounce; see section 4.

### 3.1 Data generator

**Prompt (trimmed):**
> I need a data generator that produces synthetic vehicle lot data […] A single file: scripts/DataGenerator.java, runnable with `java scripts/DataGenerator.java` […] No external libraries […] write the JSON by hand with proper string escaping. Generates exactly 1000 lots […] Uses a fixed random seed […] hardcoded catalog of about 15 real makes, each with 4–6 real models, each model with 2–3 realistic trims […] titleType: Clean (~60%), Salvage (~35%), Non-Repairable (~5%) […] Distribute makes unevenly […] Print a summary at the end.

**Response:** Claude wrote the generator to the spec. It also added a year range per model (not requested) so the data can't contain cars that never existed, such as a 2014 Kia Telluride.

Claude ran it twice and confirmed byte-identical output, 1000 unique 8-digit lot numbers, a 612 / 339 / 49 title-type split and years 2012–2026. It flagged that:
- trims are accurate per model but not per year;
- Ram's numeric model names ("1500") match other makes' models;
- the script must be run from the project root.


### 3.2 LotRepository and Normalizer

**Prompt, LotRepository (trimmed):**
> LotRepository (package com.nikhil.copartsearch.data), a Spring @Component. […] Constructor takes Spring Boot 4's auto-configured JsonMapper (Jackson 3, tools.jackson.* imports). Loads data/cars.json with ClassPathResource […] Fails fast: throw IllegalStateException with a clear message if the file is missing (IOException) or malformed (JacksonException). […] Exposes findAll().

**Response:** Claude wrote `LotRepository` and the `Lot` record. It pointed out that my fail-fast design had gaps:
- Spring Boot's mapper ignores unknown properties, and Jackson fills missing fields with `null` or `0`, so a malformed file could load without ever throwing `JacksonException`.
- An empty `[]` would load and serve empty results.

It also used `List.copyOf` instead of a read-only view.

Claude couldn't download Spring or Jackson in its sandbox, so it only checked the code's syntax against stub classes and said so.

**Prompt, Normalizer (trimmed):**
> Normalizer (package com.nikhil.copartsearch.search), a final utility class […] tokenize(String) returning List<String>. […] lowercase using Locale.ROOT […] remove any non a-z0-9 characters within each token […] expected: "Honda CR-V" → [honda, crv]; " 2.5i Premium " → [25i, premium]; "F-150 - XLT" → [f150, xlt]

**Response:** The class matched all three examples. Claude changed the whitespace split to also handle Unicode whitespace, so pasted non-breaking spaces don't join two words into one. It flagged:
- Hyphens join words while spaces split them, so `c300` won't match "C 300". It suggested fixing this in the search layer by also matching against a concatenated, space-free string.
- The class name collides with `java.text.Normalizer`.

**Follow-up:** Claude had mentioned accent folding as an option, and I chose to add it to `Normalizer`. I did not add concatenated matching, so `c300` still doesn't match "C 300". It only affects a few Mercedes-Benz trims, so I've listed it under known limitations (section 5) rather than fixing it now.

### 3.3 Search service

**Prompt (trimmed):**
> Can you write InMemorySearchService as a @Service that implements [SearchService]? […] build an inverted prefix index once in the constructor […] For lotNumber I only want the full number indexed, no prefixes […] AND semantics […] Intersect the sets starting with the smallest one. Important: copy the first set before calling retainAll so the index itself never gets modified. For ranking, a lot gets one point for each query token that exactly matches its make or model token […] Ties go year desc, then lotNumber asc […] Pagination is 1-based […] Use long for the offset math […] plain loops rather than long stream chains.

**Response:** Claude implemented the spec.

Claude didn't have my `SearchService` or `SearchResponse`, so it assumed their signatures and said where to change them. It ran 16 checks against the generated data, including 500 random queries compared with a brute-force scan.

It flagged that:
- year prefixes match almost everything, the same problem I had excluded lot-number prefixes for;
- location and title type are not searchable;
- page/size validation must happen before the service is called.

**Follow-up:** I matched the generated code to my existing interface and response type. The index covers year, make, model and trim; location and title type are not searchable. Page and size validation was added later at the controller layer (section 3.7).

### 3.4 Suggest service

**Prompt (trimmed):**
> Can you write InMemorySuggestService as a @Service that implements [SuggestService], and update SuggestController […]? When you type "p" it returns things like "Honda Pilot", "Q5 Premium Plus", "Nissan Pathfinder" […] build two kinds of phrases: "Make Model" […] and "Model Trim" […] Count how many lots each distinct phrase covers […] insert phrases in rank order, so every list in the index is already sorted best-first […] collect each phrase's prefixes into a Set before adding them […] AND semantics, so "honda pi" should give "Honda Pilot" but not "Pilot Elite".

**Response:** Claude implemented the service and controller to the spec. It assumed package names, the `SuggestResponse` shape and the route, since it didn't have my existing files. It ran 2000 random queries against a brute-force rank-then-filter scan; results matched, including order, with no duplicates.

It flagged that:
- with 288 phrases, stopping early isn't a meaningful speedup, so it's better presented as a design that scales;
- "Model Trim" phrases drop the make, so typing `1500` shows "1500 Laramie" with no sign it's a Ram;
- make+model phrases usually outrank model+trim ones.

**Follow-up:** I kept the two phrase types as specified, so the "1500 Laramie" display issue remains.

### 3.5 Frontend JS

**Prompt (trimmed):**
> I've already built index.html, so I'll describe the structure and you write app.js in plain JavaScript […] call /api/suggest on every keystroke. I'm deliberately not debouncing […] cancelling the previous request with AbortController […] If the server returns 429, just keep the suggestions already showing. Render suggestions with textContent […] Use mousedown instead of click […] if the trimmed query is the same as what's already showing and we're on page 1, skip the API call […] Two pagination bars […] window like 1 … 8 9 [10] 11 12 … 50 […] keep q, page, and size in the query string […] If someone lands on a page past the end, jump to the last real page with replaceState.

**Response:** Claude wrote `app.js` and tested it in headless Chromium. It used a mock API serving the generated data and a stand-in `index.html` built from my description, and ran 47 checks covering:
- out-of-order and late suggest responses;
- 429 handling;
- a suggestion containing HTML;
- both pagination bars;
- back/forward navigation;
- correcting a page number past the end.

The first run had three failures; all three were wrong expectations in the test, not bugs.

It flagged that without debounce, the rate limiter needs separate limits for `/api/suggest` and `/api/search`.

**Follow-up:** I did not build a rate limiter (see sections 4 and 5). I kept the 429 handling in `app.js` as defensive code, because a proxy or a future rate limiter could return a 429, and commented it as such.

### 3.6 Deployment

**Prompt (trimmed):**
> Can you walk me through deploying it to Railway? […] Spring Boot 4 on Java 21, built with Maven. The Maven wrapper is committed […] one jar serves both the page and the API […] I can't install Docker Desktop, so I need the build to happen on Railway's side […] The port […] Memory and cost […] Uptime […] Verifying before I deploy […] what to look for in the build and deploy logs […] how auto-redeploy on push works.

**Response:** Claude checked Railway's documentation and the source code of Railpack, Railway's builder. It reported:
- Railpack uses `./mvnw` only if `.mvn/wrapper/maven-wrapper.properties` is committed;
- it builds with `-DskipTests`;
- it starts the app with `-Dserver.port=$PORT` added automatically.

Its recommendations:
- Cap the heap with `JAVA_OPTS` (`-Xmx256m`, Serial GC), since Java's default heap would be sized from Railway's 48 GB per-service limit.
- Confirm Serverless (app sleeping) is off. It is off by default.
- Set a healthcheck path.
- Verify locally from a fresh clone using Railway's exact build and start commands.
- Turn on "Wait for CI", because Railway skips tests.
- Check that the rate limiter sees real client IPs behind Railway's proxy.

**Follow-up:** The app is deployed on the Railway Hobby plan, built from the GitHub repo, with `server.port` reading `PORT`. I capped the heap with `JAVA_TOOL_OPTIONS=-Xmx256m` instead of the recommended `JAVA_OPTS`, because `JAVA_TOOL_OPTIONS` applies whatever the start command is. The trade-off, which Claude pointed out: Railway also exposes variables during the build, so Maven runs with the 256 MB cap too. That's fine at this project size, but it's the first thing to check if a future build fails with an out-of-memory error.

### 3.7 Input validation

**Prompt (trimmed):**
> I hadn't tested page=0, good catch. Let's do validation now. Here are the current controllers: […] I'd also like a max length on q, say 100 characters, so nobody can send a huge string. Please keep the error body simple, something like { "status": 400, "error": "Bad Request", "message": "page must be >= 1" }, with no stack traces.

Context: after I shared a project status summary, Claude pointed out that `page=0` also causes a 500 (negative offset), in addition to the `size=0` division by zero I already knew about.

**Response:** Claude added checks to both controllers:
- `page >= 1`, `size` 1–100, `limit` 1–20, `q` at most 100 characters;
- a shared `RequestValidation` helper and a dedicated `BadRequestException`, so an unrelated `IllegalArgumentException` elsewhere is still reported as a 500;
- a `@RestControllerAdvice` returning `{status, error, message}` for validation errors, non-integer values (`page=abc`) and unexpected exceptions, with no stack traces in responses.

The advice is scoped to the two API controllers, because a catch-all `Exception` handler would otherwise turn Spring's 404s for missing static files and 405s for wrong HTTP methods into 500s. Claude checked each boundary against stub Spring classes, since it couldn't download Spring in its sandbox.

**Follow-up:** Integrated the validation layer. I also matched the frontend to the server limits: added `maxlength="100"` to the search input, and made `app.js` show the server's error message on a 400 instead of a generic error. Verified locally and on the live URL that `page=0`, `size=0`, `size=101`, `limit=0`, `page=abc` and a `q` longer than 100 characters each return a 400 with the JSON error body, and that normal searches still work.

## 4. Decisions I made myself

- **Suggestions on every keystroke, no debounce.** I checked the reference site in browser DevTools and saw it sends a suggest request per keystroke, so I matched that behavior. Each request is a cheap in-memory lookup, so I accepted unthrottled requests for this scope. Out-of-order responses are handled by cancelling the previous request with `AbortController`. Claude recommended debouncing in the initial review; I kept the reference behavior instead.
- **Scope.** I dropped odometer, damage, bid and sale date as out of scope. Each lot has lot number, year, make, model, trim, title type, location and image.
- **Skip identical searches.** If the trimmed query is the one already on screen at page 1, the search button does nothing, except when an error is showing, so the button still works as a retry.
- **Pagination above and below the results,** so users don't have to scroll to change pages.

## 5. Scope cuts and known limitations

**Not built, deliberately:**
- Rate limiting
- Filter and sort controls
- Authentication
- Bidding

**Known limitations:**
- `c300` doesn't match "C 300". Hyphens join words during normalization while spaces split them, and the search doesn't also match against a space-free string. This affects a few Mercedes-Benz trims.
- Location and title type aren't searchable.
- Suggestions for Ram's numeric models drop the make ("1500 Laramie").

## 6. About this document

Drafted by Claude from the conversation log at my request, then reviewed and completed by me.