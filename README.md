# paste-pls

`paste-pls` is a Java Discord bot that adds a message context action for uploaded text files and mirrors their contents to a configurable paste service. The first-class target is [`lucko/paste`](https://github.com/lucko/paste), including the public `pastes.dev` deployment.

## How it works

1. A user right-clicks a message in Discord and chooses **Apps -> Upload to paste**.
2. The bot scans the message attachments for supported text files.
3. Each eligible attachment is downloaded and uploaded to the configured paste service.
4. The bot replies with the resulting paste URL(s), either ephemerally or publicly based on configuration.

Discord does not expose a bot action on an individual attachment, so the interaction targets the **message containing the attachment(s)**.

## Requirements

- Java 25+
- A Discord application/bot token
- A distinct `User-Agent` string when using the public `https://api.pastes.dev/` API

## Configuration

Copy `paste-pls.properties.example` to `paste-pls.properties` and fill in the required values.

| Key | Required | Default | Notes |
| --- | --- | --- | --- |
| `discord.token` | Yes | - | Discord bot token |
| `discord.guild-id` | No | - | Registers the command in one guild for faster development feedback |
| `paste.provider` | No | `lucko` | Only `lucko` is currently implemented |
| `paste.api-base-url` | No | `https://api.pastes.dev/` | API base; for self-hosted `lucko/paste` the Docker default is typically `http://localhost:8080/data/` |
| `paste.public-base-url` | No | Derived | Defaults to `https://pastes.dev/` for the public API, or strips `/data/` from a self-hosted API URL |
| `paste.user-agent` | Yes for `api.pastes.dev` | `paste-pls/1.0` for non-official hosts | `lucko/paste` requires a unique identifying user agent on the public service |
| `discord.response-visibility` | No | `ephemeral` | `ephemeral` or `public` |
| `discord.attachment-max-bytes` | No | `1048576` | Maximum size per downloaded attachment |
| `http.request-timeout-seconds` | No | `20` | Timeout for attachment download and paste upload requests |

System properties and environment variables override values from `paste-pls.properties`.

- System property form: `-Dpaste-pls.discord.token=...`
- Environment variable form: `PASTE_PLS_DISCORD_TOKEN=...`

## Running locally

```bash
cp paste-pls.properties.example paste-pls.properties
$EDITOR paste-pls.properties
./gradlew run
```

If you set `discord.guild-id`, the context command is registered in that guild on startup. Otherwise it is registered globally, which can take longer to appear in Discord.

## Using the app without adding a bot to a guild

If you want the action to show up for a **user-installed** app, two things must be true:

1. In the Discord Developer Portal, under **Installation**, enable the **User Install** context for the application.
2. The registered command must support `USER_INSTALL` and the relevant interaction contexts. `paste-pls` now registers the message command for guilds, bot DMs, and private channels.

After changing installation settings or command definitions, reinstall the app to your account and give Discord a little time to propagate the updated global command.

## Nix / NixOS

```bash
nix develop
./gradlew test
./gradlew run
```

The flake now also exposes a real package and app:

```bash
nix build .#paste-pls
nix run .#
```

The provided `flake.nix` supplies Gradle and JDK 25, packages the bot for Nix, and exports a NixOS module for service deployment.

### GitHub Actions

The repository CI is split into:

- `CI`: verifies the Gradle Wrapper and runs `nix flake check`, which includes the packaged build and the Gradle test suite under Nix
- `Qodana`: runs JetBrains Qodana JVM Community and uploads SARIF results to GitHub code scanning

This keeps the build/test path reproducible through Nix while still using the purpose-built GitHub actions for wrapper verification and Qodana.

### NixOS service

The flake exports `nixosModules.default`, so you can host the bot as a systemd service:

```nix
{
  inputs.paste-pls.url = "path:/path/to/paste-pls";

  outputs = { self, nixpkgs, paste-pls, ... }: {
    nixosConfigurations.my-host = nixpkgs.lib.nixosSystem {
      system = "x86_64-linux";
      modules = [
        paste-pls.nixosModules.default
        ({ ... }: {
          services.paste-pls = {
            enable = true;
            environmentFile = "/run/secrets/paste-pls.env";
            settings = {
              paste.api-base-url = "https://api.pastes.dev/";
              paste.user-agent = "paste-pls (github.com/example/paste-pls)";
              discord.response-visibility = "ephemeral";
            };
          };
        })
      ];
    };
  };
}
```

Example `EnvironmentFile` contents:

```bash
PASTE_PLS_DISCORD_TOKEN=your-token-here
```

`services.paste-pls.settings` writes a generated `paste-pls.properties`, while `environmentFile` and `services.paste-pls.environment` can override values through the environment. Use the environment file for secrets.

## Notes for `pastes.dev`

- Uploads should only happen as the result of explicit user actions.
- You must provide a `paste.user-agent` value that identifies your application.
- The bot defaults to ephemeral responses to avoid unexpectedly posting private file links into a channel.

## License

This project is licensed under the **MIT License**. See [`LICENSE`](./LICENSE).

Dependencies remain under their own licenses. The current dependency set is compatible with licensing this project's source under MIT, but redistribution of built artifacts must still comply with the licenses of bundled dependencies such as Apache-2.0 components and the `trove4j` LGPL-2.1 runtime dependency pulled in by JDA.
