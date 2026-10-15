#!/usr/bin/env bash
set -euo pipefail
# Linux x86_64 bootstrap for the cloud workspace. Does not change source or locks.
repo_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
export NFC_TOOLS_ROOT=${NFC_TOOLS_ROOT:-/workspace/.tools}
mkdir -p "$NFC_TOOLS_ROOT" /workspace/.gradle /workspace/.android
python3 - <<'PY'
from pathlib import Path
import hashlib, os, shutil, subprocess, tarfile, urllib.parse, urllib.request, zipfile
p = Path(os.environ['NFC_TOOLS_ROOT'])
def fetch(url, dest):
    tmp = dest.with_suffix(dest.suffix + '.part')
    with urllib.request.urlopen(url, timeout=180) as src, tmp.open('wb') as out:
        shutil.copyfileobj(src, out)
    tmp.replace(dest)
def verified(url, dest, expected, algorithm='sha256'):
    if not dest.exists() or hashlib.new(algorithm, dest.read_bytes()).hexdigest() != expected:
        fetch(url, dest)
    if hashlib.new(algorithm, dest.read_bytes()).hexdigest() != expected:
        raise RuntimeError('Artifact checksum mismatch: ' + dest.name)
# Trust the machine's managed CA store, including its egress proxy CA. Never disable TLS.
proxy = urllib.parse.urlsplit(os.environ.get('HTTPS_PROXY', ''))
props = []
if proxy.hostname:
    for scheme in ['http', 'https']:
        props += [f'systemProp.{scheme}.proxyHost={proxy.hostname}', f'systemProp.{scheme}.proxyPort={proxy.port or 80}']
if Path('/etc/ssl/certs/java/cacerts').is_file():
    props += ['systemProp.javax.net.ssl.trustStore=/etc/ssl/certs/java/cacerts']
# Only maintain our own settings; preserve other user Gradle properties.
f = Path('/workspace/.gradle/gradle.properties')
previous = f.read_text().splitlines() if f.exists() else []
keys = {s.split('=')[0] for s in props}
f.write_text('\n'.join([s for s in previous if s.split('=')[0] not in keys] + props) + '\n')
jdk_path = p / 'jdk-path'
if not jdk_path.exists() or not (Path(jdk_path.read_text().strip()) / 'bin/javac').is_file():
    base = 'https://download.oracle.com/java/21/latest/jdk-21_linux-x64_bin.tar.gz'
    checksum = urllib.request.urlopen(base + '.sha256', timeout=60).read().decode().strip()
    archive = p / 'jdk.tar.gz'
    verified(base, archive, checksum)
    with tarfile.open(archive) as t:
        root = t.getnames()[0].split('/')[0]
        t.extractall(p, filter='data')
    jdk_path.write_text(str(p / root))
sdk = p / 'android-sdk'
tools = sdk / 'cmdline-tools/19.0'
if not (tools / 'bin/sdkmanager').exists():
    archive = p / 'android-tools.zip'
    verified('https://dl.google.com/android/repository/commandlinetools-linux-13114758_latest.zip', archive,
             '5fdcc763663eefb86a5b8879697aa6088b041e70', 'sha1')
    with zipfile.ZipFile(archive) as z:
        for info in z.infolist():
            name = info.filename.removeprefix('cmdline-tools/')
            if not name: continue
            dest = tools / name
            if info.is_dir(): dest.mkdir(parents=True, exist_ok=True)
            else:
                dest.parent.mkdir(parents=True, exist_ok=True)
                dest.write_bytes(z.read(info))
                if info.external_attr >> 16: dest.chmod(info.external_attr >> 16)
e = dict(os.environ, ANDROID_USER_HOME='/workspace/.android', ANDROID_PREFS_ROOT='/workspace')
java_options = []
if proxy.hostname:
    java_options += [f'-Dhttps.proxyHost={proxy.hostname}', f'-Dhttps.proxyPort={proxy.port or 80}']
if Path('/etc/ssl/certs/java/cacerts').is_file():
    java_options += ['-Djavax.net.ssl.trustStore=/etc/ssl/certs/java/cacerts']
e['JAVA_TOOL_OPTIONS'] = ' '.join(java_options)
if not all(x.exists() for x in [sdk/'platforms/android-35/android.jar', sdk/'build-tools/35.0.0/aapt2', sdk/'platform-tools/adb']):
    subprocess.run([str(tools/'bin/sdkmanager'), '--sdk_root='+str(sdk), 'platforms;android-35', 'build-tools;35.0.0', 'platform-tools'],
                   input='y\n'*30, text=True, env=e, check=True)
PY
export JAVA_HOME=$(cat "$NFC_TOOLS_ROOT/jdk-path")
export ANDROID_HOME="$NFC_TOOLS_ROOT/android-sdk"
export ANDROID_USER_HOME=/workspace/.android
export ANDROID_PREFS_ROOT=/workspace
export GRADLE_USER_HOME=/workspace/.gradle
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$PATH"
cd "$repo_dir"
# Respect an existing local Android Studio configuration.
if [[ ! -f local.properties ]]; then printf 'sdk.dir=%s\n' "$ANDROID_HOME" > local.properties; fi
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --console=plain
