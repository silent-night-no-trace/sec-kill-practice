from pathlib import Path
import subprocess
import os

ROOT = Path(r"C:\Users\leon\Desktop\seck")
ENV = os.environ.copy()
ENV["GIT_TERMINAL_PROMPT"] = "0"

def run(cmd, cwd=None):
    return subprocess.run(cmd, cwd=cwd, capture_output=True, text=True, env=ENV)

print("---GIT-STATE---")
print(run(["git", "status", "--short"], cwd=ROOT).stdout, end="")
print("---BRANCH---")
print(run(["git", "branch", "--show-current"], cwd=ROOT).stdout, end="")
print("---HEAD---")
head = run(["git", "rev-parse", "--verify", "HEAD"], cwd=ROOT)
print(head.stdout.strip() if head.returncode == 0 else "NO_COMMITS_YET")
print("---REMOTE---")
print(run(["git", "remote", "-v"], cwd=ROOT).stdout, end="")
print("---SSH---")
ssh = run(["ssh", "-T", "git@github.com"])
print((ssh.stdout + ssh.stderr).strip())
print("---LS-REMOTE---")
lsr = run(["git", "ls-remote", "origin"], cwd=ROOT)
print((lsr.stdout + lsr.stderr).strip())
