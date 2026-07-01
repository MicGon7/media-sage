#!/usr/bin/env python3
"""
Generate a GitHub App installation token and print it to stdout.

Reads from environment:
  GITHUB_APP_ID                  - numeric App ID (e.g. "123456")
  GITHUB_APP_INSTALLATION_ID     - installation ID for this repo
  GITHUB_APP_PRIVATE_KEY_BASE64  - base64-encoded RSA private key (PEM)

Prints the installation token to stdout (no trailing newline).
Exits with code 1 and an error message to stderr if any input is missing or the
API call fails.

Uses only Python stdlib + the openssl CLI (already present in the Docker image)
for RSA signing — no third-party packages required.
"""
import base64
import json
import os
import subprocess
import sys
import tempfile
import time
import urllib.error
import urllib.request


def b64url(data: bytes) -> str:
    return base64.urlsafe_b64encode(data).rstrip(b"=").decode()


def generate_jwt(app_id: str, private_key_pem: str) -> str:
    now = int(time.time())
    header = b64url(json.dumps({"alg": "RS256", "typ": "JWT"}).encode())
    payload = b64url(
        json.dumps({"iss": app_id, "iat": now, "exp": now + 540}).encode()
    )
    signing_input = f"{header}.{payload}"

    # Write the PEM key to a temp file so openssl can read it.
    # openssl dgst handles both PKCS#1 and PKCS#8 PEM keys natively.
    with tempfile.NamedTemporaryFile(mode="w", suffix=".pem", delete=False) as f:
        f.write(private_key_pem)
        key_path = f.name

    try:
        result = subprocess.run(
            ["openssl", "dgst", "-sha256", "-sign", key_path, "-binary"],
            input=signing_input.encode(),
            capture_output=True,
            check=True,
        )
    finally:
        os.unlink(key_path)

    return f"{signing_input}.{b64url(result.stdout)}"


def get_installation_token(jwt: str, installation_id: str) -> str:
    url = (
        f"https://api.github.com/app/installations/{installation_id}/access_tokens"
    )
    req = urllib.request.Request(url, method="POST")
    req.add_header("Authorization", f"Bearer {jwt}")
    req.add_header("Accept", "application/vnd.github+json")
    req.add_header("X-GitHub-Api-Version", "2022-11-28")
    with urllib.request.urlopen(req) as resp:
        data = json.loads(resp.read())
    return data["token"]


def main() -> None:
    app_id = os.environ.get("GITHUB_APP_ID", "").strip()
    installation_id = os.environ.get("GITHUB_APP_INSTALLATION_ID", "").strip()
    private_key_b64 = os.environ.get("GITHUB_APP_PRIVATE_KEY_BASE64", "").strip()

    if not app_id or not installation_id or not private_key_b64:
        print(
            "ERROR: GITHUB_APP_ID, GITHUB_APP_INSTALLATION_ID, and "
            "GITHUB_APP_PRIVATE_KEY_BASE64 must all be set.",
            file=sys.stderr,
        )
        sys.exit(1)

    try:
        private_key_pem = base64.b64decode(private_key_b64).decode()
        jwt = generate_jwt(app_id, private_key_pem)
        token = get_installation_token(jwt, installation_id)
        print(token, end="")
    except subprocess.CalledProcessError as exc:
        print(
            f"ERROR: openssl signing failed: {exc.stderr.decode(errors='replace')}",
            file=sys.stderr,
        )
        sys.exit(1)
    except urllib.error.HTTPError as exc:
        body = exc.read().decode(errors="replace")
        print(f"ERROR: GitHub API returned {exc.code}: {body}", file=sys.stderr)
        sys.exit(1)
    except Exception as exc:  # noqa: BLE001
        print(f"ERROR: {exc}", file=sys.stderr)
        sys.exit(1)


if __name__ == "__main__":
    main()
