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
"""
import base64
import json
import os
import sys
import time
import urllib.request
import urllib.error


def b64url(data: bytes) -> str:
    return base64.urlsafe_b64encode(data).rstrip(b"=").decode()


def generate_jwt(app_id: str, private_key_pem: str) -> str:
    from cryptography.hazmat.primitives import serialization, hashes
    from cryptography.hazmat.primitives.asymmetric import padding

    key = serialization.load_pem_private_key(private_key_pem.encode(), password=None)
    now = int(time.time())
    header = b64url(json.dumps({"alg": "RS256", "typ": "JWT"}).encode())
    payload = b64url(
        json.dumps({"iss": app_id, "iat": now, "exp": now + 540}).encode()
    )
    signing_input = f"{header}.{payload}".encode()
    sig = key.sign(signing_input, padding.PKCS1v15(), hashes.SHA256())
    return f"{header}.{payload}.{b64url(sig)}"


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
    except urllib.error.HTTPError as exc:
        body = exc.read().decode(errors="replace")
        print(f"ERROR: GitHub API returned {exc.code}: {body}", file=sys.stderr)
        sys.exit(1)
    except Exception as exc:  # noqa: BLE001
        print(f"ERROR: {exc}", file=sys.stderr)
        sys.exit(1)


if __name__ == "__main__":
    main()
