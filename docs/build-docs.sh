#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# build-docs.sh – Gather, render, and assemble all project documentation
#                 into a single directory suitable for GitHub Pages.
#
# Output: docs/site/   (ready to be published as-is)
#
# Prerequisites (the script will check for them):
#   - asciidoctor          (gem install asciidoctor)
#   - asciidoctor-diagram  (gem install asciidoctor-diagram)
#   - npx                  (comes with Node.js / npm)
#   - pandoc               (for Markdown → HTML conversion)
#
# Usage:
#   ./docs/build-docs.sh            # run from project root
#   SKIP_API=1 ./docs/build-docs.sh # skip OpenAPI rendering
# ---------------------------------------------------------------------------
set -euo pipefail

# ── Paths ──────────────────────────────────────────────────────────────────
PROJECT_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DOCS_DIR="${PROJECT_ROOT}/documentation"
SPEC_FILE="${PROJECT_ROOT}/spec/src/main/resources/static/push_gateway_openapi.yaml"
SITE_DIR="${PROJECT_ROOT}/docs/site"

# ── Colors ─────────────────────────────────────────────────────────────────
RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; NC='\033[0m'
info()  { echo -e "${GREEN}[INFO]${NC}  $*"; }
warn()  { echo -e "${YELLOW}[WARN]${NC}  $*"; }
error() { echo -e "${RED}[ERROR]${NC} $*"; }

# ── Prerequisite checks ───────────────────────────────────────────────────
check_tool() {
    if ! command -v "$1" &>/dev/null; then
        error "'$1' is not installed.  $2"
        return 1
    fi
}

MISSING=0
check_tool asciidoctor   "Install with: gem install asciidoctor"          || MISSING=1
check_tool pandoc        "Install with: brew install pandoc  (or see https://pandoc.org/installing.html)" || MISSING=1

# asciidoctor-diagram is a Ruby gem loaded at runtime; verify it's available
if ! ruby -e "require 'asciidoctor-diagram'" &>/dev/null 2>&1; then
    error "'asciidoctor-diagram' gem not found.  Install with: gem install asciidoctor-diagram"
    MISSING=1
fi

if [[ "${SKIP_API:-}" != "1" ]]; then
    check_tool npx "Install Node.js / npm to get npx (needed for OpenAPI docs)" || MISSING=1
fi

if [[ "$MISSING" -eq 1 ]]; then
    error "Aborting – please install missing tools first."
    exit 1
fi

# ── Prepare output directory ──────────────────────────────────────────────
info "Preparing output directory: ${SITE_DIR}"
rm -rf "${SITE_DIR}"
mkdir -p "${SITE_DIR}/architecture" \
         "${SITE_DIR}/api" \
         "${SITE_DIR}/images" \
         "${SITE_DIR}/assets"

# ── 1. Render Arc42 AsciiDoc (with PlantUML diagrams) ─────────────────────
info "Rendering Arc42 architecture documentation (AsciiDoc + PlantUML) …"
asciidoctor \
    -r asciidoctor-diagram \
    -a imagesdir=images \
    -a imagesoutdir="${SITE_DIR}/architecture/images" \
    -a toc=left \
    -a toclevels=3 \
    -a source-highlighter=highlight.js \
    -a icons=font \
    -a sectanchors \
    -a sectlinks \
    -a data-uri \
    -D "${SITE_DIR}/architecture" \
    "${DOCS_DIR}/arc42-template.adoc"

# Copy static images referenced by the AsciiDoc (drawio PNGs, arc42 logo, …)
cp -v "${DOCS_DIR}/images/"* "${SITE_DIR}/architecture/images/" 2>/dev/null || true
# Clean up asciidoctor-diagram cache (not needed in output)
rm -rf "${SITE_DIR}/architecture/.asciidoctor"
info "Arc42 docs → ${SITE_DIR}/architecture/arc42-template.html"

# ── 2. Render OpenAPI spec with Redoc ─────────────────────────────────────
if [[ "${SKIP_API:-}" != "1" ]]; then
    info "Rendering OpenAPI specification with Redoc …"
    npx --yes @redocly/cli build-docs \
        "${SPEC_FILE}" \
        --output "${SITE_DIR}/api/index.html" \
        --title "Push Gateway API"
    info "API docs → ${SITE_DIR}/api/index.html"
else
    warn "Skipping OpenAPI rendering (SKIP_API=1)."
fi

# ── 3. Convert Markdown files to HTML ─────────────────────────────────────
render_markdown() {
    local src="$1" dest="$2" title="$3"
    if [[ -f "$src" ]]; then
        info "Converting $(basename "$src") → $(basename "$dest")"
        pandoc "$src" \
            --from gfm \
            --to html5 \
            --standalone \
            --metadata title="$title" \
            --css assets/style.css \
            -o "$dest"
    else
        warn "Skipping $(basename "$src") – file not found."
    fi
}

render_markdown "${PROJECT_ROOT}/README.md"       "${SITE_DIR}/readme.html"        "Push Gateway"
render_markdown "${PROJECT_ROOT}/ReleaseNotes.md" "${SITE_DIR}/release-notes.html" "Release Notes"

# ── 4. Copy the raw OpenAPI YAML so it can be downloaded ──────────────────
if [[ -f "${SPEC_FILE}" ]]; then
    cp "${SPEC_FILE}" "${SITE_DIR}/api/push_gateway_openapi.yaml"
    info "Raw OpenAPI spec copied to ${SITE_DIR}/api/push_gateway_openapi.yaml"
fi

# ── 5. Create a minimal shared stylesheet ─────────────────────────────────
cat > "${SITE_DIR}/assets/style.css" << 'CSS'
/* Minimal docs-site stylesheet */
:root {
    --color-bg: #ffffff;
    --color-fg: #1a1a1a;
    --color-link: #0366d6;
    --color-border: #e1e4e8;
    --color-code-bg: #f6f8fa;
    --font-sans: -apple-system, BlinkMacSystemFont, "Segoe UI", Helvetica, Arial, sans-serif;
    --font-mono: "SFMono-Regular", Consolas, "Liberation Mono", Menlo, monospace;
}
* { box-sizing: border-box; }
body {
    font-family: var(--font-sans);
    color: var(--color-fg);
    background: var(--color-bg);
    line-height: 1.6;
    max-width: 960px;
    margin: 2rem auto;
    padding: 0 1rem;
}
a { color: var(--color-link); text-decoration: none; }
a:hover { text-decoration: underline; }
pre, code {
    font-family: var(--font-mono);
    font-size: 0.9em;
    background: var(--color-code-bg);
    border-radius: 4px;
}
pre { padding: 1em; overflow-x: auto; }
code { padding: 0.2em 0.4em; }
pre code { padding: 0; background: none; }
table { border-collapse: collapse; width: 100%; margin: 1em 0; }
th, td { border: 1px solid var(--color-border); padding: 0.5em 0.75em; text-align: left; }
th { background: var(--color-code-bg); }
h1, h2, h3 { margin-top: 1.5em; }
hr { border: none; border-top: 1px solid var(--color-border); margin: 2em 0; }
CSS

# ── 6. Generate index.html landing page ───────────────────────────────────
info "Generating index.html …"
cat > "${SITE_DIR}/index.html" << 'HTML'
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Push Gateway – Documentation</title>
    <link rel="stylesheet" href="assets/style.css">
    <style>
        .cards {
            display: grid;
            grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
            gap: 1.25rem;
            margin: 2rem 0;
        }
        .card {
            border: 1px solid var(--color-border);
            border-radius: 8px;
            padding: 1.25rem;
            transition: box-shadow 0.15s;
        }
        .card:hover { box-shadow: 0 2px 8px rgba(0,0,0,0.1); }
        .card h3 { margin-top: 0; }
        .card p { color: #586069; font-size: 0.95em; }
        .badge {
            display: inline-block;
            font-size: 0.75em;
            padding: 0.15em 0.5em;
            border-radius: 3px;
            background: var(--color-code-bg);
            border: 1px solid var(--color-border);
            color: #586069;
            margin-left: 0.5em;
            vertical-align: middle;
        }
    </style>
</head>
<body>
    <h1>Push Gateway</h1>
    <p>
        The Push Gateway handles the delivery of notifications from the TI context
        and abstracts communication with push providers (APNS, Firebase).
    </p>
    <hr>

    <div class="cards">
        <a href="architecture/arc42-template.html" class="card" style="text-decoration:none;color:inherit;">
            <h3>Architecture (Arc42)</h3>
            <p>Full architecture documentation including context diagrams, building block views,
               runtime views, deployment views, and quality requirements.</p>
            <span class="badge">AsciiDoc + PlantUML</span>
        </a>

        <a href="api/index.html" class="card" style="text-decoration:none;color:inherit;">
            <h3>API Reference</h3>
            <p>Interactive OpenAPI 3.1 documentation for the Push Gateway REST API,
               rendered with Redoc.</p>
            <span class="badge">OpenAPI 3.1</span>
        </a>

        <a href="readme.html" class="card" style="text-decoration:none;color:inherit;">
            <h3>README</h3>
            <p>Project overview, development setup, Docker Compose &amp; Kubernetes
               instructions, and configuration guide.</p>
            <span class="badge">Markdown</span>
        </a>

        <a href="release-notes.html" class="card" style="text-decoration:none;color:inherit;">
            <h3>Release Notes</h3>
            <p>Per-version release notes describing new features, fixes, and
               breaking changes.</p>
            <span class="badge">Markdown</span>
        </a>

        <a href="api/push_gateway_openapi.yaml" class="card" style="text-decoration:none;color:inherit;">
            <h3>OpenAPI Spec (raw)</h3>
            <p>Download the raw OpenAPI YAML specification file for code generation
               or import into tools like Postman.</p>
            <span class="badge">YAML</span>
        </a>
    </div>

    <hr>
    <footer style="color:#586069;font-size:0.85em;">
        <p>© gematik GmbH – generated documentation site. See
           <a href="https://github.com/gematik/push-gateway">github.com/gematik</a> for source.</p>
    </footer>
</body>
</html>
HTML

# ── Done ──────────────────────────────────────────────────────────────────
echo ""
info "Documentation site built successfully!"
info "Output: ${SITE_DIR}/"
echo ""
echo "  Contents:"
find "${SITE_DIR}" -type f | sort | while read -r f; do
    echo "    ${f#"${SITE_DIR}/"}"
done
echo ""
info "To preview locally:  cd ${SITE_DIR} && python3 -m http.server 8000"
