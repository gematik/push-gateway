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
      receivingAppIds:
        - "com.gematik.apps.alpha.apns"
    - enabled: true
      server: "api.sandbox.push.apple.com"
      bundleId: "com.gematik.apps.beta"
      authMethod: CERTIFICATE # or TOKEN
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
