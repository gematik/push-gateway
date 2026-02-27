# Docker compose

This directory contains push provider credentials and configuration for local testing, each of which have to be set up by an individuell developer and cannot be provided in advance.

## APNS

### Prerequisites

In order to set up push notifications for local testing you need the following:

* an iOS application (see to [gematik push poc](https://github.com/gematik/gem-push-notifications-concept/tree/main/push-poc-ios))
* Either
  * access to an Apple Developer Account to register your app with and to create push credentials 
* or
  * valid push credentials from elsewhere

On your machine check out the push-poc-ios app from above. Open the project with `XCode` and connect it to your developer account. An App ID with enabled Push Notifications capability should be created automatically in your developer account. 

<details>
<summary>If not, log into your Apple Developer Account and do the following</summary>
* Go to `Certificates, Identifiers & Profiles/Identifiers` and hit the blue + Button.
* On the `Register a new identifier` page select `App IDs` and hit `Continue`.
* Choose `App` from the `Select a type` view and hit `Continue`. 
* Provide a `Description` and a `Bundle ID` of your choice and check `Push Notifications` from the `Capabilities` list. Then click `Continue`. Your information will be summarized. If everything is ok click `Register`. 

Done.
</details>

In order to receive push notifications from APNS you have two possibilities to authenticate the push gateway against APNS, TOKEN and CERTIFICATE. 

Contrary to certificates, keys do not expire and for this reason may be chosen over certificates. However, both can be revoked at any time.

### CERTIFICATE

This authentication method requires the following information:

* certificate in PKCS12 format as a path to a file
* certificate password as a path to a file
    * in case there is no certificate password you must provide an empty file because `null` will not be accepted by the client library

Get a certificate from your `App ID` configuration by editing the `Push notification` capability. In that view click `Create Certificate`. You need a certificate request (CSR), created for a private key of your own. You can use `OpenSSL` to generate a private key and generate a CSR from it. Upload the CSR file and get a certificate in return. This is [the PKCS12 file mentioned here](../../backend/README.md#push-configuration)

### TOKEN

This authentication method requires the following information:

* private key in PKCS8 format
* key-id
* team-id

Get a private key from your Apple Developer Account under `Certificates, Identifiers & Profiles/Keys/+ Button`.

Give the key a name and a description, restrict it to `Apple Push Notifications service (APNs)` and configure an appropriate scope by clicking `Configure`. You may want to restrict the key to a specific topic (your bundleId), otherwise it will be eligible for all the team's apps. Please note that you can only download the key one time, so be ready to place it somewhere safe.

## Firebase

In order to receive push notifications from firebase you need to have the following setup:

* Android app with push notification capability
  * In order to adapt to your personal settings you should fork this repository and make the necessary changes in the fork.
* Firebase account (usually comes with your Google account if you have one)
* Firebase project
* Firebase application in your firebase project
* Files `service-account.json` and `google-services.json`
  * `service-account.json`:
    * In you firebase project settings select `Service accounts` and generate a new private key. You will then get the file in return which serves as credentials for firebase cloud messaging (FCM). Place the file in any location which is accessible to the push gateway, e.g. `compose/firebase` and configure it as a firebase credential in your `push-config.yaml`.
  * `google-services.json`
    * To get this file create a new android app in your firebase project and just download it when prompted to do so. Place this file in your fork repository. This will replace/update the already present file with your personal project settings.
* In your fork update the `applicationId` in `$PROJECT_ROOT/app/build.gradle.kts` according to your personal settings.
