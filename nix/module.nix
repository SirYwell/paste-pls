{ self }:
{
  config,
  lib,
  pkgs,
  ...
}:
let
  cfg = config.services.paste-pls;
  settingsFormat = pkgs.formats.javaProperties { };
  generatedConfig =
    if cfg.settings == { } then
      null
    else
      settingsFormat.generate "paste-pls.properties" cfg.settings;
  effectiveConfigFile =
    if cfg.configFile != null then cfg.configFile else generatedConfig;
in
{
  options.services.paste-pls = {
    enable = lib.mkEnableOption "paste-pls Discord bot";

    package = lib.mkOption {
      type = lib.types.package;
      default = self.packages.${pkgs.system}.default;
      description = "The paste-pls package to run.";
    };

    configFile = lib.mkOption {
      type = lib.types.nullOr lib.types.path;
      default = null;
      description = ''
        Path to an existing `paste-pls.properties` file. If set, this takes precedence
        over `services.paste-pls.settings`.
      '';
    };

    settings = lib.mkOption {
      type = settingsFormat.type;
      default = { };
      description = ''
        Declarative non-secret settings written to a generated `paste-pls.properties`.
        Secrets should be provided through `environmentFile` or `environment`.
      '';
      example = {
        paste.api-base-url = "https://api.pastes.dev/";
        paste.user-agent = "paste-pls (github.com/example/paste-pls)";
        discord.response-visibility = "ephemeral";
      };
    };

    environment = lib.mkOption {
      type = lib.types.attrsOf (lib.types.oneOf [
        lib.types.str
        lib.types.int
        lib.types.bool
        lib.types.path
      ]);
      default = { };
      description = ''
        Extra environment variables for the service. This is useful for values such as
        `PASTE_PLS_DISCORD_GUILD_ID` or a direct `PASTE_PLS_CONFIG_FILE` override.
      '';
      example = {
        PASTE_PLS_DISCORD_GUILD_ID = 123456789012345678;
      };
    };

    environmentFile = lib.mkOption {
      type = lib.types.nullOr lib.types.path;
      default = null;
      description = ''
        Optional systemd environment file for secrets such as
        `PASTE_PLS_DISCORD_TOKEN=...`.
      '';
      example = "/run/secrets/paste-pls.env";
    };

    dynamicUser = lib.mkOption {
      type = lib.types.bool;
      default = true;
      description = "Run the service with DynamicUser.";
    };

    user = lib.mkOption {
      type = lib.types.str;
      default = "paste-pls";
      description = "User account for the service when DynamicUser is disabled.";
    };

    group = lib.mkOption {
      type = lib.types.str;
      default = "paste-pls";
      description = "Group for the service when DynamicUser is disabled.";
    };
  };

  config = lib.mkIf cfg.enable {
    assertions = [
      {
        assertion = cfg.configFile == null || cfg.settings == { };
        message = "services.paste-pls.configFile and services.paste-pls.settings cannot be used at the same time.";
      }
    ];

    users.users = lib.mkIf (!cfg.dynamicUser && cfg.user == "paste-pls") {
      paste-pls = {
        isSystemUser = true;
        group = cfg.group;
      };
    };

    users.groups = lib.mkIf (!cfg.dynamicUser && cfg.group == "paste-pls") {
      paste-pls = { };
    };

    systemd.services.paste-pls = {
      description = "paste-pls Discord bot";
      wantedBy = [ "multi-user.target" ];
      wants = [ "network-online.target" ];
      after = [ "network-online.target" ];

      environment =
        lib.mapAttrs (_: value: toString value) cfg.environment
        // lib.optionalAttrs (effectiveConfigFile != null) {
          PASTE_PLS_CONFIG_FILE = toString effectiveConfigFile;
        };

      serviceConfig =
        {
          ExecStart = lib.getExe cfg.package;
          Restart = "on-failure";
          RestartSec = "5s";
          DynamicUser = cfg.dynamicUser;
          NoNewPrivileges = true;
          PrivateTmp = true;
          ProtectSystem = "strict";
          ProtectHome = true;
          ProtectControlGroups = true;
          ProtectKernelModules = true;
          ProtectKernelTunables = true;
          RestrictAddressFamilies = [
            "AF_INET"
            "AF_INET6"
            "AF_UNIX"
          ];
          LockPersonality = true;
          SystemCallArchitectures = "native";
        }
        // lib.optionalAttrs (!cfg.dynamicUser) {
          User = cfg.user;
          Group = cfg.group;
        }
        // lib.optionalAttrs (cfg.environmentFile != null) {
          EnvironmentFile = cfg.environmentFile;
        };
    };
  };
}
