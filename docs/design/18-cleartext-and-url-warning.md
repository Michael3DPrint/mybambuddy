# 18: Cleartext HTTP and the server address warning

The Setup and Settings screens warn when the server address is plain http to a host outside a private network.
The app does not block such a connection: the manifest and the network security config allow cleartext app-wide, because a network security config matches domain names, not IP ranges.
Source: https://developer.android.com/privacy-and-security/security-config.
The warning is built; blocking plain http to a public host is not built (see OI-06).

## Decisions

- The warning lives in code, not in the network security config, because that config cannot express "public hosts only".
- Private means loopback, RFC 1918 ranges, `100.64.0.0/10` (Tailscale), `.ts.net`, `.local`, `localhost` and bare single-word host names.
  Whether this set is right for the owner's setup is part of OI-06.

## Design

```text
address typed (Setup, Settings)
  -> SetupViewModel / SettingsViewModel
  -> ServerUrlPolicy.warning(url) -> text or null
  -> urlWarning in the UI state -> supporting text under the field (error color)
```

`ServerUrlPolicy.warning` returns null for https, private hosts and blank input.
The check only informs; `DynamicUrlInterceptor` rewrites the request to the saved address with whatever scheme it has.

## Tests

`PolicyTest` covers the host classification.
How the warning looks only shows on a device.

## Open work

Refusing plain http to a public host unless a Settings switch allows it: OI-06 in [OPEN_ITEMS.md](../OPEN_ITEMS.md).
If built, the check must run before the request carries the API key.
