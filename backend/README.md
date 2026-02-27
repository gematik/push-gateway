# Push-Gateway: backend

This module is the home of the actual push gateway application.

## Push configuration

The push gateway supports multiple apps that notifications can be delivered to, each of which is represented by a separate configuration block. The application expects a push configuration file at `$PUSH_CONFIG_FILE` which applies to the following structure:

The following yaml shows two apps `alpha`, `beta` with their respective push configuration blocks for both `APNS` and `Firebase`.

```yaml
push:
  apns:
    - enabled: true
      server: "api.sandbox.push.apple.com"
      bundleId: "com.gematik.apps.alpha"
      authMethod: TOKEN # or CERTIFICATE
      tokenAuth:
        token: "classpath:com.gematik.apps.alpha.apns.p8"
        keyId: "keyId"
        teamId: "teamId"
      certificateAuth:
        certificatePath: "classpath:com.gematik.apps.alpha.apns.p12"
        passwordPath: "classpath:com.gematik.apps.alpha.apns.p12.password"
      receivingAppIds:
        - "com.gematik.apps.alpha.apns"
    - enabled: true
      server: "api.sandbox.push.apple.com"
      bundleId: "com.gematik.apps.beta"
      authMethod: CERTIFICATE # or TOKEN
      tokenAuth:
        token: "classpath:com.gematik.apps.beta.apns.p8"
        keyId: "keyId"
        teamId: "teamId"
      certificateAuth:
        certificatePath: "classpath:com.gematik.apps.beta.apns.p12"
        passwordPath: "classpath:com.gematik.apps.beta.apns.p12.password"
      receivingAppIds:
        - "com.gematik.apps.beta.apns"
  firebase:
    - enabled: true
      projectId: "gematik-project-alpha"
      credentialsPath: "classpath:gematik-project-alpha.service-account.json"
      receivingAppIds:
        - "gematik-project-alpha.app-one.firebase"
        - "gematik-project-alpha.app-two.firebase"
    - enabled: true
      projectId: "gematik-project-beta"
      credentialsPath: "classpath:gematik-project-beta.service-account.json"
      receivingAppIds:
        - "gematik-project-beta.app.one.firebase"
```

A `receivingAppId` is a composition of meta information which is unique to each push provider.

A `receivingAppId` for `APNS` looks like this: `bundleId.apns`

A `receivingAppId` for `Firebase` looks like this: `projectId.applicationId.firebase`