import { createServer } from "node:http";
import { readFileSync, existsSync, statSync } from "node:fs";
import { extname, resolve, join } from "node:path";
import { spawn } from "node:child_process";

const PORT = 17890;
const PREVIEWABLE = new Set([".md", ".html", ".htm", ".csv", ".drawio", ".svg", ".png", ".jpg", ".jpeg", ".gif", ".json", ".xml", ".yaml", ".yml", ".css", ".js", ".ts", ".py", ".kt", ".java", ".swift"]);

const MIME = {
  ".html": "text/html", ".htm": "text/html", ".md": "text/html",
  ".css": "text/css", ".js": "text/javascript", ".json": "application/json",
  ".svg": "image/svg+xml", ".png": "image/png", ".jpg": "image/jpeg",
  ".csv": "text/csv", ".xml": "application/xml", ".yaml": "text/yaml",
  ".py": "text/plain", ".kt": "text/plain", ".java": "text/plain", ".swift": "text/plain",
};

function renderMarkdown(src) {
  return `<!DOCTYPE html><html><head><meta charset="utf-8"><title>Preview</title>
<style>body{background:#000;color:#eee;font-family:system-ui;max-width:900px;margin:0 auto;padding:20px}
code,pre{background:#1a1a1a;padding:2px 6px;border-radius:4px}
pre{padding:12px;overflow-x:auto}
a{color:#FF4081}img{max-width:100%}
.badge{position:fixed;top:10px;right:10px;background:#FF4081;color:#000;padding:4px 12px;border-radius:12px;font-size:12px;font-weight:bold}
</style></head><body><div class="badge">Live Preview</div>
<script src="https://cdn.jsdelivr.net/npm/marked/marked.min.js"></script>
<script>document.body.innerHTML+='<div>'+marked.parse(${JSON.stringify(src)})+'</div>'</script>
</body></html>`;
}

function renderCode(src, ext) {
  return `<!DOCTYPE html><html><head><meta charset="utf-8"><title>Preview</title>
<style>body{background:#000;color:#eee;font-family:monospace;padding:20px;white-space:pre-wrap}
.badge{position:fixed;top:10px;right:10px;background:#FF4081;color:#000;padding:4px 12px;border-radius:12px;font-size:12px;font-weight:bold}
</style></head><body><div class="badge">${ext}</div><pre>${src.replace(/</g,"&lt;")}</pre></body></html>`;
}

function renderImage(filePath, mime) {
  const data = readFileSync(filePath).toString("base64");
  return `<!DOCTYPE html><html><head><style>body{background:#000;margin:0;display:flex;justify-content:center;align-items:center;min-height:100vh}
.badge{position:fixed;top:10px;right:10px;background:#FF4081;color:#000;padding:4px 12px;border-radius:12px;font-size:12px;font-weight:bold}
</style></head><body><div class="badge">image</div><img src="data:${mime};base64,${data}" style="max-width:100%;max-height:100vh"></body></html>`;
}

let server;
let projectDirectory = "";
function ensureServer() {
  if (server) return;
  server = createServer((req, res) => {
    const url = new URL(req.url, `http://localhost:${PORT}`);
    const filePath = url.searchParams.get("file");
    if (!filePath) { res.writeHead(400); res.end("Missing file"); return; }
    const full = resolve(projectDirectory, filePath);
    if (!existsSync(full)) { res.writeHead(404); res.end(`Not found: ${full} (dir=${projectDirectory})`); return; }
    const ext = extname(full).toLowerCase();
    if (!PREVIEWABLE.has(ext)) { res.writeHead(415); res.end("Not previewable"); return; }
    const mime = MIME[ext] || "text/plain";
    try {
      let html;
      if ([".png",".jpg",".jpeg",".gif",".svg"].includes(ext)) {
        html = renderImage(full, mime);
      } else if (ext === ".md") {
        html = renderMarkdown(readFileSync(full, "utf-8"));
      } else {
        html = renderCode(readFileSync(full, "utf-8"), ext);
      }
      res.writeHead(200, { "Content-Type": "text/html" });
      res.end(html);
    } catch (e) { res.writeHead(500); res.end(e.message); }
  });
  server.listen(PORT, "localhost", () => {});
}

export const PreviewPlugin = async ({ project, client, $ }) => {
  return {
    tool: {
      preview: {
        description: "Open a preview of a file in the browser. Supports Markdown, HTML, CSV, code, images.",
        args: {
          file: { type: "string", description: "File path relative to project root, or absolute path" },
        },
        async execute(args, context) {
          const directory = context?.directory || project?.path || process.cwd();
          projectDirectory = directory;
          ensureServer();
          const filePath = resolve(directory, args.file);
          if (!existsSync(filePath)) return `File not found: ${filePath} (dir=${directory})`;
          if (!statSync(filePath).isFile()) return `Not a file: ${args.file}`;
          const ext = extname(filePath).toLowerCase();
          if (!PREVIEWABLE.has(ext)) return `Cannot preview ${ext} files`;
          const rel = filePath.startsWith(directory) ? filePath.slice(directory.length + 1).replace(/\\/g, "/") : filePath.replace(/\\/g, "/");
          const url = `http://localhost:${PORT}/?file=${encodeURIComponent(rel)}`;
          try { spawn("cmd", ["/c", "start", url], { stdio: "ignore", detached: true }).unref(); } catch {}
          return `Preview opened: ${url}`;
        },
      },
    },
  };
};

export default PreviewPlugin;
