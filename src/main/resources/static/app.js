'use strict';

// ------------------------------------------------------------------ config

const SEARCH_URL = '/api/search';
const SUGGEST_URL = '/api/suggest';
const SUGGEST_LIMIT = 10;
const DEFAULT_PAGE_SIZE = 20;
const ALLOWED_PAGE_SIZES = [10, 20, 50, 100];
const FALLBACK_IMAGE = 'https://placehold.co/640x480?text=No+Image';

// ------------------------------------------------------------------ elements

const els = {
    form: document.getElementById('search-form'),
    input: document.getElementById('search-input'),
    suggestions: document.getElementById('suggestions'),
    summary: document.getElementById('results-summary'),
    pageSize: document.getElementById('page-size'),
    body: document.getElementById('results-body'),
    empty: document.getElementById('empty-state'),
    error: document.getElementById('error-state'),
    paginations: document.querySelectorAll('.pagination'),
    rowTemplate: document.getElementById('row-template'),
};

// ------------------------------------------------------------------ state

const state = {
    query: '',
    page: 1,
    size: DEFAULT_PAGE_SIZE,
    totalPages: 0,
    activeSuggestion: -1, // index highlighted with arrow keys; -1 = none
};

// One in-flight request per endpoint. Starting a new one aborts the old one,
// so a slow stale response can never overwrite a newer one.
let suggestController = null;
let searchController = null;

// ------------------------------------------------------------------ startup

readStateFromUrl();
syncControlsFromState();
runSearch('none');

els.form.addEventListener('submit', onSubmit);
els.input.addEventListener('input', () => fetchSuggestions(els.input.value));
els.input.addEventListener('keydown', onInputKeydown);
els.pageSize.addEventListener('change', onPageSizeChange);
els.paginations.forEach((nav) => nav.addEventListener('click', onPaginationClick));
document.addEventListener('click', onDocumentClick);
window.addEventListener('popstate', onPopState);

// ------------------------------------------------------------------ URL state

/** Reads ?q=&page=&size= so refresh, shared links and back/forward all work. */
function readStateFromUrl() {
    const params = new URLSearchParams(window.location.search);

    state.query = (params.get('q') || '').trim();

    const page = parseInt(params.get('page'), 10);
    state.page = Number.isInteger(page) && page >= 1 ? page : 1;

    const size = parseInt(params.get('size'), 10);
    state.size = ALLOWED_PAGE_SIZES.includes(size) ? size : DEFAULT_PAGE_SIZE;
}

/** mode: 'push' adds a history entry, 'replace' rewrites the current one, 'none' leaves it. */
function writeUrl(mode) {
    if (mode === 'none') {
        return;
    }
    const params = new URLSearchParams();
    if (state.query) params.set('q', state.query);
    if (state.page > 1) params.set('page', String(state.page));
    if (state.size !== DEFAULT_PAGE_SIZE) params.set('size', String(state.size));

    const queryString = params.toString();
    const url = queryString ? `?${queryString}` : window.location.pathname;

    if (mode === 'push') {
        history.pushState(null, '', url);
    } else {
        history.replaceState(null, '', url);
    }
}

function syncControlsFromState() {
    els.input.value = state.query;
    els.pageSize.value = String(state.size);
}

function onPopState() {
    readStateFromUrl();
    syncControlsFromState();
    hideSuggestions();
    runSearch('none');
}

// ------------------------------------------------------------------ suggestions

async function fetchSuggestions(text) {
    if (suggestController) {
        suggestController.abort();
    }

    if (text.trim() === '') {
        hideSuggestions();
        return;
    }

    const controller = new AbortController();
    suggestController = controller;

    const params = new URLSearchParams({ q: text, limit: String(SUGGEST_LIMIT) });

    try {
        const response = await fetch(`${SUGGEST_URL}?${params}`, { signal: controller.signal });

        if (response.status === 429) {
            return; // rate limited: quietly keep the suggestions already on screen
        }
        if (!response.ok) {
            hideSuggestions();
            return;
        }

        const data = await response.json();
        if (controller.signal.aborted) {
            return; // a newer keystroke started while we were parsing
        }
        renderSuggestions(data.suggestions);
    } catch (err) {
        if (err.name !== 'AbortError') {
            hideSuggestions(); // AbortError is expected on every keystroke; anything else is real
        }
    }
}

function renderSuggestions(suggestions) {
    els.suggestions.replaceChildren();
    state.activeSuggestion = -1;
    els.input.removeAttribute('aria-activedescendant');

    if (!suggestions || suggestions.length === 0) {
        hideSuggestions();
        return;
    }

    suggestions.forEach((text, index) => {
        const item = document.createElement('li');
        item.id = `suggestion-${index}`;
        item.className = 'suggestion';
        item.setAttribute('role', 'option');
        item.textContent = text; // textContent, never innerHTML: data can't inject markup

        // mousedown (not click) fires before the input loses focus,
        // so the dropdown is still there when we read the selection.
        item.addEventListener('mousedown', (event) => {
            event.preventDefault();
            selectSuggestion(text);
        });

        els.suggestions.appendChild(item);
    });

    els.suggestions.hidden = false;
    els.input.setAttribute('aria-expanded', 'true');
}

function hideSuggestions() {
    els.suggestions.hidden = true;
    els.suggestions.replaceChildren();
    state.activeSuggestion = -1;
    els.input.setAttribute('aria-expanded', 'false');
    els.input.removeAttribute('aria-activedescendant');
}

function cancelPendingSuggestions() {
    if (suggestController) {
        suggestController.abort();
        suggestController = null;
    }
}

function selectSuggestion(text) {
    els.input.value = text;
    submitSearch(text);
}

/** Arrow keys move through suggestions, Enter picks one, Escape closes the list. */
function onInputKeydown(event) {
    const items = els.suggestions.querySelectorAll('.suggestion');
    const isOpen = !els.suggestions.hidden && items.length > 0;

    if (event.key === 'ArrowDown' && isOpen) {
        event.preventDefault();
        highlightSuggestion((state.activeSuggestion + 1) % items.length, items);
    } else if (event.key === 'ArrowUp' && isOpen) {
        event.preventDefault();
        highlightSuggestion((state.activeSuggestion - 1 + items.length) % items.length, items);
    } else if (event.key === 'Enter' && isOpen && state.activeSuggestion >= 0) {
        event.preventDefault(); // stop the form submit; use the highlighted suggestion instead
        selectSuggestion(items[state.activeSuggestion].textContent);
    } else if (event.key === 'Escape') {
        hideSuggestions();
    }
}

function highlightSuggestion(index, items) {
    items.forEach((item, i) => {
        const active = i === index;
        item.classList.toggle('active', active);
        item.setAttribute('aria-selected', String(active));
    });
    state.activeSuggestion = index;
    els.input.setAttribute('aria-activedescendant', items[index].id);
}

function onDocumentClick(event) {
    if (!els.form.contains(event.target)) {
        hideSuggestions();
    }
}

// ------------------------------------------------------------------ search

function onSubmit(event) {
    event.preventDefault(); // handle in JS instead of a full page reload
    submitSearch(els.input.value);
}

/** Entry point for Enter, the search button, and clicking a suggestion. */
function submitSearch(text) {
    cancelPendingSuggestions(); // a late suggest response must not reopen the dropdown
    hideSuggestions();

    const query = text.trim();

    // Skip the call when the results on screen are already exactly what this search would return.
    // Still allow it after an error, so the button works as a retry.
    const alreadyShowing = query === state.query && state.page === 1 && els.error.hidden;
    if (alreadyShowing) {
        return;
    }

    state.query = query;
    state.page = 1;
    runSearch('push');
}

function goToPage(page) {
    if (page < 1 || page > state.totalPages || page === state.page) {
        return;
    }
    state.page = page;
    runSearch('push');
    window.scrollTo({ top: 0, behavior: 'smooth' });
}

function onPageSizeChange() {
    state.size = Number(els.pageSize.value);
    state.page = 1; // old page number is meaningless at a different page size
    runSearch('push');
}

async function runSearch(historyMode) {
    if (searchController) {
        searchController.abort(); // e.g. fast clicks through pages
    }
    const controller = new AbortController();
    searchController = controller;

    writeUrl(historyMode);
    setLoading(true);

    const params = new URLSearchParams({
        q: state.query,
        page: String(state.page),
        size: String(state.size),
    });

    try {
        const response = await fetch(`${SEARCH_URL}?${params}`, { signal: controller.signal });

        if (response.status === 429) {
            showError('Too many requests. Please wait a moment and try again.');
            return;
        }
        if (!response.ok) {
            throw new Error(`HTTP ${response.status}`);
        }

        const data = await response.json();
        if (controller.signal.aborted) {
            return;
        }

        // Landed past the last page (e.g. an old link): jump to the last real page.
        if (data.items.length === 0 && data.totalPages > 0 && state.page > data.totalPages) {
            state.page = data.totalPages;
            runSearch('replace');
            return;
        }

        renderResults(data);
    } catch (err) {
        if (err.name !== 'AbortError') {
            showError('Something went wrong. Please try again.');
        }
    } finally {
        if (searchController === controller) {
            setLoading(false); // only the latest request clears the loading state
        }
    }
}

// ------------------------------------------------------------------ rendering

function setLoading(isLoading) {
    document.body.classList.toggle('loading', isLoading);
    els.body.setAttribute('aria-busy', String(isLoading));
}

function renderResults(data) {
    els.error.hidden = true;
    state.totalPages = data.totalPages;

    els.summary.textContent = data.query
        ? `${data.totalItems} results for "${data.query}"`
        : `${data.totalItems} vehicles`;

    els.body.replaceChildren();
    for (const lot of data.items) {
        els.body.appendChild(buildRow(lot));
    }

    els.empty.hidden = data.items.length > 0;
    renderPagination();
}

function buildRow(lot) {
    const row = els.rowTemplate.content.firstElementChild.cloneNode(true);
    const vehicle = `${lot.year} ${lot.make} ${lot.model} ${lot.trim}`;

    const image = row.querySelector('.lot-image');
    image.src = lot.imageUrl || FALLBACK_IMAGE;
    image.alt = vehicle;
    image.addEventListener('error', () => { image.src = FALLBACK_IMAGE; }, { once: true });

    row.querySelector('.lot-number').textContent = lot.lotNumber;
    row.querySelector('.lot-vehicle').textContent = vehicle;
    row.querySelector('.lot-title').textContent = lot.titleType;
    row.querySelector('.lot-location').textContent = lot.location;
    return row;
}

function showError(message) {
    els.error.textContent = message;
    els.error.hidden = false;
    els.empty.hidden = true;
    els.body.replaceChildren();
    els.summary.textContent = '';
    state.totalPages = 0;
    renderPagination();
}

/** One click handler per nav, using event delegation instead of a listener per button. */
function onPaginationClick(event) {
    const button = event.target.closest('button');
    if (!button || button.disabled) {
        return;
    }
    if (button.dataset.page) {
        goToPage(Number(button.dataset.page));
    } else if (button.dataset.action === 'prev') {
        goToPage(state.page - 1);
    } else if (button.dataset.action === 'next') {
        goToPage(state.page + 1);
    }
}

function renderPagination() {
    els.paginations.forEach(renderPaginationInto);
}

/** Builds fresh elements per nav: a DOM node can only live in one place. */
function renderPaginationInto(nav) {
    const pageNumbers = nav.querySelector('.page-numbers');
    pageNumbers.replaceChildren();

    if (state.totalPages <= 1) {
        nav.hidden = true;
        return;
    }
    nav.hidden = false;

    for (const entry of pageWindow(state.page, state.totalPages)) {
        const item = document.createElement('li');

        if (entry === '…') {
            item.textContent = '…';
            item.className = 'page-gap';
        } else {
            const button = document.createElement('button');
            button.type = 'button';
            button.textContent = entry;
            button.dataset.page = entry;
            if (entry === state.page) {
                button.setAttribute('aria-current', 'page');
                button.disabled = true;
            }
            item.appendChild(button);
        }
        pageNumbers.appendChild(item);
    }

    nav.querySelector('.prev-page').disabled = state.page <= 1;
    nav.querySelector('.next-page').disabled = state.page >= state.totalPages;
}

/**
 * Which page buttons to show: first, last, and two either side of the current page,
 * with gaps in between. 50 pages renders as  1 … 8 9 [10] 11 12 … 50  instead of 50 buttons.
 */
function pageWindow(current, total) {
    const pages = [1];
    const start = Math.max(2, current - 2);
    const end = Math.min(total - 1, current + 2);

    if (start > 2) pages.push('…');
    for (let page = start; page <= end; page++) {
        pages.push(page);
    }
    if (end < total - 1) pages.push('…');
    if (total > 1) pages.push(total);

    return pages;
}