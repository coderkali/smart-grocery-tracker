# Setting Up Maven Credentials for Spring AI Artifacts

## Problem
Maven cannot download `spring-ai-*` artifacts from `https://repo.spring.io/release` (returns HTTP 401).

## Solution: Add credentials to Maven

### Step 1: Get credentials from your admin/infra

Contact your **DevOps / Infrastructure team** or **artifact repository admin** and ask:

> "I need credentials to download artifacts from https://repo.spring.io/release (or ask them if there's an internal Nexus/Artifactory mirror for Spring artifacts). Can you provide:
> - Username
> - Password"

**If they say it's a corporate proxy/mirror**, ask for:
- Internal proxy URL
- Username
- Password
- Proxy port

### Step 2: Encrypt the password (SECURE - recommended)

Run these commands **locally on your machine** (replace with your actual password):

```bash
# Generate encrypted master password
mvn --encrypt-master-password
# When prompted, enter a strong passphrase and press Enter
# Copy the encrypted output (looks like {jSM3abc...})

# Encrypt your repository password
mvn --encrypt-password 'YOUR_REPO_PASSWORD'
# Replace YOUR_REPO_PASSWORD with your actual password
# Copy the encrypted output
```

### Step 3: Create Maven security files

Create **two files** on your local machine (do NOT commit these to git):

#### File 1: `~/.m2/settings-security.xml`

```xml
<settingsSecurity>
  <master>{ENCRYPTED_MASTER_PASSWORD}</master>
</settingsSecurity>
```

Replace `{ENCRYPTED_MASTER_PASSWORD}` with the encrypted token from Step 2.

#### File 2: `~/.m2/settings.xml`

**Option A: If using direct repository authentication (no proxy)**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0"
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
          xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0 http://maven.apache.org/xsd/settings-1.0.0.xsd">

  <servers>
    <server>
      <id>spring-releases</id>
      <username>YOUR_USERNAME</username>
      <password>{ENCRYPTED_PASSWORD}</password>
    </server>
  </servers>

</settings>
```

Replace:
- `YOUR_USERNAME` with your actual username
- `{ENCRYPTED_PASSWORD}` with the encrypted token from Step 2

**Option B: If using a corporate proxy/mirror**

Ask your infra team for the internal mirror URL. Then use:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0"
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
          xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0 http://maven.apache.org/xsd/settings-1.0.0.xsd">

  <servers>
    <server>
      <id>corp-artifact-proxy</id>
      <username>YOUR_USERNAME</username>
      <password>{ENCRYPTED_PASSWORD}</password>
    </server>
    <server>
      <id>spring-releases</id>
      <username>YOUR_USERNAME</username>
      <password>{ENCRYPTED_PASSWORD}</password>
    </server>
  </servers>

  <mirrors>
    <mirror>
      <id>corp-artifact-proxy</id>
      <mirrorOf>*</mirrorOf>
      <url>INTERNAL_MIRROR_URL</url>
    </mirror>
  </mirrors>

</settings>
```

Replace:
- `YOUR_USERNAME` with your username
- `{ENCRYPTED_PASSWORD}` with the encrypted token
- `INTERNAL_MIRROR_URL` with the URL provided by your infra team

### Step 4: Verify the setup

Test if Maven can now download the artifact:

```bash
cd /Users/kaliprasad/Documents/Project/smart-grocery-tracker

# Test downloading a single artifact
mvn -X dependency:get -DgroupId=org.springframework.ai -DartifactId=spring-ai-chat -Dversion=1.0.0
```

If successful, you should see:
```
[INFO] Downloading from spring-releases: https://repo.spring.io/release/org/springframework/ai/spring-ai-chat/1.0.0/spring-ai-chat-1.0.0.pom
[INFO] Downloaded from spring-releases: ...
[INFO] BUILD SUCCESS
```

### Step 5: Build the project

Once credentials work, run:

```bash
mvn -U -DskipTests package
```

This will:
- Download all missing Spring AI artifacts
- Compile `AiConfig.java` successfully
- Build the entire project

If the build succeeds, you're done! ✅

---

## Troubleshooting

### Still getting 401 after adding credentials?
- Double-check that the `<id>` in `settings.xml` matches the `<id>` in the POM repository entry (should be `spring-releases`).
- Verify the username and password are correct.
- Try clearing the local cache and forcing an update:
  ```bash
  rm -rf ~/.m2/repository/org/springframework/ai
  mvn -U -DskipTests package
  ```

### Credentials aren't accepted?
- Your infra team may have provided wrong credentials. Contact them again.
- The password may contain special characters that need escaping. Use the encrypted approach (Step 2).

### Still blocked and need to code immediately?
- Reply "Do Option B" and I will implement a temporary in-memory fallback so the project builds while you fix the credentials setup.

---

## Security Notes

✅ **DO:**
- Keep `~/.m2/settings.xml` and `~/.m2/settings-security.xml` **out of git** (not in your repo).
- Use encrypted passwords (Step 2) instead of plain-text.
- Ask your infra team for temporary credentials if possible, rotate them periodically.

❌ **DON'T:**
- Paste raw passwords in Slack, email, or git commits.
- Share `settings.xml` with team members (each person needs their own credentials).
- Use the same password for multiple services.

