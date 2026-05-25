{
  description = "Nix flake for building and hosting paste-pls";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
    flake-utils.url = "github:numtide/flake-utils";
  };

  outputs =
    { self, nixpkgs, flake-utils }:
    let
      nixosModule = import ./nix/module.nix { inherit self; };
    in
    flake-utils.lib.eachDefaultSystem (
      system:
      let
        pkgs = import nixpkgs { inherit system; };
        gradle = pkgs.gradle_9.override {
          java = pkgs.jdk25;
          javaToolchains = [ pkgs.jdk25 ];
        };
        package = pkgs.callPackage ./nix/package.nix { };
      in
      {
        packages = {
          default = package;
          paste-pls = package;
        };

        apps = {
          default = {
            type = "app";
            program = "${pkgs.lib.getExe package}";
            meta = package.meta;
          };
        };

        checks = {
          default = package;
          package-build = package;
          gradle-tests = package.passthru.testPackage;
        };

        devShells.default = pkgs.mkShell {
          packages = [
            gradle
            pkgs.jdk25
          ];

          shellHook = ''
            export JAVA_HOME=${pkgs.jdk25}
          '';
        };
      }
    )
    // {
      nixosModules.default = nixosModule;
      nixosModules.paste-pls = nixosModule;
    };
}
