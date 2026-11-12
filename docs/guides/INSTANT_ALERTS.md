# Instant alerts with ntfy or Pushover

Android runs this app's background alert check about every 15 minutes at best, so "print finished" or "print failed" can arrive late.
Bambuddy can push a notification the moment an event happens, so these steps set up a provider in Bambuddy and need no change in MyBambuddy.
This guide is for Bambuddy 1.3.0; the provider fields below come from its source (`backend/app/services/notification_service.py`).

## Provider options

- Bambuddy supports ntfy, Pushover, Telegram, Discord, email, Home Assistant, Gotify, Bark and a generic webhook.
- Each provider has its own on and off switch per event.
- By default print complete, failed and stopped are on.
- By default print start, progress, printer offline, printer error, filament low and maintenance due are off.
- Each provider has a test-notification action.

## Set up ntfy

ntfy is free, needs no account and works on your tailnet.

1. Install the ntfy app on the phone and subscribe to a topic.
   Pick a long random topic name, because on the public server anyone who knows a topic can read it.
2. In Bambuddy's web interface, add a notification provider of type ntfy.
3. Enter `server` (default `https://ntfy.sh`) and `topic`.
   Enter `auth_token` only for a protected topic.
4. To keep everything private, self-host ntfy and use its address as `server`.
   Bambuddy accepts local and private addresses and host names, over http or https.
   It rejects other schemes, cloud-metadata and link-local addresses, multicast and numeric-encoded IPs.
   The phone's ntfy app must also reach the address, so connect Tailscale on the phone if you use a tailnet address.
5. Tick the events you want and save.
6. Send the test notification.

## Set up Pushover

Pushover is a paid app and very reliable.

1. Create a Pushover account.
   Create an application there to get an app token; your account has a user key.
2. In Bambuddy add a provider of type Pushover and enter `user_key` and `app_token`.
3. Tick the events you want and save.
4. Send the test notification.

## Avoid double notifications

1. In MyBambuddy open Settings, then Alerts.
2. Switch off the categories that Bambuddy now covers, for example "Print finished" and "Print failed or stopped".
3. Keep "Printer went offline" on, because it works even when the server cannot reach you.

## Check it works

1. A test notification arrives on the phone.
2. A real print finishing produces one notification within a few seconds.
