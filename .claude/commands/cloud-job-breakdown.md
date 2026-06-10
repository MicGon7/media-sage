Extract turn count, cost, and duration from a Cloud Run job JSONL file.

Reads only the `result` entry — `num_turns`, `total_cost_usd`, and `duration_ms` are already
computed there. No per-turn enumeration.

**Usage:** called with a path argument, or defaults to `/tmp/worker-run.jsonl`.

```bash
python3 -c "
import json, sys
path = sys.argv[1] if len(sys.argv) > 1 else '/tmp/worker-run.jsonl'
for line in open(path):
    line = line.strip()
    if not line:
        continue
    e = json.loads(line)
    if e.get('type') == 'result':
        t = e.get('num_turns', '?')
        c = e.get('total_cost_usd', 0)
        d = e.get('duration_ms', 0)
        print(f'{t} turns | \${c:.4f} | {d//60000}m {(d%60000)//1000:02d}s')
        break
" "$@" 2>/dev/null || echo "metrics unavailable"
```

Output format: `{N} turns | ${cost} | {Xm Ys}`

If the file is not found or contains no `result` entry, prints `metrics unavailable`.
