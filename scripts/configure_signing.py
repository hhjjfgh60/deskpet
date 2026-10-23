#!/usr/bin/env python3
"""Load the fixed signing key from one GitHub Actions Secret; never create a replacement key."""
import base64
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]

def configure():
    value = os.environ.get("DESKPET_SIGNING_BUNDLE", "")
    if not value:
        raise ValueError("Missing GitHub Actions Secret DESKPET_SIGNING_BUNDLE. Add the supplied private JSON in repository Settings > Secrets and variables > Actions. No installable APK will be signed with a random replacement key.")
    bundle = json.loads(value)
    fields = ("keystoreBase64", "storePassword", "keyPassword", "keyAlias")
    for field in fields:
        if not isinstance(bundle.get(field), str) or not bundle[field] or '\n' in bundle[field] or '\r' in bundle[field]:
            raise ValueError(f"Invalid signing bundle field: {field}")
    if os.environ.get("GITHUB_ACTIONS") == "true":
        for field in fields:
            print("::add-mask::" + bundle[field], flush=True)
    folder = Path(os.environ["RUNNER_TEMP"]) / "deskpet-signing"
    folder.mkdir(mode=0o700, parents=True, exist_ok=True)
    key = folder / "fixed.p12"
    key.write_bytes(base64.b64decode(bundle["keystoreBase64"], validate=True))
    key.chmod(0o600)
    process_env = os.environ.copy()
    process_env["DESKPET_CERT_PASSWORD"] = bundle["storePassword"]
    result = subprocess.run(["keytool", "-exportcert", "-keystore", str(key),
        "-storepass:env", "DESKPET_CERT_PASSWORD", "-alias", bundle["keyAlias"]],
        capture_output=True, env=process_env)
    if result.returncode:
        raise ValueError("Signing keystore could not be opened; private diagnostic values suppressed")
    expected = (ROOT / "signing-certificate.sha256").read_text().strip()
    if hashlib.sha256(result.stdout).hexdigest() != expected:
        raise ValueError("Signing certificate differs from the pinned fixed certificate; refusing to sign an incompatible update")
    variables = {
        "DESKPET_SIGNING_STORE_FILE": str(key),
        "DESKPET_SIGNING_STORE_PASSWORD": bundle["storePassword"],
        "DESKPET_SIGNING_KEY_PASSWORD": bundle["keyPassword"],
        "DESKPET_SIGNING_KEY_ALIAS": bundle["keyAlias"],
    }
    with open(os.environ["GITHUB_ENV"], "a") as output:
        for name, secret in variables.items():
            output.write(f"{name}={secret}\n")
    print("Fixed signing certificate verified. Private key remains in runner temporary storage only.")

if __name__ == "__main__":
    try:
        configure()
    except Exception as error:
        # Do not include JSON, passwords, key bytes or raw keytool stderr.
        if isinstance(error, ValueError) and not isinstance(error, json.JSONDecodeError):
            print(f"Signing setup failed: {error}", file=sys.stderr)
        else:
            print("Signing setup failed: invalid bundle or runner environment (private details suppressed)", file=sys.stderr)
        sys.exit(1)
