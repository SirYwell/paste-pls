{
  lib,
  stdenv,
  gradle_9,
  jdk25,
  makeBinaryWrapper,
}:
let
  pname = "paste-pls";
  version = "1.0-SNAPSHOT";
  gradle' = gradle_9.override {
    java = jdk25;
    javaToolchains = [ jdk25 ];
  };

  mkPackage =
    {
      mitmCache ? null,
      doCheck ? false,
    }:
    stdenv.mkDerivation {
      inherit pname version;
      src = lib.cleanSource ../.;
      strictDeps = true;
      inherit doCheck;

      nativeBuildInputs = [
        gradle'
        makeBinaryWrapper
      ];

      inherit mitmCache;
      gradleBuildTask = "installDist";

      installPhase = ''
        runHook preInstall

        mkdir -p $out/{bin,lib}
        cp -r build/install/paste-pls $out/lib/paste-pls

        makeBinaryWrapper $out/lib/paste-pls/bin/paste-pls $out/bin/paste-pls \
          --prefix PATH : "${lib.makeBinPath [ jdk25 ]}"

        runHook postInstall
      '';

      meta = {
        description = "Discord bot for uploading text attachments to a paste service";
        homepage = "https://github.com/lucko/paste";
        license = lib.licenses.mit;
        maintainers = [ ];
        platforms = lib.platforms.unix;
        mainProgram = "paste-pls";
        sourceProvenance = with lib.sourceTypes; [
          fromSource
          binaryBytecode
        ];
      };
    };

  uncachedPackage = mkPackage { mitmCache = ""; };
  mitmCache = gradle'.fetchDeps {
    pkg = uncachedPackage;
    data = "deps.json";
  };
  testPackage = mkPackage {
    inherit mitmCache;
    doCheck = true;
  };
in
(mkPackage { inherit mitmCache; }).overrideAttrs (old: {
  passthru = (old.passthru or { }) // {
    inherit
      mitmCache
      uncachedPackage
      testPackage
      ;
    updateScript = mitmCache.updateScript;
  };
})
