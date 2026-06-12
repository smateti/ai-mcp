#!/usr/bin/env python3
"""
export_install_certs.py
=======================
Fetches the TLS certificate chain from one or more HTTPS endpoints,
writes everything into a single PEM bundle, and (optionally) installs
the certificates into the local trust store on Linux or Windows, and
into the Java JVM truststore (cacerts) so Maven/Java clients trust the
same endpoints.

Typical use: internal endpoints (GitLab, Nexus, OpenShift API, vaults)
signed by an internal CA cause "SSL: CERTIFICATE_VERIFY_FAILED" /
"PKIX path building failed" errors. This script collects what those
endpoints actually serve and registers it as trusted.

What it does
------------
1. EXPORT: for each host[:port] (default port 443) it retrieves the
   full certificate chain presented by the server:
     * preferred: `openssl s_client -showcerts` (returns the whole
       chain; openssl is present on practically every Linux box and
       in "Git for Windows"),
     * fallback: Python's ssl module (Python 3.13+ returns the chain;
       older versions return only the leaf certificate - a warning is
       printed, because trusting only a leaf breaks on renewal; prefer
       having openssl available).
   Certificates are de-duplicated across endpoints by SHA-256
   fingerprint and written to one bundle PEM (default: ca-bundle.pem),
   each block preceded by a comment with subject/issuer/expiry.

2. INSTALL (--install, needs root/Administrator):
     * Linux, Debian/Ubuntu family : copy to
         /usr/local/share/ca-certificates/<name>.crt
       then run `update-ca-certificates`.
     * Linux, RHEL/CentOS/Fedora/UBI family : copy to
         /etc/pki/ca-trust/source/anchors/<name>.pem
       then run `update-ca-trust extract`.
       (The family is auto-detected by which directory exists.)
     * Windows : each certificate is imported into the machine Root
       store with `certutil -addstore -f Root <cert>` (one file per
       certificate, because certutil only reads the first cert of a
       multi-cert PEM).

3. JAVA (--java, optional): imports each certificate into the default
   JVM truststore via
       keytool -importcert -cacerts -storepass changeit -noprompt
   so Maven/JAX-RS clients on the same machine trust the endpoints
   too. Use --java-keytool / --java-storepass to target a specific JDK
   or a non-default password. Aliases are derived from the certificate
   fingerprint, so re-running is idempotent (existing aliases are
   replaced).

Safety notes
------------
* Export performs NO verification (that is the point - the chain is
  unknown/untrusted yet). Review the printed subjects before running
  --install in production: only register certificates you recognize.
* Ideally install only the ROOT/intermediate CA certificates, not leaf
  certs. Use --ca-only to filter the bundle to CA certificates
  (CA:TRUE in Basic Constraints, detected via openssl when available).
* Installing trust store entries is system-wide: run with appropriate
  privileges and change control.

Usage
-----
    # Export only - inspect first (no privileges needed)
    python export_install_certs.py --endpoints gitlab-old.example.com gitlab-new.example.com:8443 nexus.example.com

    # Endpoints from a file (one host[:port] per line, # comments ok)
    python export_install_certs.py --endpoints-file endpoints.txt -o ca-bundle.pem

    # Export + install into the OS trust store (run as root/Administrator)
    sudo python3 export_install_certs.py --endpoints-file endpoints.txt --install

    # Also import into the default JVM truststore for Maven
    sudo python3 export_install_certs.py --endpoints-file endpoints.txt --install --java

    # Install a bundle you exported earlier elsewhere (no fetching)
    sudo python3 export_install_certs.py --from-pem ca-bundle.pem --install
"""

import argparse
import hashlib
import os
import platform
import re
import shutil
import socket
import ssl
import subprocess
import sys
import tempfile

PEM_RE = re.compile(
    r"-----BEGIN CERTIFICATE-----.*?-----END CERTIFICATE-----", re.S)


# --------------------------------------------------------------------------
# Export
# --------------------------------------------------------------------------

def fetch_chain_openssl(host: str, port: int, timeout: int = 15) -> list[str] | None:
    """Fetch the full certificate chain using `openssl s_client -showcerts`.

    Args:
        host: Server hostname (also sent as SNI via -servername).
        port: TCP port.
        timeout: Seconds before giving up.

    Returns:
        List of PEM certificate strings (leaf first), or None if the
        openssl binary is not available. Raises RuntimeError on
        connection failure.
    """
    openssl = shutil.which("openssl")
    if not openssl:
        return None
    cmd = [openssl, "s_client", "-connect", f"{host}:{port}",
           "-servername", host, "-showcerts"]
    try:
        res = subprocess.run(cmd, input="", capture_output=True, text=True,
                             timeout=timeout, check=False)
    except subprocess.TimeoutExpired:
        raise RuntimeError(f"openssl timed out connecting to {host}:{port}")
    certs = PEM_RE.findall(res.stdout)
    if not certs:
        raise RuntimeError(
            f"no certificates received from {host}:{port} "
            f"({res.stderr.strip().splitlines()[-1] if res.stderr.strip() else 'no error output'})")
    return certs


def fetch_chain_python(host: str, port: int, timeout: int = 15) -> list[str]:
    """Fetch certificates with the ssl module (no verification).

    On Python 3.13+ the full unverified chain is returned; on older
    versions only the LEAF certificate is available - a warning is
    printed in that case.

    Args:
        host: Server hostname (used for SNI).
        port: TCP port.
        timeout: Socket timeout in seconds.

    Returns:
        List of PEM certificate strings.

    Raises:
        OSError / ssl.SSLError on connection problems.
    """
    ctx = ssl.create_default_context()
    ctx.check_hostname = False
    ctx.verify_mode = ssl.CERT_NONE  # we are *collecting*, not verifying
    with socket.create_connection((host, port), timeout=timeout) as sock:
        with ctx.wrap_socket(sock, server_hostname=host) as tls:
            if hasattr(tls, "get_unverified_chain"):          # Python >= 3.13
                chain = tls.get_unverified_chain() or []
                return [c.public_bytes(ssl.ENCODING_PEM)      # type: ignore[attr-defined]
                        if hasattr(c, "public_bytes")
                        else ssl.DER_cert_to_PEM_cert(c) for c in chain]
            der = tls.getpeercert(binary_form=True)
            print(f"  WARNING: openssl not found and Python < 3.13 - only the "
                  f"LEAF certificate of {host}:{port} could be fetched. "
                  f"Install openssl to capture the full chain (recommended).")
            return [ssl.DER_cert_to_PEM_cert(der)]


def cert_info(pem: str) -> dict:
    """Extract subject / issuer / expiry / CA flag / fingerprint for a PEM cert.

    Uses `openssl x509` when available; otherwise falls back to a
    fingerprint-only summary (info fields show 'n/a').

    Args:
        pem: One PEM certificate block.

    Returns:
        {"fingerprint": str, "subject": str, "issuer": str,
         "not_after": str, "is_ca": bool|None}
    """
    fp = hashlib.sha256(pem.encode()).hexdigest()[:16]
    info = {"fingerprint": fp, "subject": "n/a", "issuer": "n/a",
            "not_after": "n/a", "is_ca": None}
    openssl = shutil.which("openssl")
    if not openssl:
        return info
    res = subprocess.run(
        [openssl, "x509", "-noout", "-subject", "-issuer", "-enddate", "-text"],
        input=pem, capture_output=True, text=True, check=False)
    out = res.stdout
    m = re.search(r"^subject=(.*)$", out, re.M)
    if m:
        info["subject"] = m.group(1).strip()
    m = re.search(r"^issuer=(.*)$", out, re.M)
    if m:
        info["issuer"] = m.group(1).strip()
    m = re.search(r"^notAfter=(.*)$", out, re.M)
    if m:
        info["not_after"] = m.group(1).strip()
    info["is_ca"] = "CA:TRUE" in out
    return info


def parse_endpoint(spec: str) -> tuple[str, int]:
    """Parse 'host' or 'host:port' (default port 443)."""
    if ":" in spec:
        host, port = spec.rsplit(":", 1)
        return host.strip(), int(port)
    return spec.strip(), 443


def export_bundle(endpoints: list[str], out_path: str, ca_only: bool) -> list[dict]:
    """Fetch chains from all endpoints, de-duplicate, write the PEM bundle.

    Args:
        endpoints: 'host[:port]' strings.
        out_path: Bundle file to write.
        ca_only: Keep only CA certificates (requires openssl to detect;
            certs whose CA status is unknown are kept with a warning).

    Returns:
        List of cert_info() dicts for every certificate written.
    """
    seen: dict[str, dict] = {}
    for spec in endpoints:
        host, port = parse_endpoint(spec)
        print(f"Fetching certificate chain from {host}:{port} ...")
        try:
            chain = fetch_chain_openssl(host, port)
            if chain is None:
                chain = fetch_chain_python(host, port)
        except (RuntimeError, OSError, ssl.SSLError) as exc:
            print(f"  ERROR: {exc} - skipping this endpoint.")
            continue
        for pem in chain:
            info = cert_info(pem)
            if ca_only and info["is_ca"] is False:
                print(f"  skipping leaf (--ca-only): {info['subject']}")
                continue
            if info["fingerprint"] not in seen:
                info["pem"] = pem.strip()
                seen[info["fingerprint"]] = info
                print(f"  + {info['subject']}  (expires {info['not_after']}, "
                      f"CA={info['is_ca']})")
            else:
                print(f"  = duplicate, already collected: {info['subject']}")

    if not seen:
        sys.exit("No certificates collected - nothing to write.")

    with open(out_path, "w", encoding="utf-8") as fh:
        for info in seen.values():
            fh.write(f"# Subject : {info['subject']}\n")
            fh.write(f"# Issuer  : {info['issuer']}\n")
            fh.write(f"# Expires : {info['not_after']}\n")
            fh.write(f"# SHA256  : {info['fingerprint']}\n")
            fh.write(info["pem"] + "\n\n")
    print(f"\nWrote {len(seen)} unique certificate(s) to {out_path}")
    return list(seen.values())


# --------------------------------------------------------------------------
# Install - system trust stores
# --------------------------------------------------------------------------

def install_linux(certs: list[dict]) -> None:
    """Install certificates into the Linux system trust store.

    Auto-detects the distro family:
      * /usr/local/share/ca-certificates  -> Debian/Ubuntu
        (files must end in .crt; refresh with update-ca-certificates)
      * /etc/pki/ca-trust/source/anchors  -> RHEL/CentOS/Fedora/UBI
        (refresh with update-ca-trust extract)

    Args:
        certs: cert_info dicts (with 'pem') to install.

    Raises:
        SystemExit: if neither trust directory exists or refresh fails.
    """
    if os.path.isdir("/usr/local/share/ca-certificates"):
        target_dir, ext, refresh = ("/usr/local/share/ca-certificates", ".crt",
                                    ["update-ca-certificates"])
    elif os.path.isdir("/etc/pki/ca-trust/source/anchors"):
        target_dir, ext, refresh = ("/etc/pki/ca-trust/source/anchors", ".pem",
                                    ["update-ca-trust", "extract"])
    else:
        sys.exit("Unsupported Linux trust layout: neither "
                 "/usr/local/share/ca-certificates nor "
                 "/etc/pki/ca-trust/source/anchors exists.")

    for info in certs:
        name = f"custom-{info['fingerprint']}{ext}"
        path = os.path.join(target_dir, name)
        with open(path, "w", encoding="utf-8") as fh:
            fh.write(info["pem"] + "\n")
        print(f"  installed {path}  ({info['subject']})")

    print(f"Refreshing trust store: {' '.join(refresh)}")
    res = subprocess.run(refresh, capture_output=True, text=True, check=False)
    print(res.stdout.strip() or res.stderr.strip())
    if res.returncode != 0:
        sys.exit("Trust store refresh FAILED - are you running as root?")


def install_windows(certs: list[dict]) -> None:
    """Install certificates into the Windows machine Root store.

    Writes each certificate to its own temporary .cer file (certutil
    imports only the first certificate of a multi-cert file) and runs:
        certutil -addstore -f Root <file>

    Must be run from an elevated (Administrator) prompt.

    Args:
        certs: cert_info dicts (with 'pem') to install.
    """
    certutil = shutil.which("certutil")
    if not certutil:
        sys.exit("certutil.exe not found on PATH - cannot install on Windows.")
    failures = 0
    for info in certs:
        with tempfile.NamedTemporaryFile("w", suffix=".cer", delete=False,
                                         encoding="utf-8") as tmp:
            tmp.write(info["pem"] + "\n")
            tmp_path = tmp.name
        try:
            res = subprocess.run([certutil, "-addstore", "-f", "Root", tmp_path],
                                 capture_output=True, text=True, check=False)
            if res.returncode == 0:
                print(f"  installed into Root store: {info['subject']}")
            else:
                failures += 1
                print(f"  FAILED: {info['subject']} -> "
                      f"{(res.stderr or res.stdout).strip()[:200]}")
        finally:
            os.unlink(tmp_path)
    if failures:
        sys.exit(f"{failures} certificate(s) failed - run from an "
                 f"elevated (Administrator) command prompt.")


# --------------------------------------------------------------------------
# Install - Java truststore (for Maven / JAX-RS clients)
# --------------------------------------------------------------------------

def install_java(certs: list[dict], keytool: str, storepass: str) -> None:
    """Import certificates into the JVM default truststore (cacerts).

    Idempotent: an existing alias is deleted and re-imported, so the
    script can be re-run after certificate rotation.

    Args:
        certs: cert_info dicts (with 'pem') to import.
        keytool: Path to keytool (default resolved from PATH/JAVA_HOME).
        storepass: Truststore password (default JVM password 'changeit').
    """
    kt = keytool or shutil.which("keytool") or (
        os.path.join(os.environ["JAVA_HOME"], "bin", "keytool")
        if os.environ.get("JAVA_HOME") else None)
    if not kt or not (shutil.which(kt) or os.path.exists(kt)):
        sys.exit("keytool not found - set --java-keytool or JAVA_HOME.")

    for info in certs:
        alias = f"custom-{info['fingerprint']}"
        with tempfile.NamedTemporaryFile("w", suffix=".pem", delete=False,
                                         encoding="utf-8") as tmp:
            tmp.write(info["pem"] + "\n")
            tmp_path = tmp.name
        try:
            subprocess.run([kt, "-delete", "-cacerts", "-alias", alias,
                            "-storepass", storepass],
                           capture_output=True, text=True, check=False)
            res = subprocess.run([kt, "-importcert", "-cacerts", "-noprompt",
                                  "-trustcacerts", "-alias", alias,
                                  "-file", tmp_path, "-storepass", storepass],
                                 capture_output=True, text=True, check=False)
            if res.returncode == 0:
                print(f"  imported into JVM cacerts as '{alias}': "
                      f"{info['subject']}")
            else:
                print(f"  FAILED ({info['subject']}): "
                      f"{(res.stderr or res.stdout).strip()[:200]}")
        finally:
            os.unlink(tmp_path)


# --------------------------------------------------------------------------

def main() -> None:
    parser = argparse.ArgumentParser(
        description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    src = parser.add_mutually_exclusive_group(required=True)
    src.add_argument("--endpoints", nargs="+", metavar="HOST[:PORT]",
                     help="Endpoints to fetch certificates from (default port 443).")
    src.add_argument("--endpoints-file",
                     help="File with one host[:port] per line ('#' comments ok).")
    src.add_argument("--from-pem", metavar="BUNDLE",
                     help="Skip fetching; install certificates from an existing "
                          "PEM bundle (e.g. exported on another machine).")
    parser.add_argument("-o", "--output", default="ca-bundle.pem",
                        help="PEM bundle to write (default: ca-bundle.pem).")
    parser.add_argument("--ca-only", action="store_true",
                        help="Keep only CA certificates (recommended for "
                             "trust-store installs; needs openssl).")
    parser.add_argument("--install", action="store_true",
                        help="Install into the OS trust store "
                             "(Linux: root, Windows: Administrator).")
    parser.add_argument("--java", action="store_true",
                        help="Also import into the JVM default truststore "
                             "(cacerts) via keytool - for Maven/Java clients.")
    parser.add_argument("--java-keytool", default=None,
                        help="Path to keytool (default: PATH, then JAVA_HOME).")
    parser.add_argument("--java-storepass", default="changeit",
                        help="cacerts password (default: changeit).")
    args = parser.parse_args()

    if args.from_pem:
        with open(args.from_pem, encoding="utf-8") as fh:
            blocks = PEM_RE.findall(fh.read())
        if not blocks:
            sys.exit(f"No certificates found in {args.from_pem}")
        certs = []
        for pem in blocks:
            info = cert_info(pem)
            info["pem"] = pem.strip()
            certs.append(info)
            print(f"loaded: {info['subject']} (expires {info['not_after']})")
    else:
        if args.endpoints_file:
            with open(args.endpoints_file, encoding="utf-8") as fh:
                endpoints = [ln.strip() for ln in fh
                             if ln.strip() and not ln.strip().startswith("#")]
        else:
            endpoints = args.endpoints
        certs = export_bundle(endpoints, args.output, args.ca_only)

    if args.install:
        system = platform.system()
        print(f"\nInstalling into {system} trust store ...")
        if system == "Linux":
            install_linux(certs)
        elif system == "Windows":
            install_windows(certs)
        else:
            sys.exit(f"--install not supported on {system} by this script.")
        print("System trust store updated.")

    if args.java:
        print("\nImporting into JVM truststore (cacerts) ...")
        install_java(certs, args.java_keytool, args.java_storepass)

    if not args.install and not args.java:
        print("\nExport only (no --install/--java given). Review the bundle, "
              "then re-run with --install [--java] under root/Administrator.")


if __name__ == "__main__":
    main()
